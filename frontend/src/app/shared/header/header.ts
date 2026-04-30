import { Component, signal, inject, computed } from '@angular/core';
import { RouterLink, RouterLinkActive, ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './header.html',
  styleUrls: ['./header.css'],
})
export class Header {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  tableId = signal(this.route.snapshot.params['id'] || '1');

  esCamarero = computed(() => this.router.url.includes('/camarero'));

  estadoLlamada = signal<'oculto' | 'confirmacion' | 'en-camino'>('oculto');

  menuAbierto = signal(false);

  toggleMenu() {
    this.menuAbierto.update(v => !v);
    document.body.style.overflow = this.menuAbierto() ? 'hidden' : '';
  }

  cerrarMenu() {
    this.menuAbierto.set(false);
    document.body.style.overflow = '';
  }

  abrirConfirmacion(event: Event) {
    event.preventDefault();
    this.estadoLlamada.set('confirmacion');
  }

  cancelarLlamada() {
    this.estadoLlamada.set('oculto');
  }

  confirmarLlamada() {
    this.estadoLlamada.set('en-camino');
    setTimeout(() => {
      this.estadoLlamada.set('oculto');
    }, 3000);
  }
}
