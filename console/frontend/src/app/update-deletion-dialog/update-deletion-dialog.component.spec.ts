// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UpdateDeletionDialogComponent } from './update-deletion-dialog.component';

describe('UpdateDeletionDialogComponent', () => {
    let component: UpdateDeletionDialogComponent;
    let fixture: ComponentFixture<UpdateDeletionDialogComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [UpdateDeletionDialogComponent]
        });
        fixture = TestBed.createComponent(UpdateDeletionDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
