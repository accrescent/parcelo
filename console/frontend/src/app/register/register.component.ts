import { Component, OnInit } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormContainerComponent } from '../form-container/form-container.component';

import { AuthService } from '../auth/auth.service';
import { AuthError } from '../auth/auth';

@Component({
    selector: 'app-register',
    templateUrl: './register.component.html',
    styleUrls: ['./register.component.scss'],
    imports: [NgIf, MatCardModule, MatProgressSpinnerModule, MatButtonModule, FormContainerComponent],
    standalone: true
})
export class RegisterComponent implements OnInit {
    error: AuthError | null = null;

    constructor(private authService: AuthService, private activatedRoute: ActivatedRoute, private router: Router) {}

    ngOnInit(): void {
        this.activatedRoute.queryParams.subscribe(params => {
            // No other reasonable way to update based on several observable outcomes without using a this reference.
            // eslint-disable-next-line @typescript-eslint/no-this-alias
            const component = this;
            this.authService.logIn(params['code'], params['state']).subscribe({
                complete: () => component.router.navigate(['/']),
                error: (err) => component.error = err as AuthError
            });
        });
    }

    public get authError(): typeof AuthError {
        return AuthError; 
    }
}
