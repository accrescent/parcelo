// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewEditEditorComponent } from './new-edit-editor.component';

describe('NewEditEditorComponent', () => {
    let component: NewEditEditorComponent;
    let fixture: ComponentFixture<NewEditEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NewEditEditorComponent]
        });
        fixture = TestBed.createComponent(NewEditEditorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
