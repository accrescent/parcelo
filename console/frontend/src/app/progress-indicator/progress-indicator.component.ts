import { Component, Input } from '@angular/core';

import { NgIf } from '@angular/common';

import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ProgressState } from './progress.state';

@Component({
  selector: 'app-progress-indicator',
  templateUrl: './progress-indicator.component.html',
  imports: [NgIf, MatTooltipModule, MatProgressBarModule],
  styleUrls: ['./progress-indicator.component.scss'],
  standalone: true
})
export class ProgressIndicatorComponent {
  @Input() state: ProgressState = { kind: 'NotLoading' };
}
