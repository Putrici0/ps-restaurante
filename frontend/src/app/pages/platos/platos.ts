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

// Estados específicos para la vista de camareros/platos
type EstadoVisualPlato = 'listo' | 'entregado';

interface ItemAgrupadoPlato {
  nombre: string;
  cantidad: number;
}

interface PedidoPlatoAgrupado {
  pedidoId: string;
  cuentaId: string;
  mesaId: string;
  fechaPedido: string;
  estado: EstadoVisualPlato;
  ordenesIds: string[];
  items: ItemAgrupadoPlato[];
  totalItems: number;
}

@Component({
  selector: 'app-platos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './platos.html',
  styleUrl: './platos.css',
})
export class PlatosComponent {
  private readonly ordenesApi = inject(OrdenesApiService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly pollingMs = 2500;
  private readonly refreshAfterWriteMs = 1500;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly procesandoPedidoId = signal<string | null>(null);
  readonly pausadoHasta = signal<number>(0);
  readonly pedidos = signal<PedidoPlatoAgrupado[]>([]);

  readonly pedidosOrdenados = computed(() => {
    return [...this.pedidos()].sort((a, b) => {
      // Prioridad: primero lo que está "listo" para ser entregado
      if (a.estado === 'listo' && b.estado === 'entregado') return -1;
      if (a.estado === 'entregado' && b.estado === 'listo') return 1;

      const fechaA = new Date(a.fechaPedido).getTime();
      const fechaB = new Date(b.fechaPedido).getTime();
      return fechaA - fechaB;
    });
  });

  readonly pendientesCount = computed(
    () => this.pedidos().filter((p) => p.estado === 'listo').length,
  );

  constructor() {
    interval(this.pollingMs)
      .pipe(
        startWith(0),
        switchMap(() => {
          if (this.estaSincronizacionPausada()) return of(null);

          // Obtenemos platos listos y platos entregados (Cocina)
          return combineLatest([
            this.ordenesApi.obtenerListasCocina(),
            this.ordenesApi.obtenerEntregadasCocina(),
          ]).pipe(
            catchError((error) => {
              console.error(error);
              this.error.set('No se pudieron cargar los platos.');
              this.cargando.set(false);
              return of([[], []] as [OrdenCocinaResponse[], OrdenCocinaResponse[]]);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => {
        if (!resultado) return;
        const [listas, entregadas] = resultado;
        this.pedidos.set(
          this.agruparPorPedido(
            [...listas, ...entregadas].filter((o) => o.ordenEstado !== 'Entregado'),),
        );
        this.cargando.set(false);
        this.error.set(null);
      });
  }

  avanzarEstado(pedido: PedidoPlatoAgrupado): void {
    if (!pedido.ordenesIds.length || this.procesandoPedidoId()) return;

    this.procesandoPedidoId.set(pedido.pedidoId);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    // Si está listo, lo marcamos como Entregado
    forkJoin(pedido.ordenesIds.map((id) => this.ordenesApi.marcarEntregada(id)))
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: () => {
          this.error.set('Error al entregar el plato.');
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  retrocederEstado(pedido: PedidoPlatoAgrupado): void {
    if (!pedido.ordenesIds.length || this.procesandoPedidoId()) return;

    this.procesandoPedidoId.set(pedido.pedidoId);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    // Si estaba entregado, vuelve a estado "Listo"
    forkJoin(pedido.ordenesIds.map((id) => this.ordenesApi.marcarLista(id)))
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: () => {
          this.error.set('Error al revertir el estado.');
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  private agruparPorPedido(ordenes: OrdenCocinaResponse[]): PedidoPlatoAgrupado[] {
    // FILTRO: Solo platos (NO bebidas) y que la cuenta no esté pagada
    const visibles = ordenes.filter((orden) => {
      const esPlato = orden.plato?.categoria !== 'Bebida';
      return esPlato;
    });

    const mapa = new Map<string, PedidoPlatoAgrupado>();

    for (const orden of visibles) {
      const pedidoId = orden.pedido?.id;
      if (!pedidoId) continue;

      const mesaId = orden.pedido?.cuenta?.mesas?.[0]?.id?.toString() ?? 'S/A';
      const estado = orden.ordenEstado === 'Entregado' ? 'entregado' : 'listo';

      if (!mapa.has(pedidoId)) {
        mapa.set(pedidoId, {
          pedidoId,
          cuentaId: orden.pedido?.cuenta?.id ?? '',
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

      // Si un item del grupo está solo "listo", todo el grupo se ve como "listo"
      if (estado === 'listo') grupo.estado = 'listo';

      const itemExistente = grupo.items.find((i) => i.nombre === orden.plato.nombre);
      if (itemExistente) {
        itemExistente.cantidad += 1;
      } else {
        grupo.items.push({ nombre: orden.plato.nombre, cantidad: 1 });
      }
    }

    return Array.from(mapa.values());
  }

  // Métodos auxiliares de sincronización (igual que en bebidas)
  private pausarSincronizacion(ms: number) {
    this.pausadoHasta.set(Date.now() + ms);
  }
  private estaSincronizacionPausada() {
    return Date.now() < this.pausadoHasta();
  }
  private recargarConRetardo(ms: number) {
    setTimeout(() => this.recargar(), ms);
  }

  private recargar(): void {
    combineLatest([
      this.ordenesApi.obtenerListasCocina(),
      this.ordenesApi.obtenerEntregadasCocina(),
    ])
      .pipe(take(1))
      .subscribe(([listas, entregadas]) => {
        this.pedidos.set(
          this.agruparPorPedido(
            [...listas, ...entregadas].filter((o) => o.ordenEstado !== 'Entregado'),),
          );
        this.procesandoPedidoId.set(null);
      });
  }
}
