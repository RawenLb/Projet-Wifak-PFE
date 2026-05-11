import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ManagerMlDashboardComponent } from './manager-ml-dashboard.component';

describe('ManagerMlDashboardComponent', () => {
  let component: ManagerMlDashboardComponent;
  let fixture: ComponentFixture<ManagerMlDashboardComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ManagerMlDashboardComponent]
    });
    fixture = TestBed.createComponent(ManagerMlDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
