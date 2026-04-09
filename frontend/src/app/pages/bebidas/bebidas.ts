import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { combineLatest, forkJoin, interval, of } from 'rxjs';
import { catchError, startWith, switchMap, take } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { NavbarComponent } from '../../components/navbar/navbar';
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
  imports: [CommonModule, NavbarComponent],
  templateUrl: './bebidas.html',
  styleUrl: './bebidas.css',
})
export class BebidasComponent {
  private readonly ordenesApi = inject(OrdenesApiService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly pollingMs = 2500;
  private readonly refreshAfterWriteMs = 1500;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly procesandoPedidoId = signal<string | null>(null);
  readonly pausadoHasta = signal<number>(0);
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
    interval(this.pollingMs)
      .pipe(
        startWith(0),
        switchMap(() => {
          if (this.estaSincronizacionPausada()) {
            return of(null);
          }

          return combineLatest([
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
              ] as [
                OrdenCocinaResponse[],
                OrdenCocinaResponse[],
                OrdenCocinaResponse[],
              ]);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => {
        if (resultado === null) {
          return;
        }

        const [pendientes, enPreparacion, listas] = resultado;
        const todas = [...pendientes, ...enPreparacion, ...listas];
        this.pedidos.set(this.agruparPorPedido(todas));
        this.cargando.set(false);
        this.error.set(null);
      });
  }

  avanzarEstado(pedido: PedidoBebidaAgrupado): void {
    if (!pedido.ordenesIds.length || this.procesandoPedidoId()) {
      return;
    }

    this.procesandoPedidoId.set(pedido.pedidoId);
    this.error.set(null);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    const accion =
      pedido.estado === 'pendiente'
        ? this.ordenesApi.marcarEnPreparacion.bind(this.ordenesApi)
        : this.ordenesApi.marcarLista.bind(this.ordenesApi);

    forkJoin(pedido.ordenesIds.map((id) => accion(id)))
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
        error: (error) => {
          console.error(error);
          this.error.set(
            'No se pudo actualizar el estado del pedido de bebidas.',
          );
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  retrocederEstado(pedido: PedidoBebidaAgrupado): void {
    if (!pedido.ordenesIds.length || this.procesandoPedidoId()) {
      return;
    }

    this.procesandoPedidoId.set(pedido.pedidoId);
    this.error.set(null);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    const accion =
      pedido.estado === 'listo'
        ? this.ordenesApi.marcarEnPreparacion.bind(this.ordenesApi)
        : this.ordenesApi.marcarPendiente.bind(this.ordenesApi);

    forkJoin(pedido.ordenesIds.map((id) => accion(id)))
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
        error: (error) => {
          console.error(error);
          this.error.set(
            'No se pudo actualizar el estado del pedido de bebidas.',
          );
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  tiempoPedido(pedido: PedidoBebidaAgrupado): string {
    const fecha = new Date(pedido.fechaPedido).getTime();
    if (Number.isNaN(fecha)) {
      return '';
    }

    const mins = Math.max(0, Math.floor((Date.now() - fecha) / 60000));
    if (mins < 1) {
      return '< 1 min';
    }
    if (mins < 60) {
      return `${mins} min`;
    }

    const horas = Math.floor(mins / 60);
    const resto = mins % 60;
    return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
  }

  private pausarSincronizacion(ms: number): void {
    this.pausadoHasta.set(Date.now() + ms);
  }

  private estaSincronizacionPausada(): boolean {
    return Date.now() < this.pausadoHasta();
  }

  private recargarConRetardo(ms: number): void {
    window.setTimeout(() => {
      this.recargar();
    }, ms);
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
          this.cargando.set(false);
          this.error.set(null);
          this.procesandoPedidoId.set(null);
        },
        error: (error) => {
          console.error(error);
          this.error.set('No se pudieron recargar las bebidas.');
          this.cargando.set(false);
          this.procesandoPedidoId.set(null);
        },
      });
  }

  private agruparPorPedido(
    ordenes: OrdenCocinaResponse[],
  ): PedidoBebidaAgrupado[] {
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
        String(orden.pedido?.cuenta?.mesas?.[0]?.id ?? 'Sin asignar');

      const estado = this.mapearEstado(orden.ordenEstado);

      if (!mapa.has(pedidoId)) {
        mapa.set(pedidoId, {
          pedidoId,
          cuentaId,
          mesaId,
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

      const existente = grupo.items.find(
        (item) => item.nombre === orden.plato.nombre,
      );

      if (existente) {
        existente.cantidad += 1;
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
