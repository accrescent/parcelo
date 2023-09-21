// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewUpdateScreenComponent } from './new-update-screen.component';

describe('NewUpdateScreenComponent', () => {
    let component: NewUpdateScreenComponent;
    let fixture: ComponentFixture<NewUpdateScreenComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NewUpdateScreenComponent]
        });
        fixture = TestBed.createComponent(NewUpdateScreenComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
