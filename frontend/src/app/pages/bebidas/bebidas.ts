import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

// 1. Añadimos el nuevo estado "preparando"
export type EstadoPedido = 'pendiente' | 'preparando' | 'listo';

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
    // Ponemos uno en 'preparando' para que veas el nuevo color
    {
      id: '2',
      mesa: 15,
      estado: 'preparando',
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

  private audioNotificacion = new Audio('audio/campana.mp3');

  recibirNuevoPedido(nuevoPedido: PedidoBebida) {
    this.reproducirSonido();
    this.pedidos.update((lista) => [...lista, nuevoPedido]);
  }

  private reproducirSonido() {
    this.audioNotificacion.currentTime = 0;
    this.audioNotificacion.play().catch((error) => {
      console.warn('El navegador bloqueó el sonido:', error);
    });
  }

  // 2. Ordenamos por 3 niveles de prioridad
  pedidosOrdenados = computed(() => {
    const prioridad = { pendiente: 1, preparando: 2, listo: 3 };
    return [...this.pedidos()].sort((a, b) => prioridad[a.estado] - prioridad[b.estado]);
  });

  // Solo contamos los que aún no están listos
  pendientesCount = computed(() => this.pedidos().filter((p) => p.estado !== 'listo').length);

  // 3. Nuevas funciones para el flujo
  avanzarEstado(pedido: PedidoBebida) {
    this.pedidos.update((lista) =>
      lista.map((p) => {
        if (p.id === pedido.id) {
          if (p.estado === 'pendiente') return { ...p, estado: 'preparando' };
          if (p.estado === 'preparando') return { ...p, estado: 'listo' };
        }
        return p;
      }),
    );
  }

  retrocederEstado(pedido: PedidoBebida) {
    this.pedidos.update(lista => lista.map(p => {
      if (p.id === pedido.id) {
        // Si está listo, vuelve a preparando
        if (p.estado === 'listo') return { ...p, estado: 'preparando' };
        // Si se está preparando, vuelve a la cola de pendientes
        if (p.estado === 'preparando') return { ...p, estado: 'pendiente' };
      }
      return p;
    }));
  }
}
