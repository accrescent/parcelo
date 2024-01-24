// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReviewerUpdateCardComponent } from './reviewer-update-card.component';

describe('ReviewerUpdateCardComponent', () => {
    let component: ReviewerUpdateCardComponent;
    let fixture: ComponentFixture<ReviewerUpdateCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReviewerUpdateCardComponent]
        });
        fixture = TestBed.createComponent(ReviewerUpdateCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
