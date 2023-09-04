// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AppsScreenComponent } from './apps-screen/apps-screen.component';
import { ConsoleLayoutComponent } from './console-layout/console-layout.component';
import { LandingComponent } from './landing/landing.component';
import { NewDraftScreenComponent } from './new-draft-screen/new-draft-screen.component';
import { RegisterUnauthorizedComponent } from './register-unauthorized/register-unauthorized.component';

const routes: Routes = [
    { path: '', component: LandingComponent },
    { path: '', component: ConsoleLayoutComponent, children: [
        { path: 'apps', component: AppsScreenComponent },
        { path: 'drafts/new', component: NewDraftScreenComponent },
    ] },
    { path: 'register/unauthorized', component: RegisterUnauthorizedComponent },
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
