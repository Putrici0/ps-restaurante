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

  // Capturamos el id de la mesa desde la URL
  tableId = signal <string> (this.route.snapshot.params['id'] || '1');

  // Controla el estado del PopUp del camarero
  estadoLlamada = signal<'oculto' | 'confirmacion' | 'en-camino'>('oculto');

  abrirConfirmacion(event: Event) {
    event.preventDefault();
    this.estadoLlamada.set('confirmacion');
  }

  cancelarLlamada() {
    this.estadoLlamada.set('oculto');
  }

  confirmarLlamada() {
    this.estadoLlamada.set('en-camino');

    //Ocultamos el mensaje automáticamente después de 3 segundos
    setTimeout(() => {
      this.estadoLlamada.set('oculto');
    }, 3000);
  }
}
