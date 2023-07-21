import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { authGuard } from './auth.guard';
import { AppListComponent } from './app-list/app-list.component';
import { ConsoleLayoutComponent } from './console-layout/console-layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { LandingComponent } from './landing/landing.component';
import { RegisterOkComponent } from './register-ok/register-ok.component';
import { RegisterUnauthorizedComponent } from './register-unauthorized/register-unauthorized.component';
import { PageNotFoundComponent } from './page-not-found/page-not-found.component';

const routes: Routes = [
    { path: '', component: LandingComponent },
    { path: '', component: ConsoleLayoutComponent, canActivate: [authGuard], children: [
        { path: 'apps', component: AppListComponent },
        { path: 'dashboard', component: DashboardComponent },
    ] },
    { path: 'register/ok', component: RegisterOkComponent },
    { path: 'register/unauthorized', component: RegisterUnauthorizedComponent },
    { path: '**', component: PageNotFoundComponent }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
