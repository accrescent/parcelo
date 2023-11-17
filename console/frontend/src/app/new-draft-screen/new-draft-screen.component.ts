// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';

import { DraftService } from '../draft.service';
import {
    DraftSubmissionDialogComponent,
} from '../draft-submission-dialog/draft-submission-dialog.component';
import { NewDraftEditorComponent } from '../new-draft-editor/new-draft-editor.component';
import { NewDraftForm } from '../new-draft-form';

@Component({
    selector: 'app-new-draft-screen',
    standalone: true,
    imports: [MatDialogModule, MatProgressBarModule, NewDraftEditorComponent],
    templateUrl: './new-draft-screen.component.html',
})
export class NewDraftScreenComponent {
    uploadProgress = 0;

    constructor(
        private dialog: MatDialog,
        private draftService: DraftService,
        private router: Router,
    ) {}

    createDraft(form: NewDraftForm): void {
        this.draftService.createDraft(form.apkSet, form.icon, form.label).subscribe(event => {
            if (event.type === HttpEventType.UploadProgress) {
                this.uploadProgress = 100 * event.loaded / event.total!;

                // Clear the progress bar once the upload is complete
                if (event.loaded === event.total!) {
                    this.uploadProgress = 0;
                }
            } else if (event instanceof HttpResponse) {
                const draft = event.body!;
                this.dialog
                    .open(DraftSubmissionDialogComponent, { data: draft })
                    .afterClosed()
                    .subscribe(confirmed => {
                        if (confirmed) {
                            this.draftService.submitDraft(draft.id).subscribe(() => {
                                this.router.navigate(['apps']);
                            });
                        } else {
                            this.router.navigate(['apps']);
                        }
                    });
            }
        });
    }
}
