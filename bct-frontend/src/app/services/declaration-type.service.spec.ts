import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import {
  DeclarationTypeService,
  DeclarationType,
  CreateDeclarationTypeRequest,
  ValidationRule,
  SqlTestResult,
} from './declaration-type.service';

describe('DeclarationTypeService', () => {
  let service: DeclarationTypeService;
  let httpMock: HttpTestingController;
  const API = 'http://localhost:8088/api/admin/declaration-types';

  const mockType: DeclarationType = {
    id: 1,
    code: 'DECL_TEST',
    nom: 'Test',
    format: 'XML',
    frequence: 'MENSUELLE',
    dateLimite: '2025-01-31',
    actif: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [DeclarationTypeService],
    });
    service = TestBed.inject(DeclarationTypeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── getAll ──────────────────────────────────────────────────────

  it('getAll() → GET /api/admin/declaration-types', () => {
    service.getAll().subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].code).toBe('DECL_TEST');
    });
    httpMock.expectOne(API).flush([mockType]);
  });

  // ── getById ─────────────────────────────────────────────────────

  it('getById(1) → GET /api/admin/declaration-types/1', () => {
    service.getById(1).subscribe(res => expect(res.id).toBe(1));
    httpMock.expectOne(`${API}/1`).flush(mockType);
  });

  // ── create ──────────────────────────────────────────────────────

  it('create() → POST /api/admin/declaration-types', () => {
    const req: CreateDeclarationTypeRequest = {
      code: 'NEW', nom: 'Nouveau', format: 'XML',
      frequence: 'MENSUELLE', dateLimite: '2025-01-31', actif: true,
    };
    service.create(req).subscribe(res => expect(res.code).toBe('NEW'));
    const r = httpMock.expectOne(API);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual(req);
    r.flush({ ...mockType, code: 'NEW' });
  });

  // ── update ──────────────────────────────────────────────────────

  it('update(1, ...) → PUT /api/admin/declaration-types/1', () => {
    service.update(1, mockType).subscribe();
    const r = httpMock.expectOne(`${API}/1`);
    expect(r.request.method).toBe('PUT');
    r.flush(mockType);
  });

  // ── delete ──────────────────────────────────────────────────────

  it('delete(1) → DELETE /api/admin/declaration-types/1', () => {
    service.delete(1).subscribe();
    const r = httpMock.expectOne(`${API}/1`);
    expect(r.request.method).toBe('DELETE');
    r.flush(null);
  });

  // ── toggleStatus ────────────────────────────────────────────────

  it('toggleStatus(1) → PATCH /api/admin/declaration-types/1/toggle', () => {
    service.toggleStatus(1).subscribe(res => expect(res.actif).toBeFalse());
    const r = httpMock.expectOne(`${API}/1/toggle`);
    expect(r.request.method).toBe('PATCH');
    r.flush({ ...mockType, actif: false });
  });

  // ── getValidationRules ──────────────────────────────────────────

  it('getValidationRules(1) → GET /api/admin/declaration-types/1/validation-rules', () => {
    const rules: ValidationRule[] = [
      { id: 1, champConcerne: 'montant', typeValidation: 'REQUIRED', messageErreur: 'Requis', obligatoire: true },
    ];
    service.getValidationRules(1).subscribe(res => expect(res.length).toBe(1));
    httpMock.expectOne(`${API}/1/validation-rules`).flush(rules);
  });

  // ── downloadTemplate ────────────────────────────────────────────

  it('downloadTemplate(1) → GET /api/admin/declaration-types/1/template (blob)', () => {
    service.downloadTemplate(1).subscribe(blob => expect(blob).toBeTruthy());
    const r = httpMock.expectOne(`${API}/1/template`);
    expect(r.request.responseType).toBe('blob');
    r.flush(new Blob(['<xml/>']));
  });

  // ── downloadXsd ─────────────────────────────────────────────────

  it('downloadXsd(1) → GET /api/admin/declaration-types/1/xsd/download (blob)', () => {
    service.downloadXsd(1).subscribe(blob => expect(blob).toBeTruthy());
    const r = httpMock.expectOne(`${API}/1/xsd/download`);
    expect(r.request.responseType).toBe('blob');
    r.flush(new Blob(['<xsd/>']));
  });

  // ── saveSqlQuery ────────────────────────────────────────────────

  it('saveSqlQuery(1, ...) → PATCH /api/admin/declaration-types/1/sql', () => {
    const sql = 'SELECT * FROM declarations';
    service.saveSqlQuery(1, sql).subscribe();
    const r = httpMock.expectOne(`${API}/1/sql`);
    expect(r.request.method).toBe('PATCH');
    expect(r.request.body).toEqual({ sqlQuery: sql });
    r.flush(mockType);
  });

  // ── testSqlQuery ────────────────────────────────────────────────

  it('testSqlQuery(1, ...) → POST /api/admin/declaration-types/1/sql/test', () => {
    const result: SqlTestResult = { success: true, nombreLignes: 5 };
    service.testSqlQuery(1, '2025-01-01', '2025-01-31').subscribe(res => {
      expect(res.success).toBeTrue();
      expect(res.nombreLignes).toBe(5);
    });
    const r = httpMock.expectOne(`${API}/1/sql/test`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({ dateDebut: '2025-01-01', dateFin: '2025-01-31' });
    r.flush(result);
  });
});
