// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewDraftEditorComponent } from './new-draft-editor.component';

describe('NewDraftEditorComponent', () => {
    let component: NewDraftEditorComponent;
    let fixture: ComponentFixture<NewDraftEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NewDraftEditorComponent]
        });
        fixture = TestBed.createComponent(NewDraftEditorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
