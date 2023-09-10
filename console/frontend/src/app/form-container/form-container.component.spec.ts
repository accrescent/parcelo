import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FormContainerComponent } from './form-container.component';

describe('FormContainerComponent', () => {
    let component: FormContainerComponent;
    let fixture: ComponentFixture<FormContainerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [FormContainerComponent]
        });
        fixture = TestBed.createComponent(FormContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
