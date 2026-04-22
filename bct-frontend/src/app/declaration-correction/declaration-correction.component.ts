// src/app/declaration-correction/declaration-correction.component.ts
import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { Declaration } from '../services/Declaration.service';
import { DeclarationTypeService } from '../services/declaration-type.service';
import { ValidationService } from '../services/Validation.service';
import { DeclarationService, FieldMapping, GenerateWithMappingRequest } from '../services/Declaration.service';
import { XmlCorrectionService, XmlCorrectionResult, ParsedXmlField } from '../services/xml-correction.service';

export type CorrectionMode = 'inline-xml' | 'mapping-view' | 'sql-xsd-view';

@Component({
  selector: 'app-declaration-correction',
  templateUrl: './declaration-correction.component.html',
  styleUrls: ['./declaration-correction.component.scss']
})
export class DeclarationCorrectionComponent implements OnInit, OnChanges {

  @Input() declaration!: Declaration;
  @Output() correctionSaved   = new EventEmitter<Declaration>();
  @Output() correctionCancelled = new EventEmitter<void>();

  // ── Mode d'affichage ────────────────────────────────────────────
  activeMode: CorrectionMode = 'inline-xml';

  // ── XML inline edition ──────────────────────────────────────────
  xmlContent        = '';
  xmlOriginal       = '';
  xmlModified       = false;
  xmlParseError     = '';
  xmlValidationErrors: string[] = [];
  xmlLines:         XmlLineEntry[] = [];
  selectedLineIndex: number | null = null;
  xmlSearchQuery    = '';
  showDiffOnly      = false;

  // ── Mapping view ────────────────────────────────────────────────
  mappingJson:      FieldMapping[] = [];
  mappingAnalysis:  any = null;
  loadingMapping    = false;
  mappingError      = '';

  // ── XSD + SQL side-by-side ───────────────────────────────────────
  xsdContent        = '';
  sqlQuery          = '';
  xsdFields:        XsdDisplayField[] = [];
  sqlColumns:       string[] = [];

  // ── Etat général ────────────────────────────────────────────────
  loading       = false;
  saving        = false;
  error         = '';
  success       = '';
  showRejectReason = true;

  // ── Test SQL ─────────────────────────────────────────────────────
  testDateDebut = '';
  testDateFin   = '';
  testingSQL    = false;
  sqlTestResult: any = null;

  constructor(
    private declarationService: DeclarationService,
    private declarationTypeService: DeclarationTypeService,
    private validationService: ValidationService,
    private xmlCorrectionService: XmlCorrectionService
  ) {}

