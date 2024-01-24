// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReviewDialogComponent } from './review-dialog.component';

describe('ReviewDialogComponent', () => {
    let component: ReviewDialogComponent;
    let fixture: ComponentFixture<ReviewDialogComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReviewDialogComponent]
        });
        fixture = TestBed.createComponent(ReviewDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
