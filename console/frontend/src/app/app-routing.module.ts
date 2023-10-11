// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { authGuard } from './auth.guard';

const routes: Routes = [
    {
        path: '',
        loadComponent: () =>
            import('./console-layout/console-layout.component').then(m => m.ConsoleLayoutComponent),
        canActivate: [authGuard],
        children: [
            { path: '', redirectTo: 'apps', pathMatch: 'full' },
            {
                path: 'apps',
                loadComponent: () =>
                    import('./apps-screen/apps-screen.component').then(m => m.AppsScreenComponent),
            },
            {
                path: 'apps/:id/updates',
                loadComponent: () => import('./updates-screen/updates-screen.component')
                    .then(m => m.UpdatesScreenComponent),
            },
            {
                path: 'drafts/new',
                loadComponent: () => import('./new-draft-screen/new-draft-screen.component')
                    .then(m => m.NewDraftScreenComponent),
            },
            {
                path: 'review',
                loadChildren: () => import('./review/review.routes').then(m => m.REVIEW_ROUTES),
            },
        ],
    },
    {
        path: 'login',
        loadComponent: () =>
            import('./login-screen/login-screen.component').then(m => m.LoginScreenComponent),
    },
    {
        path: 'auth/github/callback',
        loadComponent: () => import('./login/login.component').then(m => m.LoginComponent),
    },
    {
        path: '**',
        loadComponent: () =>
            import('./page-not-found/page-not-found.component').then(m => m.PageNotFoundComponent),
    },
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
