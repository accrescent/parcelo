import { Component, OnInit } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
  imports: [MatProgressSpinnerModule, NgIf],
  standalone: true
})
export class RegisterComponent implements OnInit {
  loading = true;

  constructor(private authService: AuthService, private activatedRoute: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.activatedRoute.queryParams.subscribe(params => {
      this.authService.logIn(params['code'], params['state']).subscribe(res => {
        if (res) {
          this.router.navigate(['dashboard']);
        } else {
          this.loading = false;
        }
      })
    })
  }
}
