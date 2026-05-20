import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DeclarationService, Declaration, DeclarationStats } from './Declaration.service';

describe('DeclarationService', () => {
  let service: DeclarationService;
  let httpMock: HttpTestingController;
  const baseUrl = 'http://localhost:8088/api/declarations';

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [DeclarationService],
    });
    service = TestBed.inject(DeclarationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── resolveMimeType — méthode pure ──────────────────────────────

  it('resolveMimeType — .csv → text/csv', () => {
    expect(service.resolveMimeType('rapport.csv')).toContain('text/csv');
  });

  it('resolveMimeType — .txt → text/plain', () => {
    expect(service.resolveMimeType('rapport.txt')).toContain('text/plain');
  });

  it('resolveMimeType — .json → application/json', () => {
    expect(service.resolveMimeType('data.json')).toBe('application/json');
  });

  it('resolveMimeType — .pdf → application/pdf', () => {
    expect(service.resolveMimeType('doc.pdf')).toBe('application/pdf');
  });

  it('resolveMimeType — .xml → application/xml', () => {
    expect(service.resolveMimeType('decl.xml')).toContain('application/xml');
  });

  it('resolveMimeType — extension inconnue → application/xml par défaut', () => {
    expect(service.resolveMimeType('fichier.xyz')).toContain('application/xml');
  });

  it('resolveMimeType — nom vide → application/xml par défaut', () => {
    expect(service.resolveMimeType('')).toContain('application/xml');
  });

  // ── getAllDeclarations ──────────────────────────────────────────

  it('getAllDeclarations() → GET /api/declarations', () => {
    const mockDeclarations: Declaration[] = [
      { id: 1, statut: 'GENEREE', periode: '2025-01' },
      { id: 2, statut: 'VALIDEE', periode: '2025-02' },
    ];

    service.getAllDeclarations().subscribe(decls => {
      expect(decls.length).toBe(2);
      expect(decls[0].statut).toBe('GENEREE');
    });

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush(mockDeclarations);
  });

  // ── getDeclarationById ─────────────────────────────────────────

  it('getDeclarationById(1) → GET /api/declarations/1', () => {
    const mockDecl: Declaration = { id: 1, statut: 'GENEREE', periode: '2025-01' };

    service.getDeclarationById(1).subscribe(decl => {
      expect(decl.id).toBe(1);
    });

    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDecl);
  });

  // ── generateDeclaration ────────────────────────────────────────

  it('generateDeclaration() → POST /api/declarations/generate', () => {
    const request = {
      declarationTypeId: 1,
      periode: '2025-01',
      dateDebut: '2025-01-01',
      dateFin: '2025-01-31',
    };
    const mockDecl: Declaration = { id: 10, statut: 'GENEREE', periode: '2025-01' };

    service.generateDeclaration(request).subscribe(decl => {
      expect(decl.id).toBe(10);
      expect(decl.statut).toBe('GENEREE');
    });

    const req = httpMock.expectOne(`${baseUrl}/generate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockDecl);
  });

  // ── deleteDeclaration ──────────────────────────────────────────

  it('deleteDeclaration(1) → DELETE /api/declarations/1', () => {
    service.deleteDeclaration(1).subscribe();

    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  // ── getStats ───────────────────────────────────────────────────

  it('getStats() → GET /api/declarations/stats', () => {
    const mockStats: DeclarationStats = {
      total: 10, generees: 3, enValidation: 2,
      validees: 2, rejetees: 1, envoyees: 2,
    };

    service.getStats().subscribe(stats => {
      expect(stats.total).toBe(10);
      expect(stats.generees).toBe(3);
    });

    const req = httpMock.expectOne(`${baseUrl}/stats`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStats);
  });
});
