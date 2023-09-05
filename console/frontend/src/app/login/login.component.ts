import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { FormContainerModule } from '../form-container/form-container.module';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss'],
    standalone: true,
    imports: [MatButtonModule, FormContainerModule],
})
export class LoginComponent { }
