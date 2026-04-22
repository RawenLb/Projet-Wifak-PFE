// src/app/services/xml-correction.service.ts
import { Injectable } from '@angular/core';

export interface ParsedXmlField {
  path:  string;
  tag:   string;
  value: string;
  depth: number;
  line:  number;
}

export interface XmlCorrectionResult {
  isValid:  boolean;
  errors:   string[];
  warnings: string[];
}

@Injectable({ providedIn: 'root' })
export class XmlCorrectionService {

  validateXmlSyntax(xml: string): XmlCorrectionResult {
    const result: XmlCorrectionResult = { isValid: true, errors: [], warnings: [] };
    if (!xml?.trim()) { result.isValid = false; result.errors.push('XML vide'); return result; }

    const parser = new DOMParser();
    const doc    = parser.parseFromString(xml, 'application/xml');
    const err    = doc.querySelector('parsererror');
    if (err) {
      result.isValid = false;
      result.errors.push('XML invalide : ' + (err.textContent?.substring(0, 200) ?? ''));
    }
    return result;
  }

  extractFieldsFromXml(xml: string): ParsedXmlField[] {
    const fields: ParsedXmlField[] = [];
    const parser = new DOMParser();
    const doc    = parser.parseFromString(xml, 'application/xml');
    if (doc.querySelector('parsererror')) return fields;
    this.walkNode(doc.documentElement, '', 0, fields);
    return fields;
  }

  private walkNode(el: Element, parentPath: string, depth: number, out: ParsedXmlField[]): void {
    const path = parentPath ? `${parentPath}/${el.tagName}` : el.tagName;
    const children = Array.from(el.children);
    if (children.length === 0) {
      out.push({ path, tag: el.tagName, value: el.textContent ?? '', depth, line: 0 });
    } else {
      children.forEach(c => this.walkNode(c, path, depth + 1, out));
    }
  }

  diffXml(original: string, modified: string): Map<string, { original: string; modified: string }> {
    const origFields = this.extractFieldsFromXml(original);
    const modFields  = this.extractFieldsFromXml(modified);
    const diff       = new Map<string, { original: string; modified: string }>();

    const origMap = new Map(origFields.map(f => [f.path, f.value]));
    const modMap  = new Map(modFields.map(f  => [f.path, f.value]));

    for (const [path, val] of modMap) {
      const orig = origMap.get(path) ?? '';
      if (orig !== val) diff.set(path, { original: orig, modified: val });
    }
    return diff;
  }
}