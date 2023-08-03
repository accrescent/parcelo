import { Component, OnInit } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth/auth.service';
import { Permissions, AuthError } from '../auth/auth';

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
            this.authService.logIn(params['code'], params['state']).subscribe(res => {
                if ((res as Permissions).reviewer) {
                    this.router.navigate(['/']);
                } else {
                    this.error = res as AuthError;
                }
            });
        });
    }

    public get authError(): typeof AuthError {
        return AuthError; 
    }
}
