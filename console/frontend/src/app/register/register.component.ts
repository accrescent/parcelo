import { Component, OnInit } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth/auth.service';
import { AuthError } from '../auth/auth';

@Component({
    selector: 'app-register',
    templateUrl: './register.component.html',
    styleUrls: ['./register.component.scss'],
    imports: [NgIf, MatCardModule, MatProgressSpinnerModule, MatButtonModule],
    standalone: true
})
export class RegisterComponent implements OnInit {
    error: AuthError | null = null;

    constructor(private authService: AuthService, private activatedRoute: ActivatedRoute, private router: Router) {}

    ngOnInit(): void {
        this.activatedRoute.queryParams.subscribe(params => {
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
