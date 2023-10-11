// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, OnInit } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { Draft } from '../../draft';
import { DraftService } from '../draft.service';
import { ReviewDialogComponent } from '../review-dialog/review-dialog.component';
import { ReviewerDraftCardComponent } from '../reviewer-draft-card/reviewer-draft-card.component';

@Component({
    selector: 'app-review-screen',
    standalone: true,
    imports: [MatDialogModule, NgFor, NgIf, ReviewerDraftCardComponent],
    templateUrl: './review-screen.component.html',
    styleUrls: ['./review-screen.component.scss'],
})
export class ReviewScreenComponent implements OnInit {
    drafts: Draft[] = [];

    constructor(private dialog: MatDialog, private draftService: DraftService) {}

    ngOnInit(): void {
        this.draftService.getAssigned().subscribe(drafts => this.drafts = drafts);
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
}
