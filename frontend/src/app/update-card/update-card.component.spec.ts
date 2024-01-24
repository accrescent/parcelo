// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UpdateCardComponent } from './update-card.component';

describe('UpdateCardComponent', () => {
    let component: UpdateCardComponent;
    let fixture: ComponentFixture<UpdateCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [UpdateCardComponent]
        });
        fixture = TestBed.createComponent(UpdateCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
