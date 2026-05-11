import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentNotificationsComponent } from './agent-notifications.component';

describe('AgentNotificationsComponent', () => {
  let component: AgentNotificationsComponent;
  let fixture: ComponentFixture<AgentNotificationsComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AgentNotificationsComponent]
    });
    fixture = TestBed.createComponent(AgentNotificationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
