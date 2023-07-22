import { Component, OnInit } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth/auth.service';
import { AuthResult } from '../auth/auth.result';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
  imports: [MatProgressSpinnerModule, NgIf],
  standalone: true
})
export class RegisterComponent implements OnInit {
  result: AuthResult | null = null;

  constructor(private authService: AuthService, private activatedRoute: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.activatedRoute.queryParams.subscribe(params => {
      this.authService.logIn(params['code'], params['state']).subscribe(res => {
        this.result = res;
        if (this.result == AuthResult.OK) {
          this.router.navigate(['dashboard']);
        }
      })
    })
  }

  public get authResult(): typeof AuthResult {
    return AuthResult; 
  }
}
