import { Component } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../auth.service';

@Component({
    selector: 'app-console-layout',
    templateUrl: './console-layout.component.html',
    styleUrls: ['./console-layout.component.scss']
})
export class ConsoleLayoutComponent {
    constructor(private authService: AuthService, private router: Router) {}

    logOut(): void {
        this.authService.logOut().subscribe();
        this.router.navigate(['/']);
    }
}
