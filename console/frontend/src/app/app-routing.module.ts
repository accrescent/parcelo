// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AppsScreenComponent } from './apps-screen/apps-screen.component';
import { authGuard } from './auth.guard';
import { ConsoleLayoutComponent } from './console-layout/console-layout.component';
import { LoginComponent } from './login/login.component';
import { LoginScreenComponent } from './login-screen/login-screen.component';
import { PageNotFoundComponent } from './page-not-found/page-not-found.component';
import { NewDraftScreenComponent } from './new-draft-screen/new-draft-screen.component';

const routes: Routes = [
    { path: '', component: ConsoleLayoutComponent, canActivate: [authGuard], children: [
        { path: '', redirectTo: 'apps', pathMatch: 'full' },
        { path: 'apps', component: AppsScreenComponent },
        { path: 'drafts/new', component: NewDraftScreenComponent },
    ] },
    { path: 'login', component: LoginScreenComponent },
    { path: 'auth/github/callback', component: LoginComponent },
    { path: '**', component: PageNotFoundComponent },
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
