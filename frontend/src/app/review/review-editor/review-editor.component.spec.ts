// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReviewEditorComponent } from './review-editor.component';

describe('ReviewEditorComponent', () => {
    let component: ReviewEditorComponent;
    let fixture: ComponentFixture<ReviewEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReviewEditorComponent]
        });
        fixture = TestBed.createComponent(ReviewEditorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
