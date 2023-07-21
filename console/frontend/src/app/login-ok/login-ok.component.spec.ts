// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoginOkComponent } from './login-ok.component';

describe('LoginOkComponent', () => {
    let component: LoginOkComponent;
    let fixture: ComponentFixture<LoginOkComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [LoginOkComponent]
        });
        fixture = TestBed.createComponent(LoginOkComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
