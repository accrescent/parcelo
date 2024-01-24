// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PublisherDraftCardComponent } from './publisher-draft-card.component';

describe('PublisherDraftCardComponent', () => {
    let component: PublisherDraftCardComponent;
    let fixture: ComponentFixture<PublisherDraftCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [PublisherDraftCardComponent]
        });
        fixture = TestBed.createComponent(PublisherDraftCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
