import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeclarationCalendarComponent } from './declaration-calendar.component';

describe('DeclarationCalendarComponent', () => {
  let component: DeclarationCalendarComponent;
  let fixture: ComponentFixture<DeclarationCalendarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [DeclarationCalendarComponent]
    });
    fixture = TestBed.createComponent(DeclarationCalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
