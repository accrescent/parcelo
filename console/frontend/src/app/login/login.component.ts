import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { FormContainerComponent } from '../form-container/form-container.component';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss'],
    standalone: true,
    imports: [MatButtonModule, FormContainerComponent],
})
export class LoginComponent { }
