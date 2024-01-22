// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditDeletionDialogComponent } from './edit-deletion-dialog.component';

describe('EditDeletionDialogComponent', () => {
    let component: EditDeletionDialogComponent;
    let fixture: ComponentFixture<EditDeletionDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EditDeletionDialogComponent]
        })
            .compileComponents();

        fixture = TestBed.createComponent(EditDeletionDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
