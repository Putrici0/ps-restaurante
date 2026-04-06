import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

export type EstadoPedido = 'pendiente' | 'listo';

export interface ItemPedido {
  cantidad: number;
  nombre: string;
}

export interface PedidoBebida {
  id: string;
  mesa: number;
  estado: EstadoPedido;
  tiempo: string;
  items: ItemPedido[];
}

@Component({
  selector: 'app-bebidas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bebidas.html',
  styleUrl: './bebidas.css',
})
export class BebidasComponent {
  // Estado principal
  pedidos = signal<PedidoBebida[]>([
    {
      id: '1',
      mesa: 5,
      estado: 'pendiente',
      tiempo: '5 min',
      items: [
        { cantidad: 2, nombre: 'Cervezas Tropical' },
        { cantidad: 2, nombre: 'Cliper de Fresa' },
        { cantidad: 1, nombre: 'Botella de vino Yaiza' },
      ],
    },
    {
      id: '2',
      mesa: 15,
      estado: 'pendiente',
      tiempo: '5 min',
      items: [
        { cantidad: 2, nombre: 'Cervezas Tropical' },
        { cantidad: 2, nombre: 'Cliper de Fresa' },
        { cantidad: 1, nombre: 'Botella de vino Yaiza' },
      ],
    },
    {
      id: '3',
      mesa: 1,
      estado: 'pendiente',
      tiempo: '5 min',
      items: [
        { cantidad: 2, nombre: 'Cervezas Tropical' },
        { cantidad: 2, nombre: 'Cliper de Fresa' },
        { cantidad: 1, nombre: 'Botella de vino Yaiza' },
      ],
    },
    {
      id: '4',
      mesa: 9,
      estado: 'listo',
      tiempo: '5 min',
      items: [
        { cantidad: 2, nombre: 'Cervezas Tropical' },
        { cantidad: 2, nombre: 'Cliper de Fresa' },
        { cantidad: 1, nombre: 'Botella de vino Yaiza' },
      ],
    },
    {
      id: '5',
      mesa: 20,
      estado: 'listo',
      tiempo: '5 min',
      items: [
        { cantidad: 2, nombre: 'Cervezas Tropical' },
        { cantidad: 2, nombre: 'Cliper de Fresa' },
        { cantidad: 1, nombre: 'Botella de vino Yaiza' },
      ],
    },
    {
      id: '6',
      mesa: 12,
      estado: 'listo',
      tiempo: '5 min',
      items: [
        { cantidad: 2, nombre: 'Cervezas Tropical' },
        { cantidad: 2, nombre: 'Cliper de Fresa' },
        { cantidad: 1, nombre: 'Botella de vino Yaiza' },
      ],
    },
  ]);

  pedidosOrdenados = computed(() => {
    return [...this.pedidos()].sort((a, b) => {
      if (a.estado === b.estado) return 0;
      return a.estado === 'pendiente' ? -1 : 1;
    });
  });

  pendientesCount = computed(() =>
    this.pedidos().filter(p => p.estado === 'pendiente').length
  );

  toggleEstado(pedido: PedidoBebida) {
    this.pedidos.update(lista => lista.map(p =>
      p.id === pedido.id
        ? { ...p, estado: p.estado === 'pendiente' ? 'listo' : 'pendiente' }
        : p
    ));
  }
}