  ngOnInit(): void {
    this.initFromDeclaration();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['declaration']?.currentValue) {
      this.initFromDeclaration();
    }
  }

  private initFromDeclaration(): void {
    if (!this.declaration) return;

    const fmt = this.declaration.declarationType?.format;

    this.xmlContent  = this.declaration.contenuFichier ?? '';
    this.xmlOriginal = this.xmlContent;
    this.xmlModified = false;

    this.testDateDebut = this.declaration.dateDebut ?? '';
    this.testDateFin   = this.declaration.dateFin   ?? '';

    if (fmt === 'XML') {
      this.activeMode = 'inline-xml';
      this.parseXmlLines();
      this.loadMappingAndXsd();
    } else {
      this.activeMode = 'sql-xsd-view';
      this.sqlQuery = this.declaration.sqlQueryUsed ?? '';
    }
  }

  // ══════════════════════════════════════════════════════════════
  // XML INLINE EDITING
  // ══════════════════════════════════════════════════════════════

  parseXmlLines(): void {
    if (!this.xmlContent) return;
    try {
      this.xmlLines     = parseXmlToLines(this.xmlContent);
      this.xmlParseError = '';
    } catch (e: any) {
      this.xmlParseError = 'XML mal formé : ' + (e.message ?? '');
      this.xmlLines      = [];
    }
  }

  onXmlContentChange(value: string): void {
    this.xmlContent  = value;
    this.xmlModified = value !== this.xmlOriginal;
    this.parseXmlLines();
  }

  updateLineValue(lineIndex: number, newValue: string): void {
    const line = this.xmlLines[lineIndex];
    if (!line || line.type !== 'leaf') return;

    line.value    = newValue;
    line.modified = newValue !== line.originalValue;
    this.rebuildXmlFromLines();
  }

  private rebuildXmlFromLines(): void {
    this.xmlContent  = rebuildXml(this.xmlLines);
    this.xmlModified = this.xmlContent !== this.xmlOriginal;
  }

  selectLine(index: number): void {
    this.selectedLineIndex = this.selectedLineIndex === index ? null : index;
  }

  resetXml(): void {
    if (!confirm('Réinitialiser toutes les modifications ?')) return;
    this.xmlContent  = this.xmlOriginal;
    this.xmlModified = false;
    this.xmlLines.forEach(l => { l.value = l.originalValue; l.modified = false; });
    this.selectedLineIndex = null;
  }

  get filteredLines(): (XmlLineEntry & { index: number })[] {
    return this.xmlLines
      .map((l, i) => ({ ...l, index: i }))
      .filter(l => {
        if (this.showDiffOnly && !l.modified) return false;
        if (this.xmlSearchQuery) {
          const q = this.xmlSearchQuery.toLowerCase();
          return l.tag?.toLowerCase().includes(q) || l.value?.toLowerCase().includes(q);
        }
        return true;
      });
  }

  get modifiedCount(): number {
    return this.xmlLines.filter(l => l.modified).length;
  }

  // ══════════════════════════════════════════════════════════════
  // MAPPING / XSD
  // ══════════════════════════════════════════════════════════════

  private loadMappingAndXsd(): void {
    if (!this.declaration.declarationType?.id) return;
    this.loadingMapping = true;
    this.mappingError   = '';

    const typeId = this.declaration.declarationType.id;

    this.declarationTypeService.getById(typeId).subscribe({
      next: (type) => {
        this.xsdContent = type.xsdContent ?? '';
        this.sqlQuery   = type.sqlQuery ?? this.declaration.sqlQueryUsed ?? '';
        this.parseXsdFields(this.xsdContent);

        if ((this.declaration as any).mappingJson) {
          try {
            this.mappingJson = JSON.parse((this.declaration as any).mappingJson);
          } catch { this.mappingJson = []; }
        }

        this.declarationService.analyzeMappingHttp({
          declarationTypeId: typeId,
          dateDebut: this.declaration.dateDebut,
          dateFin:   this.declaration.dateFin
        }).subscribe({
          next: (analysis) => {
            this.mappingAnalysis = analysis;
            this.sqlColumns      = analysis.sqlColumns ?? [];
            this.loadingMapping  = false;

            if (!this.mappingJson.length && analysis.xsdFields?.length) {
              this.mappingJson = (analysis.xsdFields as any[]).map((f: any) => ({
                xsdFieldName: f.name,
                xsdFieldPath: f.path,
                xsdType:      f.type,
                required:     f.required,
                source:       analysis.autoMapped?.[f.name] ? 'SQL' : 'NONE',
                sqlColumn:    analysis.autoMapped?.[f.name] ?? '',
                staticValue:  ''
              }));
            }
          },
          error: () => {
            this.loadingMapping = false;
            this.mappingError   = 'Impossible de charger l\'analyse de mapping';
          }
        });
      },
      error: () => {
        this.loadingMapping = false;
        this.mappingError   = 'Impossible de charger les détails du type';
      }
    });
  }

  private parseXsdFields(xsd: string): void {
    if (!xsd) return;
    const regex = /xs:element\s+name="([^"]+)"(?:[^>]*type="([^"]+)")?(?:[^>]*minOccurs="([^"]+)")?/g;
    const fields: XsdDisplayField[] = [];
    let m: RegExpExecArray | null;
    while ((m = regex.exec(xsd)) !== null) {
      fields.push({
        name:     m[1],
        type:     m[2] ? m[2].replace(/xs?:/, '') : 'string',
        required: m[3] !== '0'
      });
    }
    this.xsdFields = fields;
  }

  getMappingForXsdField(fieldName: string): FieldMapping | null {
    return this.mappingJson.find(m => m.xsdFieldName === fieldName) ?? null;
  }

  updateMappingField(index: number, key: keyof FieldMapping, value: any): void {
    if (!this.mappingJson[index]) return;
    (this.mappingJson[index] as any)[key] = value;
  }

  // ══════════════════════════════════════════════════════════════
  // ✅ HELPERS TEMPLATE — remplacent le pipe 'missingRequired'
  //    et les arrow functions interdites dans les templates Angular
  // ══════════════════════════════════════════════════════════════

  /**
   * Remplace le pipe 'missingRequired' supprimé.
   * Retourne true si au moins un champ requis n'est pas mappé.
   */
  hasMissingRequired(): boolean {
    return this.mappingJson.some(m => m.required && m.source === 'NONE');
  }

  /**
   * Correspondance exacte (casse stricte) entre un champ XSD et les colonnes SQL.
   */
  hasExactMatch(fieldName: string): boolean {
    return this.sqlColumns.includes(fieldName);
  }

  /**
   * Correspondance insensible à la casse (fuzzy) sans correspondance exacte.
   */
  hasFuzzyMatch(fieldName: string): boolean {
    if (this.hasExactMatch(fieldName)) return false;
    return this.sqlColumns.some(c => c.toLowerCase() === fieldName.toLowerCase());
  }

  /**
   * Retourne true si le champ a une correspondance exacte OU fuzzy.
   * Utilisé pour la classe CSS [class.match].
   */
  hasExactOrFuzzyMatch(fieldName: string): boolean {
    return this.hasExactMatch(fieldName) || this.hasFuzzyMatch(fieldName);
  }

  /**
   * Retourne la colonne SQL correspondant en mode fuzzy (insensible à la casse).
   */
  getFuzzyMatch(fieldName: string): string {
    return this.sqlColumns.find(c => c.toLowerCase() === fieldName.toLowerCase()) ?? '';
  }

  // ══════════════════════════════════════════════════════════════
  // SQL TEST
  // ══════════════════════════════════════════════════════════════

  testSql(): void {
    if (!this.sqlQuery?.trim() || !this.testDateDebut || !this.testDateFin) return;
    if (!this.declaration.declarationType?.id) return;

    this.testingSQL    = true;
    this.sqlTestResult = null;
    const typeId       = this.declaration.declarationType.id;

    this.declarationTypeService.saveSqlQuery(typeId, this.sqlQuery).subscribe({
      next: () => {
        this.declarationTypeService.testSqlQuery(typeId, this.testDateDebut, this.testDateFin)
          .subscribe({
            next:  r   => { this.sqlTestResult = r; this.testingSQL = false; },
            error: err => {
              this.sqlTestResult = { success: false, error: err.error?.error || err.message };
              this.testingSQL    = false;
            }
          });
      },
      error: () => {
        this.sqlTestResult = { success: false, error: 'Erreur sauvegarde SQL' };
        this.testingSQL    = false;
      }
    });
  }

  getSqlColumnsDisplay(): string {
    return (this.sqlTestResult?.colonnesDisponibles ?? []).join(', ');
  }

  // ══════════════════════════════════════════════════════════════
  // SAUVEGARDE
  // ══════════════════════════════════════════════════════════════

  save(): void {
    if (!this.declaration?.id) return;
    this.saving = true;
    this.error  = '';
    this.success = '';

    const fmt = this.declaration.declarationType?.format;

    if (this.activeMode === 'inline-xml' && this.xmlModified) {
      this.saveInlineXml();
    } else if (this.activeMode === 'mapping-view' && fmt === 'XML') {
      this.saveWithMapping();
    } else {
      this.saveStandard();
    }
  }

  private saveInlineXml(): void {
    this.declarationService.patchXmlContent(this.declaration.id!, this.xmlContent).subscribe({
      next: (updated) => {
        this.saving     = false;
        this.success    = 'XML corrigé sauvegardé.';
        this.xmlOriginal = this.xmlContent;
        this.xmlModified = false;
        this.submitAndEmit(updated);
      },
      error: () => {
        this.saveStandard();
      }
    });
  }

  private saveWithMapping(): void {
    const missing = this.mappingJson.filter(m => m.required && m.source === 'NONE').length;
    if (missing > 0) {
      this.error  = `${missing} champ(s) obligatoire(s) sans valeur.`;
      this.saving = false;
      return;
    }

    const req: GenerateWithMappingRequest = {
      declarationTypeId: this.declaration.declarationType!.id,
      periode:   this.declaration.periode,
      dateDebut: this.declaration.dateDebut ?? '',
      dateFin:   this.declaration.dateFin   ?? '',
      mappings:  this.mappingJson
    };

    this.declarationService.generateDeclarationWithMapping(req).subscribe({
      next: (saved) => {
        this.saving  = false;
        this.success = 'Déclaration régénérée avec succès.';
        this.submitAndEmit(saved);
      },
      error: (err) => {
        this.saving = false;
        this.error  = 'Erreur régénération : ' + (err.error?.error || err.message);
      }
    });
  }

  private saveStandard(): void {
    const req = {
      declarationTypeId: this.declaration.declarationType!.id,
      periode:   this.declaration.periode,
      dateDebut: this.declaration.dateDebut ?? '',
      dateFin:   this.declaration.dateFin   ?? ''
    };

    this.declarationService.updateDeclaration(this.declaration.id!, req as any).subscribe({
      next: (updated) => {
        this.saving  = false;
        this.success = 'Déclaration mise à jour.';
        this.submitAndEmit(updated);
      },
      error: (err) => {
        this.saving = false;
        this.error  = 'Erreur sauvegarde : ' + (err.error?.message || err.message);
      }
    });
  }

  private submitAndEmit(decl: Declaration): void {
    this.validationService.submitForValidation(decl.id!).subscribe({
      next: () => {
        this.success = '✅ Correction sauvegardée et soumise pour validation.';
        setTimeout(() => this.correctionSaved.emit(decl), 1200);
      },
      error: () => {
        this.correctionSaved.emit(decl);
      }
    });
  }

  cancel(): void { this.correctionCancelled.emit(); }

  // ══════════════════════════════════════════════════════════════
  // HELPERS UI
  // ══════════════════════════════════════════════════════════════

  setMode(mode: CorrectionMode): void { this.activeMode = mode; }

  get isXml(): boolean { return this.declaration?.declarationType?.format === 'XML'; }

  getDepthPadding(depth: number): string { return `${depth * 16}px`; }

  getMappingSourceClass(source: string): string {
    return source === 'SQL' ? 'src-sql' : source === 'STATIC' ? 'src-static' : 'src-none';
  }
}

