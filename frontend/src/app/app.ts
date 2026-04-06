import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router'; // <-- 1. Asegúrate de que esto esté importado

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: "'<router-outlet></router-outlet>'",
  styleUrls: [],
})
export class AppComponent {
  title = 'frontend';
}
