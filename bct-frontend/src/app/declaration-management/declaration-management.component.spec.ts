import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeclarationManagementComponent } from './declaration-management.component';

describe('DeclarationManagementComponent', () => {
  let component: DeclarationManagementComponent;
  let fixture: ComponentFixture<DeclarationManagementComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [DeclarationManagementComponent]
    });
    fixture = TestBed.createComponent(DeclarationManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
