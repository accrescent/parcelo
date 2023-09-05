import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { FormContainerComponent } from './form-container.component';

@NgModule({
  declarations: [
    FormContainerComponent
  ],
  exports: [
    FormContainerComponent
  ],
  imports: [
    CommonModule
  ]
})
export class FormContainerModule { }
