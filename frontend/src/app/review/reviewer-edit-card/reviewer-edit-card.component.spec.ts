// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReviewerEditCardComponent } from './reviewer-edit-card.component';

describe('ReviewerEditCardComponent', () => {
    let component: ReviewerEditCardComponent;
    let fixture: ComponentFixture<ReviewerEditCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReviewerEditCardComponent]
        })
            .compileComponents();

        fixture = TestBed.createComponent(ReviewerEditCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
