// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReviewerDraftCardComponent } from './reviewer-draft-card.component';

describe('ReviewerDraftCardComponent', () => {
    let component: ReviewerDraftCardComponent;
    let fixture: ComponentFixture<ReviewerDraftCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReviewerDraftCardComponent]
        });
        fixture = TestBed.createComponent(ReviewerDraftCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
