// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DraftCardComponent } from './draft-card.component';

describe('DraftCardComponent', () => {
    let component: DraftCardComponent;
    let fixture: ComponentFixture<DraftCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DraftCardComponent]
        });
        fixture = TestBed.createComponent(DraftCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
