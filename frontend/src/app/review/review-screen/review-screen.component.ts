// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, OnInit } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { Draft } from '../../draft';
import { Edit } from '../../edit';
import { DraftService } from '../draft.service';
import { EditService } from '../edit.service';
import { ReviewDialogComponent } from '../review-dialog/review-dialog.component';
import { ReviewerDraftCardComponent } from '../reviewer-draft-card/reviewer-draft-card.component';
import { ReviewerEditCardComponent } from '../reviewer-edit-card/reviewer-edit-card.component';
import {
    ReviewerUpdateCardComponent,
} from '../reviewer-update-card/reviewer-update-card.component';
import { Update } from '../../update';
import { UpdateService } from '../update.service';

@Component({
    selector: 'app-review-screen',
    standalone: true,
    imports: [
        MatDialogModule,
        NgFor,
        NgIf,
        ReviewerDraftCardComponent,
        ReviewerEditCardComponent,
        ReviewerUpdateCardComponent,
    ],
    templateUrl: './review-screen.component.html',
    styleUrl: './review-screen.component.scss',
})
export class ReviewScreenComponent implements OnInit {
    drafts: Draft[] = [];
    edits: Edit[] = [];
    updates: Update[] = [];

    constructor(
        private dialog: MatDialog,
        private draftService: DraftService,
        private editService: EditService,
        private updateService: UpdateService,
    ) {}

    ngOnInit(): void {
        this.draftService.getAssigned().subscribe(drafts => this.drafts = drafts);
        this.editService.getAssigned().subscribe(edits => this.edits = edits);
        this.updateService.getAssigned().subscribe(updates => this.updates = updates);
    }

    openDraftReviewDialog(draftId: string): void {
        this.dialog
            .open(ReviewDialogComponent)
            .afterClosed()
            .subscribe(review => {
                if (review !== undefined) {
                    this.draftService.createReviewForDraft(draftId, review).subscribe(() => {
                        // Remove draft card from the UI
                        const i = this.drafts.findIndex(d => d.id === draftId);
                        if (i > -1) {
                            this.drafts.splice(i, 1);
                        }
                    });
                }
            });
    }

    openEditReviewDialog(editId: string): void {
        this.dialog
            .open(ReviewDialogComponent)
            .afterClosed()
            .subscribe(review => {
                if (review !== undefined) {
                    this.editService.createReviewForEdit(editId, review).subscribe(() => {
                        // Remove edit card from the UI
                        const i = this.edits.findIndex(e => e.id === editId);
                        if (i > -1) {
                            this.edits.splice(i, 1);
                        }
                    });
                }
            });
    }

    openUpdateReviewDialog(updateId: string): void {
        this.dialog
            .open(ReviewDialogComponent)
            .afterClosed()
            .subscribe(review => {
                if (review !== undefined) {
                    this.updateService.createReviewForUpdate(updateId, review).subscribe(() => {
                        // Remove update card from the UI
                        const i = this.updates.findIndex(u => u.id === updateId);
                        if (i > -1) {
                            this.updates.splice(i, 1);
                        }
                    });
                }
            });
    }
}
