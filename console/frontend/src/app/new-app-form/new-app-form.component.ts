import { Component } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { NgIf, NgFor } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl, NonNullableFormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Draft } from '../app/app';
import { AppService } from '../app/app.service';

@Component({
    selector: 'app-new-app-form',
    templateUrl: './new-app-form.component.html',
    styleUrls: ['./new-app-form.component.scss'],
    imports: [NgIf, NgFor, FormsModule, ReactiveFormsModule, MatInputModule, MatFormFieldModule, MatProgressBarModule, MatButtonModule, MatCardModule],
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
    uploadProgress = 0;

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
            this.appService.upload(label, app, icon).subscribe(event => {
                if (event.type === HttpEventType.UploadProgress) {
                    this.uploadProgress = 100 * event.loaded / event.total!!;

                    // Clear the progress bar once the upload is complete
                    if (event.loaded === event.total) {
                        this.uploadProgress = 0;
                    }
                } else if (event instanceof HttpResponse) {
                    this.draft = event.body!!;
                }
            });
        }
    }
}