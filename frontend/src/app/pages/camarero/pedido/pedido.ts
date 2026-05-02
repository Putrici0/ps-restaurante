import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin, take } from 'rxjs';
import { CamareroHeader } from '../camarero-header/camarero-header';
import {
  CuentaActivaResponse,
  CuentaApiService,
  EstadoCuentaResponse,
  OrdenCuentaResponse,
} from '../../../services/cuenta-api.service';

interface ItemCuentaAgrupado {
  platoId: string;
  nombre: string;
  categoria: string;
  descripcion: string;
  imagen: string;
  precioUnitario: number;
  cantidad: number;
  estados: string[];
  subtotal: number;
  ordenesIds: string[];
  pagado: boolean;
}

@Component({
  selector: 'app-camarero-pedido',
  standalone: true,
  imports: [CommonModule, CamareroHeader],
  templateUrl: './pedido.html',
  styleUrl: './pedido.css',
})
export class PedidoCamarero implements OnInit, OnDestroy {
  private readonly cuentaApiService = inject(CuentaApiService);
  private readonly route = inject(ActivatedRoute);

  private intervaloRefresco?: number;
  private readonly mesaId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly cuentaActiva = signal<CuentaActivaResponse | null>(null);
  readonly ordenes = signal<OrdenCuentaResponse[]>([]);
  readonly importePendiente = signal(0);
  readonly estadoSaldada = signal<EstadoCuentaResponse | null>(null);

  readonly cuentaCerrada = computed(() => {
    return !!this.cuentaActiva()?.payed || !!this.estadoSaldada()?.saldada;
  });

  readonly sinCuentaActiva = computed(() => {
    return !this.cargando() && this.cuentaActiva() === null;
  });

  readonly itemsPendientes = computed(() => {
    const mapa = new Map<string, ItemCuentaAgrupado>();

    for (const orden of this.ordenes()) {
      if (orden.pagada || orden.ordenEstado === 'Cancelado') {
        continue;
      }

      const plato = orden.plato;
      const key = plato.id;

      if (!mapa.has(key)) {
        mapa.set(key, {
          platoId: plato.id,
          nombre: plato.nombre,
          categoria: plato.categoria,
          descripcion: plato.descripcion,
          imagen: plato.imagen,
          precioUnitario: Number(orden.precio),
          cantidad: 0,
          estados: [],
          subtotal: 0,
          ordenesIds: [],
          pagado: false,
        });
      }

      const item = mapa.get(key)!;
      item.cantidad += 1;
      item.estados.push(orden.ordenEstado);
      item.subtotal += Number(orden.precio);

      if (orden.id) {
        item.ordenesIds.push(orden.id);
      }
    }

    return Array.from(mapa.values()).sort((a, b) =>
      a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
    );
  });

  readonly itemsPagados = computed(() => {
    const mapa = new Map<string, ItemCuentaAgrupado>();

    for (const orden of this.ordenes()) {
      if (!orden.pagada || orden.ordenEstado === 'Cancelado') {
        continue;
      }

      const plato = orden.plato;
      const key = plato.id;

      if (!mapa.has(key)) {
        mapa.set(key, {
          platoId: plato.id,
          nombre: plato.nombre,
          categoria: plato.categoria,
          descripcion: plato.descripcion,
          imagen: plato.imagen,
          precioUnitario: Number(orden.precio),
          cantidad: 0,
          estados: [],
          subtotal: 0,
          ordenesIds: [],
          pagado: true,
        });
      }

      const item = mapa.get(key)!;
      item.cantidad += 1;
      item.estados.push(orden.ordenEstado);
      item.subtotal += Number(orden.precio);

      if (orden.id) {
        item.ordenesIds.push(orden.id);
      }
    }

    return Array.from(mapa.values()).sort((a, b) =>
      a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
    );
  });

  readonly itemsAgrupados = computed(() => [
    ...this.itemsPendientes(),
    ...this.itemsPagados(),
  ]);

  readonly totalItems = computed(() =>
    this.itemsAgrupados().reduce((acc, item) => acc + item.cantidad, 0),
  );

  ngOnInit(): void {
    this.cargarCuentaCompleta(true);

    this.intervaloRefresco = window.setInterval(() => {
      this.cargarCuentaCompleta(false);
    }, 4000);
  }

  ngOnDestroy(): void {
    if (this.intervaloRefresco) {
      window.clearInterval(this.intervaloRefresco);
    }
  }

  recargar(): void {
    this.cargarCuentaCompleta(true);
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src =
      'https://images.unsplash.com/photo-1544025162-d76694265947?q=80&w=1200&auto=format&fit=crop';
  }

  obtenerResumenEstado(item: ItemCuentaAgrupado): string {
    const estadosNormalizados = item.estados.map((estado) => {
      if (estado === 'Preparación' || estado === 'Preparacion') {
        return 'En preparación';
      }
      return estado;
    });

    const unicos = Array.from(new Set(estadosNormalizados));
    return unicos.join(' · ');
  }

  hayOrdenUrgente(item: ItemCuentaAgrupado): boolean {
    const ids = new Set(item.ordenesIds);
    return this.ordenes().some((orden) => ids.has(orden.id) && !!orden.urgente);
  }

  toggleUrgenteItem(item: ItemCuentaAgrupado): void {
    const cuentaId = this.cuentaActiva()?.id;
    if (!cuentaId || item.pagado || item.ordenesIds.length === 0) {
      return;
    }

    const marcarComoUrgente = !this.hayOrdenUrgente(item);

    const llamadas = item.ordenesIds.map((ordenId) =>
      marcarComoUrgente
        ? this.cuentaApiService.marcarOrdenUrgente(ordenId)
        : this.cuentaApiService.desmarcarOrdenUrgente(ordenId),
    );

    forkJoin(llamadas)
      .pipe(take(1))
      .subscribe({
        next: () => this.cargarCuentaCompleta(false),
        error: () => {
          this.error.set('No se ha podido actualizar la urgencia de la orden.');
        },
      });
  }

  private cargarCuentaCompleta(mostrarLoading: boolean): void {
    if (!this.mesaId) {
      this.error.set('No se ha podido identificar la mesa.');
      this.cargando.set(false);
      return;
    }

    if (mostrarLoading) {
      this.cargando.set(true);
      this.error.set(null);
    }

    this.cuentaApiService
      .obtenerCuentaActivaDeMesa(this.mesaId)
      .pipe(take(1))
      .subscribe({
        next: (cuenta) => {
          this.cuentaActiva.set(cuenta);

          if (!cuenta) {
            this.ordenes.set([]);
            this.importePendiente.set(0);
            this.estadoSaldada.set(null);
            this.cargando.set(false);
            return;
          }

          forkJoin({
            ordenes: this.cuentaApiService.obtenerOrdenesDeCuenta(cuenta.id),
            pendiente: this.cuentaApiService.obtenerPendienteCuenta(cuenta.id),
            saldada: this.cuentaApiService.obtenerEstadoSaldada(cuenta.id),
          })
            .pipe(take(1))
            .subscribe({
              next: ({ ordenes, pendiente, saldada }) => {
                this.ordenes.set(ordenes);
                this.importePendiente.set(Number(pendiente.importe));
                this.estadoSaldada.set(saldada);
                this.cargando.set(false);
              },
              error: () => {
                this.error.set('No se ha podido cargar la cuenta en este momento.');
                this.cargando.set(false);
              },
            });
        },
        error: () => {
          this.error.set('No se ha podido cargar la cuenta en este momento.');
          this.cargando.set(false);
        },
      });
  }
}
