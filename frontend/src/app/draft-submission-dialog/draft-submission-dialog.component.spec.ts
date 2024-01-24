// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DraftSubmissionDialogComponent } from './draft-submission-dialog.component';

describe('DraftSubmissionDialogComponent', () => {
    let component: DraftSubmissionDialogComponent;
    let fixture: ComponentFixture<DraftSubmissionDialogComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DraftSubmissionDialogComponent]
        });
        fixture = TestBed.createComponent(DraftSubmissionDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
