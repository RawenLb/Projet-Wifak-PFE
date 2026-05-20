import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ManagerPendingComponent } from './manager-pending.component';

describe('ManagerPendingComponent', () => {
  let component: ManagerPendingComponent;
  let fixture: ComponentFixture<ManagerPendingComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ManagerPendingComponent]
    });
    fixture = TestBed.createComponent(ManagerPendingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
