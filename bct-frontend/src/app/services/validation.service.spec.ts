import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ValidationService, ValidationStats, ValidationLog } from './Validation.service';
import { Declaration } from './Declaration.service';

describe('ValidationService', () => {
  let service: ValidationService;
  let httpMock: HttpTestingController;
  const BASE = 'http://localhost:8088/api/validation';

  const mockDecl: Declaration = { id: 1, statut: 'EN_VALIDATION', periode: '2025-01' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ValidationService],
    });
    service = TestBed.inject(ValidationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── submitForValidation ─────────────────────────────────────────

  it('submitForValidation(1) → POST /api/validation/1/submit', () => {
    service.submitForValidation(1).subscribe(res => expect(res.statut).toBe('EN_VALIDATION'));
    const r = httpMock.expectOne(`${BASE}/1/submit`);
    expect(r.request.method).toBe('POST');
    r.flush(mockDecl);
  });

  it('submitForValidation(1, comment) → POST avec query param', () => {
    service.submitForValidation(1, 'correction effectuée').subscribe();
    const r = httpMock.expectOne(`${BASE}/1/submit?correctionComment=correction%20effectu%C3%A9e`);
    expect(r.request.method).toBe('POST');
    r.flush(mockDecl);
  });

  // ── validateDeclaration ─────────────────────────────────────────

  it('validateDeclaration(1) → POST /api/validation/1/validate', () => {
    service.validateDeclaration(1).subscribe(res => expect(res.id).toBe(1));
    const r = httpMock.expectOne(`${BASE}/1/validate`);
    expect(r.request.method).toBe('POST');
    r.flush({ ...mockDecl, statut: 'VALIDEE' });
  });

  // ── rejectDeclaration ──────────────────────────────────────────

  it('rejectDeclaration(1, ...) → POST /api/validation/1/reject', () => {
    service.rejectDeclaration(1, 'Données incorrectes').subscribe();
    const r = httpMock.expectOne(`${BASE}/1/reject`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({ commentaire: 'Données incorrectes' });
    r.flush({ ...mockDecl, statut: 'REJETEE' });
  });

  // ── markAsSent ─────────────────────────────────────────────────

  it('markAsSent(1) → POST /api/validation/1/send', () => {
    service.markAsSent(1).subscribe();
    const r = httpMock.expectOne(`${BASE}/1/send`);
    expect(r.request.method).toBe('POST');
    r.flush({ ...mockDecl, statut: 'ENVOYEE' });
  });

  // ── getPendingDeclarations ──────────────────────────────────────

  it('getPendingDeclarations() → GET /api/validation/pending', () => {
    service.getPendingDeclarations().subscribe(res => expect(res.length).toBe(1));
    httpMock.expectOne(`${BASE}/pending`).flush([mockDecl]);
  });

  // ── getStats ───────────────────────────────────────────────────

  it('getStats() → GET /api/validation/stats', () => {
    const stats: ValidationStats = {
      total: 10, generees: 2, enValidation: 3,
      validees: 2, rejetees: 1, envoyees: 2,
    };
    service.getStats().subscribe(res => expect(res.total).toBe(10));
    httpMock.expectOne(`${BASE}/stats`).flush(stats);
  });

  // ── getHistory ─────────────────────────────────────────────────

  it('getHistory(1) → GET /api/validation/1/history', () => {
    const logs: ValidationLog[] = [
      {
        id: 1, declarationId: 1, action: 'SUBMIT',
        statutAvant: 'GENEREE', statutApres: 'EN_VALIDATION',
        effectuePar: 'agent1', dateAction: '2025-01-15T10:00:00',
      },
    ];
    service.getHistory(1).subscribe(res => expect(res.length).toBe(1));
    httpMock.expectOne(`${BASE}/1/history`).flush(logs);
  });

  // ── getRejectTemplates ─────────────────────────────────────────

  it('getRejectTemplates() → GET /api/validation/reject-templates', () => {
    service.getRejectTemplates().subscribe(res => expect(res).toBeTruthy());
    httpMock.expectOne(`${BASE}/reject-templates`).flush([]);
  });
});
