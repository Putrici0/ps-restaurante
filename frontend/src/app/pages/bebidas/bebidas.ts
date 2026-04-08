import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { combineLatest, forkJoin, interval, of } from 'rxjs';
import { catchError, startWith, switchMap, take } from 'rxjs/operators';

import {
  EstadoOrdenBackend,
  OrdenCocinaResponse,
  OrdenesApiService,
} from '../../services/ordenes-api.service';

type EstadoVisualPedido = 'pendiente' | 'preparando' | 'listo';

interface ItemAgrupadoBebida {
  nombre: string;
  cantidad: number;
}

interface PedidoBebidaAgrupado {
  pedidoId: string;
  cuentaId: string;
  mesaId: string;
  fechaPedido: string;
  estado: EstadoVisualPedido;
  ordenesIds: string[];
  items: ItemAgrupadoBebida[];
  totalItems: number;
}

@Component({
  selector: 'app-bebidas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bebidas.html',
  styleUrl: './bebidas.css',
})
export class BebidasComponent {
  private readonly ordenesApi = inject(OrdenesApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly procesandoPedidoId = signal<string | null>(null);

  readonly pedidos = signal<PedidoBebidaAgrupado[]>([]);

  readonly pedidosOrdenados = computed(() => {
    const prioridad: Record<EstadoVisualPedido, number> = {
      pendiente: 0,
      preparando: 1,
      listo: 2,
    };

    return [...this.pedidos()].sort((a, b) => {
      const porEstado = prioridad[a.estado] - prioridad[b.estado];
      if (porEstado !== 0) {
        return porEstado;
      }

      const fechaA = new Date(a.fechaPedido).getTime();
      const fechaB = new Date(b.fechaPedido).getTime();
      return fechaA - fechaB;
    });
  });

  readonly pendientesCount = computed(
    () => this.pedidos().filter((p) => p.estado !== 'listo').length,
  );

  constructor() {
    interval(2000)
      .pipe(
        startWith(0),
        switchMap(() =>
          combineLatest([
            this.ordenesApi.obtenerPendientesBarra(),
            this.ordenesApi.obtenerEnPreparacionBarra(),
            this.ordenesApi.obtenerListasBarra(),
          ]).pipe(
            catchError((error) => {
              console.error(error);
              this.error.set('No se pudieron cargar las bebidas.');
              this.cargando.set(false);
              return of([
                [] as OrdenCocinaResponse[],
                [] as OrdenCocinaResponse[],
                [] as OrdenCocinaResponse[],
              ]);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(([pendientes, enPreparacion, listas]) => {
        const todas = [...pendientes, ...enPreparacion, ...listas];
        this.pedidos.set(this.agruparPorPedido(todas));
        this.cargando.set(false);
        this.error.set(null);
      });
  }

  avanzarEstado(pedido: PedidoBebidaAgrupado): void {
    if (!pedido.ordenesIds.length) {
      return;
    }

    this.procesandoPedidoId.set(pedido.pedidoId);

    const nuevoEstado: EstadoVisualPedido =
      pedido.estado === 'pendiente' ? 'preparando' : 'listo';

    this.actualizarEstadoLocal(pedido.pedidoId, nuevoEstado);

    const accion =
      pedido.estado === 'pendiente'
        ? this.ordenesApi.marcarEnPreparacion.bind(this.ordenesApi)
        : this.ordenesApi.marcarLista.bind(this.ordenesApi);

    forkJoin(pedido.ordenesIds.map((id) => accion(id)))
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.procesandoPedidoId.set(null);
        },
        error: (error) => {
          console.error(error);
          this.error.set('No se pudo actualizar el estado del pedido de bebidas.');
          this.procesandoPedidoId.set(null);
          this.recargar();
        },
      });
  }

  retrocederEstado(pedido: PedidoBebidaAgrupado): void {
    if (!pedido.ordenesIds.length) {
      return;
    }

    this.procesandoPedidoId.set(pedido.pedidoId);

    const nuevoEstado: EstadoVisualPedido =
      pedido.estado === 'listo' ? 'preparando' : 'pendiente';

    this.actualizarEstadoLocal(pedido.pedidoId, nuevoEstado);

    const accion =
      pedido.estado === 'listo'
        ? this.ordenesApi.marcarEnPreparacion.bind(this.ordenesApi)
        : this.ordenesApi.marcarPendiente.bind(this.ordenesApi);

    forkJoin(pedido.ordenesIds.map((id) => accion(id)))
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.procesandoPedidoId.set(null);
        },
        error: (error) => {
          console.error(error);
          this.error.set('No se pudo actualizar el estado del pedido de bebidas.');
          this.procesandoPedidoId.set(null);
          this.recargar();
        },
      });
  }

