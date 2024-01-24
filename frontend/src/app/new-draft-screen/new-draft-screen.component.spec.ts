// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewDraftScreenComponent } from './new-draft-screen.component';

describe('NewDraftScreenComponent', () => {
    let component: NewDraftScreenComponent;
    let fixture: ComponentFixture<NewDraftScreenComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NewDraftScreenComponent]
        });
        fixture = TestBed.createComponent(NewDraftScreenComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
