import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RegisterUnauthorizedComponent } from './register-unauthorized.component';

describe('RegisterUnauthorizedComponent', () => {
    let component: RegisterUnauthorizedComponent;
    let fixture: ComponentFixture<RegisterUnauthorizedComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [RegisterUnauthorizedComponent]
        });
        fixture = TestBed.createComponent(RegisterUnauthorizedComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
