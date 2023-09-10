// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgIf, NgFor } from '@angular/common';

import { FormContainerComponent } from '../form-container/form-container.component';
import { DraftService } from '../app/draft.service';
import { NewDraftEditorComponent } from '../new-draft-editor/new-draft-editor.component';
import { NewDraftForm } from '../new-draft-editor/new-draft-form';
import { ProgressIndicatorComponent } from '../progress-indicator/progress-indicator.component';
import { ProgressState } from '../progress-indicator/progress.state';
import { Draft } from '../app/draft';
import { SubmissionPromptComponent } from '../submission-prompt/submission-prompt.component';

@Component({
    selector: 'app-new-draft-screen',
    standalone: true,
    styleUrls: ["./new-draft-screen.component.scss"],
    imports: [FormContainerComponent, NewDraftEditorComponent, ProgressIndicatorComponent, NgIf, NgFor, SubmissionPromptComponent],
    templateUrl: './new-draft-screen.component.html',
})
export class NewDraftScreenComponent {
    draft: Draft | undefined = undefined;
    loadingState: ProgressState = { kind: 'NotLoading' };
    
    constructor(private draftService: DraftService, private router: Router) {}

    uploadDraft(form: NewDraftForm): void {
        // No other reasonable way to update based on several observable outcomes without using a this reference.
        // eslint-disable-next-line @typescript-eslint/no-this-alias
        const component = this;
        component.loadingState = { kind: 'NotLoading' };
        this.draftService.createDraft(form.apkSet, form.icon, form.label).subscribe({
            next: (event) => {
                console.log(event);
                if (typeof event === "number") {
                    component.loadingState = { kind: 'Loading', progress: event };
                } else {
                    component.draft = event;
                }
            }, 
            error: (err) => {
                console.log(err);
                component.loadingState = { kind: 'LoadingFailed', error: err.error };
            }
        });
    }

    submitDraft(draft: Draft): void {
        this.draftService.submitDraft(draft.id).subscribe(() => this.exit());        
    }

    exit(): void {
        this.router.navigate(["apps"]);
    }
}
