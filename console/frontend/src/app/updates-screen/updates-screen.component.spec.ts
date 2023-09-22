// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UpdatesScreenComponent } from './updates-screen.component';

describe('UpdatesScreenComponent', () => {
    let component: UpdatesScreenComponent;
    let fixture: ComponentFixture<UpdatesScreenComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [UpdatesScreenComponent]
        });
        fixture = TestBed.createComponent(UpdatesScreenComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
