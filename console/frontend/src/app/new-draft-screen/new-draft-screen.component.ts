// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component } from '@angular/core';
import { Router } from '@angular/router';

import { FormContainerComponent } from '../form-container/form-container.component';
import { DraftService } from '../app/draft.service';
import { NewDraftEditorComponent } from '../new-draft-editor/new-draft-editor.component';
import { NewDraftForm } from '../new-draft-editor/new-draft-form';
import { ProgressIndicatorComponent } from '../progress-indicator/progress-indicator.component';
import { NotLoading, Loading, LoadingFailed, ProgressState } from '../progress-indicator/progress.state';

@Component({
    selector: 'app-new-draft-screen',
    standalone: true,
    styleUrls: ["./new-draft-screen.component.scss"],
    imports: [FormContainerComponent, NewDraftEditorComponent, ProgressIndicatorComponent],
    templateUrl: './new-draft-screen.component.html',
})
export class NewDraftScreenComponent {
    loadingState: ProgressState = { kind: 'NotLoading' };
    
    constructor(private draftService: DraftService, private router: Router) {}

    submitNewDraft(form: NewDraftForm): void {
        // I don't know how else to resolve the scope issue in the subscribe object
        const component = this;
        component.loadingState = { kind: 'NotLoading' };
        this.draftService.createDraft(form.apkSet, form.icon, form.label).subscribe({
            next: (event) => {
                console.log(event);
                if (typeof event === "number") {
                    component.loadingState = { kind: 'Loading', progress: event };
                } else {
                    this.router.navigate(["apps"]);
                }
            }, 
            error: (err) => {
                console.log(err);
                component.loadingState = { kind: 'LoadingFailed', error: err.error };
            }
        });
    }
}
