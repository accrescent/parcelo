import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

// TODO: Add interceptor to disallow localStorage weirdness

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    standalone: true,
    imports: [RouterOutlet],
})
export class AppComponent {
    title = 'frontend';
}