// ══════════════════════════════════════════════════════════════
// TYPES LOCAUX
// ══════════════════════════════════════════════════════════════

export interface XmlLineEntry {
  type:           'open' | 'close' | 'leaf' | 'comment' | 'declaration';
  tag?:           string;
  value?:         string;
  originalValue?: string;
  attributes?:    Record<string, string>;
  depth:          number;
  modified:       boolean;
  raw:            string;
  lineNum:        number;
}

export interface XsdDisplayField {
  name:     string;
  type:     string;
  required: boolean;
}

// ══════════════════════════════════════════════════════════════
// PARSING XML → LIGNES ÉDITABLES
// ══════════════════════════════════════════════════════════════

function parseXmlToLines(xml: string): XmlLineEntry[] {
  const lines: XmlLineEntry[] = [];
  let depth = 0;
  let lineNum = 0;

  const rawLines = xml.split('\n');
  for (const raw of rawLines) {
    lineNum++;
    const trimmed = raw.trim();
    if (!trimmed) continue;

    const entry: XmlLineEntry = {
      type: 'leaf', tag: '', value: '', originalValue: '',
      attributes: {}, depth, modified: false, raw, lineNum
    };

    if (trimmed.startsWith('<?')) {
      entry.type = 'declaration'; entry.raw = raw;
      lines.push(entry); continue;
    }
    if (trimmed.startsWith('<!--')) {
      entry.type = 'comment'; entry.raw = raw;
      lines.push(entry); continue;
    }

    // Fermeture </tag>
    const closeMatch = trimmed.match(/^<\/([^>]+)>/);
    if (closeMatch) {
      depth = Math.max(0, depth - 1);
      entry.type  = 'close';
      entry.tag   = closeMatch[1];
      entry.depth = depth;
      entry.raw   = raw;
      lines.push(entry); continue;
    }

    // Self-closing <tag/>
    const selfMatch = trimmed.match(/^<([^\s/>]+)([^>]*)\/>/);
    if (selfMatch) {
      entry.type  = 'leaf';
      entry.tag   = selfMatch[1];
      entry.value = '';
      entry.originalValue = '';
      entry.attributes = parseAttributes(selfMatch[2]);
      entry.depth = depth;
      lines.push(entry); continue;
    }

    // Leaf: <tag>value</tag>
    const leafMatch = trimmed.match(/^<([^\s>]+)([^>]*)>([^<]*)<\/[^>]+>$/);
    if (leafMatch) {
      entry.type          = 'leaf';
      entry.tag           = leafMatch[1];
      entry.attributes    = parseAttributes(leafMatch[2]);
      entry.value         = leafMatch[3];
      entry.originalValue = leafMatch[3];
      entry.depth         = depth;
      lines.push(entry); continue;
    }

    // Open <tag ...>
    const openMatch = trimmed.match(/^<([^\s/>]+)([^>]*)>/);
    if (openMatch) {
      entry.type       = 'open';
      entry.tag        = openMatch[1];
      entry.attributes = parseAttributes(openMatch[2]);
      entry.depth      = depth;
      depth++;
      lines.push(entry); continue;
    }

    // Texte pur
    entry.type          = 'leaf';
    entry.value         = trimmed;
    entry.originalValue = trimmed;
    entry.depth         = depth;
    lines.push(entry);
  }
  return lines;
}

function parseAttributes(attrStr: string): Record<string, string> {
  const attrs: Record<string, string> = {};
  const regex = /(\w[\w-]*)="([^"]*)"/g;
  let m: RegExpExecArray | null;
  while ((m = regex.exec(attrStr)) !== null) attrs[m[1]] = m[2];
  return attrs;
}

function rebuildXml(lines: XmlLineEntry[]): string {
  return lines.map(l => {
    if (l.type !== 'leaf' || !l.tag) return l.raw;
    const indent = '  '.repeat(l.depth);
    const attrs  = Object.entries(l.attributes ?? {})
      .map(([k, v]) => ` ${k}="${v}"`).join('');
    return `${indent}<${l.tag}${attrs}>${l.value ?? ''}</${l.tag}>`;
  }).join('\n');
}