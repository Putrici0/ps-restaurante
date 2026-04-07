import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Header } from '../../../shared/components/header/header';
import { OrderService } from '../../../shared/services/order'; // Importar el servicio

interface Plato {
  id: number;
  nombre: string;
  precio: number;
  cantidad: number;
}

@Component({
  selector: 'app-menu-page',
  standalone: true,
  imports: [CommonModule, Header],
  templateUrl: './menu-page.html',
  styleUrls: ['./menu-page.css'],
})
export class MenuPage {
  private orderService = inject(OrderService);

  platos = signal<Plato[]>([
    { id: 1, nombre: 'Cerveza Reserva', precio: 5.8, cantidad: 0 },
    { id: 2, nombre: 'Croquetas de Jamón', precio: 11.0, cantidad: 0 },
    { id: 3, nombre: 'Tiramisú Casero', precio: 8.5, cantidad: 0 },
  ]);

  // Calculamos automáticamente cuantos platos se han seleccionado
  totalSeleccionado = computed(() => {
    return this.platos().reduce((acc, plato) => acc + plato.cantidad, 0);
  });

  incrementar(id: number) {
    this.platos.update((lista) =>
      lista.map((p) => (p.id === id ? { ...p, cantidad: p.cantidad + 1 } : p)),
    );
  }

  decrementar(id: number) {
    this.platos.update((lista) =>
      lista.map((p) => (p.id === id && p.cantidad > 0 ? { ...p, cantidad: p.cantidad - 1 } : p)),
    );
  }

  enviarPedido() {
    // Filtramos solo los platos que tienen cantidad mayor a 0
    const platosAPedir = this.platos().filter((p) => p.cantidad > 0);

    // Enviamos los platos al servicio
    this.orderService.agregarPedido(platosAPedir);

    // Reseteamos las cantidades del menú a 0 para que puedan pedir más cosas luego
    this.platos.update((lista) => lista.map((p) => ({ ...p, cantidad: 0 })));

    // Hacemo que el propio navegador envie un mensaje.
    alert('¡Pedido enviado a cocina!');
  }
}
