// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UpdateSubmissionDialogComponent } from './update-submission-dialog.component';

describe('UpdateSubmissionDialogComponent', () => {
    let component: UpdateSubmissionDialogComponent;
    let fixture: ComponentFixture<UpdateSubmissionDialogComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [UpdateSubmissionDialogComponent]
        });
        fixture = TestBed.createComponent(UpdateSubmissionDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
