import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeclarationCorrectionComponent } from './declaration-correction.component';

describe('DeclarationCorrectionComponent', () => {
  let component: DeclarationCorrectionComponent;
  let fixture: ComponentFixture<DeclarationCorrectionComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [DeclarationCorrectionComponent]
    });
    fixture = TestBed.createComponent(DeclarationCorrectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
