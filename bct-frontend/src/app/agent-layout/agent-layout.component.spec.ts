import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentLayoutComponent } from './agent-layout.component';

describe('AgentLayoutComponent', () => {
  let component: AgentLayoutComponent;
  let fixture: ComponentFixture<AgentLayoutComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AgentLayoutComponent]
    });
    fixture = TestBed.createComponent(AgentLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
