// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AppListComponent } from './app-list/app-list.component';
import { ConsoleLayoutComponent } from './console-layout/console-layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { LandingComponent } from './landing/landing.component';
import { RegisterUnauthorizedComponent } from './register-unauthorized/register-unauthorized.component';

const routes: Routes = [
    { path: '', component: LandingComponent },
    { path: '', component: ConsoleLayoutComponent, children: [
        { path: 'apps', component: AppListComponent },
        { path: 'dashboard', component: DashboardComponent },
    ] },
    { path: 'register/unauthorized', component: RegisterUnauthorizedComponent },
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
