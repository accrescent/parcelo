// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditCardComponent } from './edit-card.component';

describe('EditCardComponent', () => {
    let component: EditCardComponent;
    let fixture: ComponentFixture<EditCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EditCardComponent]
        })
            .compileComponents();

        fixture = TestBed.createComponent(EditCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
