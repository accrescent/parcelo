// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { authGuard } from './auth/auth.guard';
import { loginGuard } from './login/login.guard';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { ConsoleLayoutComponent } from './console-layout/console-layout.component';
import { PageNotFoundComponent } from './page-not-found/page-not-found.component';
import { AppsScreenComponent } from './apps-screen/apps-screen.component';
import { NewDraftScreenComponent } from './new-draft-screen/new-draft-screen.component';

const routes: Routes = [
    { path: '', redirectTo: 'login', pathMatch: 'full', canMatch: [loginGuard]},
    { path: '', redirectTo: 'apps', pathMatch: 'full', canMatch: [authGuard]},
    { path: 'login', component: LoginComponent, canActivate: [loginGuard]},
    { path: '', component: ConsoleLayoutComponent, canActivate: [authGuard], children: [
        { path: 'apps', component: AppsScreenComponent },
        { path: 'drafts/new', component: NewDraftScreenComponent },
    ] },
    { path: 'auth/github/callback', component: RegisterComponent },
    { path: '**', component: PageNotFoundComponent }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
