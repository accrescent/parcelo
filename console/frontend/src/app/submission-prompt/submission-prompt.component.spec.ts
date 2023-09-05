import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SubmissionPromptComponent } from './submission-prompt.component';

describe('SubmissionPromptComponent', () => {
  let component: SubmissionPromptComponent;
  let fixture: ComponentFixture<SubmissionPromptComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SubmissionPromptComponent]
    });
    fixture = TestBed.createComponent(SubmissionPromptComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
