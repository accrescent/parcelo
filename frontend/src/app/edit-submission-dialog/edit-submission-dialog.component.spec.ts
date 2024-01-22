// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditSubmissionDialogComponent } from './edit-submission-dialog.component';

describe('EditSubmissionDialogComponent', () => {
    let component: EditSubmissionDialogComponent;
    let fixture: ComponentFixture<EditSubmissionDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EditSubmissionDialogComponent]
        })
            .compileComponents();

        fixture = TestBed.createComponent(EditSubmissionDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
