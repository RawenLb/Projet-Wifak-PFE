import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeclarationTypeManagementComponent } from './declaration-type-management.component';

describe('DeclarationTypeManagementComponent', () => {
  let component: DeclarationTypeManagementComponent;
  let fixture: ComponentFixture<DeclarationTypeManagementComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [DeclarationTypeManagementComponent]
    });
    fixture = TestBed.createComponent(DeclarationTypeManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
