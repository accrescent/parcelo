// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Draft } from '../app/draft';
import { MatButtonModule } from '@angular/material/button';

@Component({
    selector: 'app-submission-prompt',
    templateUrl: './submission-prompt.component.html',
    styleUrls: ['./submission-prompt.component.scss'],
    imports: [MatButtonModule],
    standalone: true
})
export class SubmissionPromptComponent {
    @Input() draft: Draft = {} as Draft;
    @Output() confirm = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();
  
    confirmSubmission(): void {
        this.confirm.emit();
    }

    cancelSubmission(): void {
        this.cancel.emit();
    }
}
