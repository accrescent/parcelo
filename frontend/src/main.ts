// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { importProvidersFrom } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';

import { AppComponent } from './app/app.component';
import { AppRoutingModule } from './app/app-routing.module';
import { unauthorizedInterceptor } from './app/unauthorized.interceptor';
import { withCredentialsInterceptor } from './app/with-credentials.interceptor';

bootstrapApplication(AppComponent, {
    providers: [
        importProvidersFrom(AppRoutingModule),
        provideAnimations(),
        provideHttpClient(withInterceptors([unauthorizedInterceptor, withCredentialsInterceptor]))
    ]
})
    .catch(err => console.error(err));
