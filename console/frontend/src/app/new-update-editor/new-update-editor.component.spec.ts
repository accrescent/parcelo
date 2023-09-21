// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewUpdateEditorComponent } from './new-update-editor.component';

describe('NewUpdateEditorComponent', () => {
    let component: NewUpdateEditorComponent;
    let fixture: ComponentFixture<NewUpdateEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NewUpdateEditorComponent]
        });
        fixture = TestBed.createComponent(NewUpdateEditorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
