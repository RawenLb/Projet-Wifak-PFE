import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { DeclarationService, Declaration } from '../../services/Declaration.service';
import {
  ValidationService, ValidationStats, ValidationLog, RejectTemplate
} from '../../services/Validation.service';
import { JiraService, JiraTicketResponse } from '../../services/jira.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';

type SortField     = 'id' | 'type' | 'periode' | 'date';
type SortDir       = 'asc' | 'desc';
type PriorityFilter = 'ALL' | 'REJETE' | 'NOUVEAU';

export interface ChecklistItem {
  id: string;
  label: string;
  checked: boolean;
}

@Component({
  selector: 'app-manager-pending',
  templateUrl: './manager-pending.component.html',
  styleUrls: ['./manager-pending.component.scss']
})
export class ManagerPendingComponent implements OnInit, OnDestroy {

  // ── Données ────────────────────────────────────────────
  pending: Declaration[] = [];
  stats: ValidationStats | null = null;
  rejectTemplates: RejectTemplate[] = [];

  // ── État général ───────────────────────────────────────
  loading = false;
  actionEnCours: Record<number, boolean> = {};
  lastRefreshed: Date | null = null;
  autoRefreshInterval: any;

  // ── Filtres & tri ──────────────────────────────────────
  searchQuery      = '';
  filtreType       = '';
  priorityFilter: PriorityFilter = 'ALL';
  sortField: SortField = 'date';
  sortDir: SortDir = 'desc';

  // ── Sélection multiple ─────────────────────────────────
  selectedIds = new Set<number>();
  bulkLoading = false;

  // ── Panneau Lecteur de Déclaration ─────────────────────
  showReaderPanel   = false;
  readerDeclaration: Declaration | null = null;
  readerIndex       = 0;
  readerTab: 'details' | 'historique' | 'fichier' = 'details';
  readerLogs: ValidationLog[] = [];
  readerLogsLoading = false;
  readerNotes       = '';

  // Checklist de validation manuelle — réinitialisée à chaque ouverture
  checklistItems: ChecklistItem[] = [];

  private readonly DEFAULT_CHECKLIST: { id: string; label: string }[] = [
    { id: 'periode',   label: 'La période déclarée correspond au trimestre en cours' },
    { id: 'agent',     label: 'L\'agent déclarant est identifié et habilité' },
    { id: 'fichier',   label: 'Le fichier joint est lisible et au bon format' },
    { id: 'coherence', label: 'Les données sont cohérentes avec les déclarations précédentes' },
    { id: 'signature', label: 'La déclaration est correctement signée / horodatée' },
  ];

  // ── Modal Rejet ────────────────────────────────────────
  showRejetModal           = false;
  declarationSelectionnee: Declaration | null = null;
  commentaireRejet         = '';
  commentaireRejetTouched  = false;
  rejetEnCours             = false;
  selectedTemplate: string  = '';

  // ── Modal Historique standalone ────────────────────────
  showHistoriqueModal = false;
  historique: ValidationLog[] = [];
  historiqueLoading   = false;

  // ── Toast ──────────────────────────────────────────────
  // (kept for template compatibility — delegates to ToastService)
  message      = '';
  messageType: 'success' | 'error' | 'info' = 'success';

  // ── Jira ───────────────────────────────────────────────
  jiraLoading: Record<number, boolean> = {};
  private jiraTicketMap = new Map<number, JiraTicketResponse | null>();
  private jiraSub!: Subscription;

  constructor(
    private declarationService: DeclarationService,
    private validationService: ValidationService,
    public jiraService: JiraService,
    private confirmDialog: ConfirmDialogService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.jiraSub = this.jiraService.ticketMap$.subscribe(map => this.jiraTicketMap = map);
    this.charger();
    this.loadRejectTemplates();
    this.autoRefreshInterval = setInterval(() => this.charger(true), 60000);
  }

  ngOnDestroy(): void {
    this.jiraSub?.unsubscribe();
    clearInterval(this.autoRefreshInterval);
  }

