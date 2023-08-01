import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { authGuard } from './auth/auth.guard';
import { loginGuard } from './login/login.guard';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { ConsoleLayoutComponent } from './console-layout/console-layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { AppListComponent } from './app-list/app-list.component';
import { NewAppFormComponent } from './new-app-form/new-app-form.component';
import { PageNotFoundComponent } from './page-not-found/page-not-found.component';

const routes: Routes = [
    { path: '', redirectTo: 'login', pathMatch: 'full', canMatch: [loginGuard]},
    { path: '', redirectTo: 'dashboard', pathMatch: 'full', canMatch: [authGuard]},
    { path: 'login', component: LoginComponent, canActivate: [loginGuard]},
    { path: '', component: ConsoleLayoutComponent, canActivate: [authGuard], children: [
        { path: 'apps', component: AppListComponent },
        { path: 'dashboard', component: DashboardComponent },
        { path: 'new-app', component: NewAppFormComponent }
    ] },
    { path: 'auth/github/callback', component: RegisterComponent },
    { path: '**', component: PageNotFoundComponent }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
