import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentDeclarationTypesComponent } from './agent-declaration-types.component';

describe('AgentDeclarationTypesComponent', () => {
  let component: AgentDeclarationTypesComponent;
  let fixture: ComponentFixture<AgentDeclarationTypesComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AgentDeclarationTypesComponent]
    });
    fixture = TestBed.createComponent(AgentDeclarationTypesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
