// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DraftDeletionDialogComponent } from './draft-deletion-dialog.component';

describe('DraftDeletionDialogComponent', () => {
    let component: DraftDeletionDialogComponent;
    let fixture: ComponentFixture<DraftDeletionDialogComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DraftDeletionDialogComponent]
        });
        fixture = TestBed.createComponent(DraftDeletionDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
