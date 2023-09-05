import { Component } from '@angular/core';
import { NgIf, NgFor } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl, NonNullableFormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';

import { Draft } from '../app/app';
import { AppService } from '../app/app.service';
import { ProgressState } from '../progress-indicator/progress.state';

@Component({
    selector: 'app-new-app-form',
    templateUrl: './new-app-form.component.html',
    styleUrls: ['./new-app-form.component.scss'],
    imports: [NgIf, NgFor, FormsModule, ReactiveFormsModule, MatInputModule, MatTooltipModule,
        MatFormFieldModule, MatProgressBarModule, MatButtonModule, MatCardModule],
    standalone: true
})
export class NewAppFormComponent {
    label = new FormControl('', [Validators.required, Validators.minLength(3), Validators.maxLength(20)]);
    app = new FormControl('', Validators.required);
    icon = new FormControl('', Validators.required);
    uploadForm = this.fb.group({
        label: this.label,
        app: this.app,
        icon: this.icon,
    });
    draft: Draft | undefined = undefined;
    loadingState: ProgressState = {} as NotLoading;

    constructor(
        private fb: NonNullableFormBuilder,
        private appService: AppService,
        private router: Router,
    ) {}

    getLabelErrorMessage(): string {
        if (this.label.hasError('required')) {
            return 'This field is required';
        }

        if (this.label.hasError('minlength') || this.label.hasError('maxlength')) {
            return 'Must be between 3 and 20 characters';
        }

        return '';
    }

    onUpload(): void {
        const label = (<HTMLInputElement>document.getElementById("label")).value;
        const icon = (<HTMLInputElement>document.getElementById("icon")).files?.[0];
        const app = (<HTMLInputElement>document.getElementById("app")).files?.[0];

        if (app !== undefined && icon !== undefined) {
            const component = this;
            this.uploadProgress = 0;
            this.error = undefined;
            this.draft = undefined;
            this.appService.upload(label, app, icon).subscribe({
                next: (event) => {
                    if (typeof event === "number") {
                        component.uploadProgress = event as number;
                    } else {
                        component.draft = event as Draft;
                    }
                }, 
                error: (err) => component.error = err
            });
        }
    }

    onConfirm(): void {
        if (this.draft === undefined) throw new Error("Invalid draft state on confirmation");
        const component = this;
        this.appService.confirm(this.draft).subscribe({
            next: () => this.router.navigate(["apps"]),
            error: (err) => component.error = err
        });
    }

    onSkipConfirm(): void {
        this.router.navigate(["apps"]);
    }
}