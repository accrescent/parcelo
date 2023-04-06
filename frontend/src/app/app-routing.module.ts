import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { ConsoleLayoutComponent } from './console-layout/console-layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { LandingComponent } from './landing/landing.component';

const routes: Routes = [
    { path: '', component: LandingComponent },
    { path: '', component: ConsoleLayoutComponent, children: [
        { path: 'dashboard', component: DashboardComponent },
    ] },
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { }
