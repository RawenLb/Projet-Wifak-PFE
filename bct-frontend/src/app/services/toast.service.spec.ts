import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ToastService, Toast } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;
  let toastsEmitted: Toast[][];

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ToastService] });
    service = TestBed.inject(ToastService);
    toastsEmitted = [];
    service.toasts$.subscribe(t => toastsEmitted.push([...t]));
  });

  // ── show ────────────────────────────────────────────────────────

  it('show() — émet un toast avec les bonnes propriétés', () => {
    service.show('success', 'Opération réussie', 3000);
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last.length).toBe(1);
    expect(last[0].type).toBe('success');
    expect(last[0].message).toBe('Opération réussie');
    expect(last[0].duration).toBe(3000);
    expect(last[0].id).toBeGreaterThan(0);
  });

  it('show() — chaque toast a un id unique', () => {
    service.show('info', 'Toast 1');
    service.show('info', 'Toast 2');
    const ids = toastsEmitted.map(t => t[t.length - 1]?.id).filter(Boolean);
    const uniqueIds = new Set(ids);
    expect(uniqueIds.size).toBe(ids.length);
  });

  it('show() — accumule plusieurs toasts', () => {
    service.show('success', 'A');
    service.show('error', 'B');
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last.length).toBe(2);
  });

  // ── raccourcis ──────────────────────────────────────────────────

  it('success() → type success', () => {
    service.success('Bien');
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last[0].type).toBe('success');
    expect(last[0].duration).toBe(4000);
  });

  it('error() → type error avec durée 6000', () => {
    service.error('Erreur');
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last[0].type).toBe('error');
    expect(last[0].duration).toBe(6000);
  });

  it('warning() → type warning avec durée 5000', () => {
    service.warning('Attention');
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last[0].type).toBe('warning');
    expect(last[0].duration).toBe(5000);
  });

  it('info() → type info', () => {
    service.info('Info');
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last[0].type).toBe('info');
  });

  // ── dismiss ─────────────────────────────────────────────────────

  it('dismiss() — supprime le toast par id', () => {
    service.show('success', 'À supprimer');
    const id = toastsEmitted[toastsEmitted.length - 1][0].id;
    service.dismiss(id);
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last.find(t => t.id === id)).toBeUndefined();
  });

  it('dismiss() — ne plante pas si id inexistant', () => {
    expect(() => service.dismiss(9999)).not.toThrow();
  });

  // ── auto-dismiss ────────────────────────────────────────────────

  it('show() — auto-dismiss après la durée', fakeAsync(() => {
    service.show('info', 'Auto', 1000);
    const id = toastsEmitted[toastsEmitted.length - 1][0].id;
    tick(1000);
    const last = toastsEmitted[toastsEmitted.length - 1];
    expect(last.find(t => t.id === id)).toBeUndefined();
  }));
});
