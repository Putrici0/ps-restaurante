import { Component, signal, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './header.html',
  styleUrls: ['./header.css'],
})
export class Header {
  private route = inject(ActivatedRoute);

  tableId = signal(this.route.snapshot.params['id'] || '1');

  menuAbierto = signal(false);

  toggleMenu() {
    this.menuAbierto.update(v => !v);

    // bloquear scroll cuando el menú está abierto
    document.body.style.overflow = this.menuAbierto() ? 'hidden' : '';
  }

  cerrarMenu() {
    this.menuAbierto.set(false);

    // restaurar scroll
    document.body.style.overflow = '';
  }
}
