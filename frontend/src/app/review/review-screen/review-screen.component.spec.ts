// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReviewScreenComponent } from './review-screen.component';

describe('ReviewScreenComponent', () => {
    let component: ReviewScreenComponent;
    let fixture: ComponentFixture<ReviewScreenComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReviewScreenComponent]
        });
        fixture = TestBed.createComponent(ReviewScreenComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
