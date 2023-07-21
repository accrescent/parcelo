import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RegisterOkComponent } from './register-ok.component';

describe('RegisterOkComponent', () => {
  let component: RegisterOkComponent;
  let fixture: ComponentFixture<RegisterOkComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [RegisterOkComponent]
    });
    fixture = TestBed.createComponent(RegisterOkComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
