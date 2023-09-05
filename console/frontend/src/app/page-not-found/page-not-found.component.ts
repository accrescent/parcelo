import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';

import { FormContainerModule } from '../form-container/form-container.module';

@Component({
  selector: 'app-page-not-found',
  templateUrl: './page-not-found.component.html',
  styleUrls: ['./page-not-found.component.scss'],
  imports: [MatCardModule, MatButtonModule, FormContainerModule],
  standalone: true
})
export class PageNotFoundComponent {

}
