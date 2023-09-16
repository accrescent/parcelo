// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';

import { DraftService } from '../draft.service';
import { NewDraftEditorComponent } from '../new-draft-editor/new-draft-editor.component';
import { NewDraftForm } from '../new-draft-form';

@Component({
    selector: 'app-new-draft-screen',
    standalone: true,
    imports: [MatProgressBarModule, NewDraftEditorComponent],
    templateUrl: './new-draft-screen.component.html',
})
export class NewDraftScreenComponent {
    uploadProgress = 0;

    constructor(private draftService: DraftService, private router: Router) {}

    submitNewDraft(form: NewDraftForm): void {
        this.draftService.createDraft(form.apkSet, form.icon, form.label).subscribe(event => {
            if (event.type === HttpEventType.UploadProgress) {
                this.uploadProgress = 100 * event.loaded / event.total!;
            } else if (event instanceof HttpResponse) {
                this.router.navigate(['apps']);
            }
        });
    }
}