  private recargar(): void {
    combineLatest([
      this.ordenesApi.obtenerPendientesBarra(),
      this.ordenesApi.obtenerEnPreparacionBarra(),
      this.ordenesApi.obtenerListasBarra(),
    ])
      .pipe(take(1))
      .subscribe({
        next: ([pendientes, enPreparacion, listas]) => {
          const todas = [...pendientes, ...enPreparacion, ...listas];
          this.pedidos.set(this.agruparPorPedido(todas));
        },
      });
  }

  private actualizarEstadoLocal(
    pedidoId: string,
    estado: EstadoVisualPedido,
  ): void {
    this.pedidos.update((lista) =>
      lista.map((pedido) =>
        pedido.pedidoId === pedidoId ? { ...pedido, estado } : pedido,
      ),
    );
  }

  private agruparPorPedido(ordenes: OrdenCocinaResponse[]): PedidoBebidaAgrupado[] {
    const visibles = ordenes.filter((orden) => {
      const esBebida = orden.plato?.categoria === 'Bebida';
      const cuentaPagada = orden.pedido?.cuenta?.payed === true;
      return esBebida && !cuentaPagada;
    });

    const mapa = new Map<string, PedidoBebidaAgrupado>();

    for (const orden of visibles) {
      const pedidoId = orden.pedido?.id;
      if (!pedidoId) {
        continue;
      }

      const cuentaId = orden.pedido?.cuenta?.id ?? '';
      const mesaId =
        orden.pedido?.cuenta?.mesas?.[0]?.id?.toString?.() ??
        orden.pedido?.cuenta?.mesas?.[0]?.id ??
        'Sin asignar';

      const estado = this.mapearEstado(orden.ordenEstado);

      if (!mapa.has(pedidoId)) {
        mapa.set(pedidoId, {
          pedidoId,
          cuentaId,
          mesaId: String(mesaId),
          fechaPedido: orden.pedido?.fechaPedido ?? orden.fecha,
          estado,
          ordenesIds: [],
          items: [],
          totalItems: 0,
        });
      }

      const grupo = mapa.get(pedidoId)!;
      grupo.ordenesIds.push(orden.id);
      grupo.totalItems += 1;

      if (estado === 'pendiente') {
        grupo.estado = 'pendiente';
      } else if (estado === 'preparando' && grupo.estado !== 'pendiente') {
        grupo.estado = 'preparando';
      }

      const itemExistente = grupo.items.find(
        (item) => item.nombre === orden.plato.nombre,
      );

      if (itemExistente) {
        itemExistente.cantidad += 1;
      } else {
        grupo.items.push({
          nombre: orden.plato.nombre,
          cantidad: 1,
        });
      }
    }

    return Array.from(mapa.values()).map((pedido) => ({
      ...pedido,
      items: [...pedido.items].sort((a, b) => a.nombre.localeCompare(b.nombre)),
    }));
  }

  private mapearEstado(estado: EstadoOrdenBackend): EstadoVisualPedido {
    switch (estado) {
      case 'Pendiente':
        return 'pendiente';
      case 'Preparación':
        return 'preparando';
      case 'Listo':
        return 'listo';
      default:
        return 'pendiente';
    }
  }
}