  charger(silent = false): void {
    if (!silent) this.loading = true;
    this.validationService.getStats().subscribe({ next: s => this.stats = s, error: () => {} });
    this.validationService.getPendingDeclarations().subscribe({
      next: data => {
        this.pending = data;
        this.lastRefreshed = new Date();
        this.loading = false;
        data.forEach(d => {
          if (d.id && !this.jiraTicketMap.has(d.id)) {
            this.jiraLoading[d.id] = true;
            this.jiraService.getTicketForDeclaration(d.id).subscribe({
              next: () => { this.jiraLoading[d.id!] = false; },
              error: () => { this.jiraLoading[d.id!] = false; }
            });
          }
        });
      },
      error: () => {
        this.loading = false;
        if (!silent) this.toast.error('Erreur chargement des déclarations en attente');
      }
    });
  }

  // ── Filtres ────────────────────────────────────────────
  get uniqueTypes(): string[] {
    return [...new Set(this.pending.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  get filteredPending(): Declaration[] {
    let list = [...this.pending];

    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(d =>
        String(d.id).includes(q) ||
        d.declarationType?.code?.toLowerCase().includes(q) ||
        d.declarationType?.nom?.toLowerCase().includes(q) ||
        d.generePar?.toLowerCase().includes(q) ||
        d.periode?.toLowerCase().includes(q)
      );
    }

    if (this.filtreType) list = list.filter(d => d.declarationType?.code === this.filtreType);

    if (this.priorityFilter === 'REJETE')  list = list.filter(d => !!d.commentaireRejet);
    if (this.priorityFilter === 'NOUVEAU') list = list.filter(d => !d.commentaireRejet);

    list.sort((a, b) => {
      let va: any, vb: any;
      switch (this.sortField) {
        case 'id':      va = a.id ?? 0;      vb = b.id ?? 0; break;
        case 'type':    va = a.declarationType?.code ?? ''; vb = b.declarationType?.code ?? ''; break;
        case 'periode': va = a.periode ?? ''; vb = b.periode ?? ''; break;
        case 'date':
          va = a.dateGeneration ? new Date(a.dateGeneration).getTime() : 0;
          vb = b.dateGeneration ? new Date(b.dateGeneration).getTime() : 0;
          break;
      }
      const cmp = va < vb ? -1 : va > vb ? 1 : 0;
      return this.sortDir === 'asc' ? cmp : -cmp;
    });

    return list;
  }

  setPriorityFilter(f: PriorityFilter): void { this.priorityFilter = f; }

  toggleSort(field: SortField): void {
    if (this.sortField === field) this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    else { this.sortField = field; this.sortDir = 'desc'; }
  }

  getSortIcon(field: SortField): string {
    if (this.sortField !== field) return '↕';
    return this.sortDir === 'asc' ? '↑' : '↓';
  }

  // ── Panneau Lecteur ────────────────────────────────────
  ouvrirLecteur(d: Declaration): void {
    // Toggle : fermer si déjà ouvert sur la même déclaration
    if (this.readerDeclaration?.id === d.id && this.showReaderPanel) {
      this.fermerLecteur();
      return;
    }
    this.readerDeclaration = d;
    this.readerIndex = this.filteredPending.findIndex(x => x.id === d.id);
    this.readerTab   = 'details';
    this.readerLogs  = [];
    this.readerNotes = '';
    this.showReaderPanel = true;

    // Réinitialiser la checklist
    this.checklistItems = this.DEFAULT_CHECKLIST.map(item => ({ ...item, checked: false }));
  }

  fermerLecteur(): void {
    this.showReaderPanel  = false;
    this.readerDeclaration = null;
    this.readerLogs       = [];
  }

  naviguerDeclaration(direction: -1 | 1): void {
    const newIndex = this.readerIndex + direction;
    if (newIndex < 0 || newIndex >= this.filteredPending.length) return;
    this.readerIndex = newIndex;
    const d = this.filteredPending[newIndex];
    this.readerDeclaration = d;
    this.readerTab   = 'details';
    this.readerLogs  = [];
    this.readerNotes = '';
    this.checklistItems = this.DEFAULT_CHECKLIST.map(item => ({ ...item, checked: false }));
  }

  chargerHistoriqueReader(): void {
    this.readerTab = 'historique';
    if (!this.readerDeclaration?.id || this.readerLogs.length > 0) return;
    this.readerLogsLoading = true;
    this.validationService.getHistory(this.readerDeclaration.id).subscribe({
      next: logs => { this.readerLogs = logs; this.readerLogsLoading = false; },
      error: () => { this.readerLogsLoading = false; }
    });
  }

  // ── Checklist ──────────────────────────────────────────
  getCheckCount(): number {
    return this.checklistItems.filter(i => i.checked).length;
  }

  getCheckPercent(): number {
    if (!this.checklistItems.length) return 0;
    return Math.round((this.getCheckCount() / this.checklistItems.length) * 100);
  }

  // ── Actions CRUD ───────────────────────────────────────
  valider(d: Declaration): void {
    if (!d.id) return;
    this.confirmDialog.confirm(
      'Valider la déclaration',
      `Valider la déclaration #${d.id} — ${d.declarationType?.nom} ?`,
      {
        detail: `Période : ${d.periode}\nAgent : ${d.generePar ?? '-'}`,
        confirmLabel: 'Valider',
        type: 'info'
      }
    ).then(confirmed => {
      if (!confirmed) return;
      this.actionEnCours[d.id!] = true;
      this.validationService.validateDeclaration(d.id!).subscribe({
        next: () => {
          this.pending = this.pending.filter(x => x.id !== d.id);
          this.actionEnCours[d.id!] = false;
          if (this.readerDeclaration?.id === d.id) this.fermerLecteur();
          this.rafraichirStats();
          this.jiraService.invalidateCache(d.id!);
          this.toast.success(`Déclaration #${d.id} validée avec succès.`);
        },
        error: err => {
          this.actionEnCours[d.id!] = false;
          this.toast.error(err.error?.error || err.message || 'Erreur lors de la validation');
        }
      });
    });
  }

  ouvrirRejet(d: Declaration): void {
    this.declarationSelectionnee    = d;
    this.commentaireRejet           = '';
    this.commentaireRejetTouched    = false;
    this.selectedTemplate           = '';
    this.showRejetModal             = true;
  }

  fermerRejet(): void {
    this.showRejetModal          = false;
    this.declarationSelectionnee = null;
    this.commentaireRejet        = '';
    this.rejetEnCours            = false;
    this.selectedTemplate        = '';
  }

  appliquerTemplate(template: RejectTemplate): void {
    this.commentaireRejet = template.text;
    this.selectedTemplate = template.id;
  }

  confirmerRejet(): void {
    this.commentaireRejetTouched = true;
    if (!this.declarationSelectionnee?.id || !this.commentaireRejet.trim()) return;
    const id = this.declarationSelectionnee.id;
    this.rejetEnCours = true;
    this.validationService.rejectDeclaration(id, this.commentaireRejet.trim()).subscribe({
      next: () => {
        this.pending = this.pending.filter(x => x.id !== id);
        this.fermerRejet();
        if (this.readerDeclaration?.id === id) this.fermerLecteur();
        this.rafraichirStats();
        this.jiraService.invalidateCache(id);
        this.toast.error(`Déclaration #${id} rejetée.`);
      },
      error: err => {
        this.rejetEnCours = false;
        this.toast.error('Erreur : ' + (err.error?.error || err.message));
      }
    });
  }

  loadRejectTemplates(): void {
    this.validationService.getRejectTemplates().subscribe({
      next: t => this.rejectTemplates = t,
      error: () => {}
    });
  }

  // ── Bulk ───────────────────────────────────────────────
  toggleSelect(id: number): void {
    if (this.selectedIds.has(id)) this.selectedIds.delete(id);
    else this.selectedIds.add(id);
  }

  async validerSelection(): Promise<void> {
    if (this.selectedIds.size === 0) return;
    const ids = [...this.selectedIds];
    const confirmed = await this.confirmDialog.confirm(
      'Validation groupée',
      `Valider les ${ids.length} déclaration(s) sélectionnée(s) ?`,
      { confirmLabel: 'Valider tout', type: 'info' }
    );
    if (!confirmed) return;
    this.bulkLoading = true;
    let success = 0, errors = 0;
    for (const id of ids) {
      try {
        await this.validationService.validateDeclaration(id).toPromise();
        success++;
        this.pending = this.pending.filter(x => x.id !== id);
        this.selectedIds.delete(id);
        this.jiraService.invalidateCache(id);
      } catch { errors++; }
    }
    this.bulkLoading = false;
    this.rafraichirStats();
    if (errors === 0) {
      this.toast.success(`${success} déclaration(s) validée(s) avec succès.`);
    } else {
      this.toast.warning(`${success} validée(s), ${errors} erreur(s).`);
    }
  }

  // ── Modal Historique standalone ────────────────────────
  voirHistorique(d: Declaration): void {
    if (!d.id) return;
    this.declarationSelectionnee = d;
    this.historique = []; this.historiqueLoading = true; this.showHistoriqueModal = true;
    this.validationService.getHistory(d.id).subscribe({
      next: logs => { this.historique = logs; this.historiqueLoading = false; },
      error: () => { this.historiqueLoading = false; }
    });
  }

  fermerHistorique(): void {
    this.showHistoriqueModal = false;
    this.historique = [];
    this.declarationSelectionnee = null;
  }

  // ── Jira ───────────────────────────────────────────────
  getJiraTicket(id: number): JiraTicketResponse | null { return this.jiraTicketMap.get(id) ?? null; }

  ouvrirJira(d: Declaration): void {
    const ticket = this.getJiraTicket(d.id!);
    if (ticket) this.jiraService.openJiraTicket(ticket);
  }

  // ── Download ───────────────────────────────────────────
  download(d: Declaration): void {
    if (!d.id) return;
    this.declarationService.downloadDeclaration(d.id).subscribe({
      next: blob => {
        const mime = this.declarationService.resolveMimeType(d.nomFichier || '');
        const url = window.URL.createObjectURL(new Blob([blob], { type: mime }));
        const a = document.createElement('a'); a.href = url;
        a.download = d.nomFichier || `declaration_${d.id}`; a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Erreur téléchargement')
    });
  }

  // ── Helpers ────────────────────────────────────────────
  getFileExtension(filename: string): string {
    return filename.split('.').pop() || 'fichier';
  }

  getStatutClass(s: string): string {
    return ({
      GENEREE: 'sc-generee', EN_VALIDATION: 'sc-validation',
      VALIDEE: 'sc-validee', REJETEE: 'sc-rejetee', ENVOYEE: 'sc-envoyee'
    } as Record<string, string>)[s] || '';
  }

  getStatutLabel(s: string): string {
    return ({
      GENEREE: 'Générée', EN_VALIDATION: 'En validation',
      VALIDEE: 'Validée', REJETEE: 'Rejetée', ENVOYEE: 'Traitée'
    } as Record<string, string>)[s] || s;
  }

  getActionClass(a: string): string {
    return ({ SUBMIT: 'act-submit', VALIDATE: 'act-validate', REJECT: 'act-reject', SEND: 'act-send' } as Record<string, string>)[a] || '';
  }

  getActionLabel(a: string): string {
    return ({ SUBMIT: '📤 Soumission', VALIDATE: '✅ Validation', REJECT: '❌ Rejet', SEND: '📨 Envoi BCT' } as Record<string, string>)[a] || a;
  }

  trackById(_: number, d: Declaration): number { return d.id ?? 0; }

  formatTime(date: Date): string {
    return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  private rafraichirStats(): void {
    this.validationService.getStats().subscribe({ next: s => this.stats = s, error: () => {} });
  }

  private showToast(msg: string, type: 'success' | 'error' | 'info'): void {
    // Delegates to ToastService — kept for any remaining template references
    if (type === 'success') this.toast.success(msg);
    else if (type === 'error') this.toast.error(msg);
    else this.toast.info(msg);
    this.message = msg; this.messageType = type;
    setTimeout(() => this.message = '', 5000);
  }
}