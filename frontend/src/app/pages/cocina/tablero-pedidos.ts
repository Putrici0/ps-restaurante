import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, forkJoin, map, of, take } from 'rxjs';
import {
  CategoriaPlatoBackend,
  OrdenCocinaResponse,
  OrdenesApiService,
} from '../../services/ordenes-api.service';

type ResultadoCarga = {
  ok: boolean;
  data: OrdenCocinaResponse[];
};

@Component({
  selector: 'app-tablero-pedidos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tablero-pedidos.component.html',
  styleUrls: ['./tablero-pedidos.component.css'],
})
export class TableroPedidos implements OnInit, OnDestroy {
  private readonly ordenesApiService = inject(OrdenesApiService);

  private intervaloRefresco?: number;
  private intervaloReloj?: number;

  private readonly pollingMs = 8000;
  private readonly refreshAfterWriteMs = 1500;

  readonly cargando = signal<boolean>(true);
  readonly actualizando = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly ordenesPendientes = signal<OrdenCocinaResponse[]>([]);
  readonly ordenesPreparacion = signal<OrdenCocinaResponse[]>([]);
  readonly ordenesListas = signal<OrdenCocinaResponse[]>([]);

  readonly ahora = signal(Date.now());
  readonly ordenDetalleAbiertaId = signal<string | null>(null);
  readonly pausadoHasta = signal(0);

  readonly totalOrdenes = computed(
    () =>
      this.ordenesPendientes().length +
      this.ordenesPreparacion().length +
      this.ordenesListas().length,
  );

  ngOnInit(): void {
    this.cargarTablero(true);

    this.intervaloRefresco = window.setInterval(() => {
      if (!this.estaSincronizacionPausada()) {
        this.cargarTablero(false);
      }
    }, this.pollingMs);

    this.intervaloReloj = window.setInterval(() => {
      this.ahora.set(Date.now());
    }, 30000);
  }

  ngOnDestroy(): void {
    if (this.intervaloRefresco) {
      window.clearInterval(this.intervaloRefresco);
    }
    if (this.intervaloReloj) {
      window.clearInterval(this.intervaloReloj);
    }
  }

  recargar(): void {
    this.cargarTablero(true);
  }

  toggleDetalle(ordenId: string): void {
    if (this.ordenDetalleAbiertaId() === ordenId) {
      this.ordenDetalleAbiertaId.set(null);
      return;
    }
    this.ordenDetalleAbiertaId.set(ordenId);
  }

  detalleAbierto(ordenId: string): boolean {
    return this.ordenDetalleAbiertaId() === ordenId;
  }

  pasarAPreparacion(ordenId: string): void {
    if (this.actualizando()) return;

    const orden = this.ordenesPendientes().find((o) => o.id === ordenId);
    if (!orden) return;

    this.actualizando.set(true);
    this.error.set(null);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    this.ordenesApiService
      .marcarEnPreparacion(ordenId)
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: (err) => {
          console.error(err);
          this.error.set('No se ha podido actualizar el estado de la orden.');
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  pasarAPendiente(ordenId: string): void {
    if (this.actualizando()) return;

    const orden =
      this.ordenesPreparacion().find((o) => o.id === ordenId) ??
      this.ordenesListas().find((o) => o.id === ordenId);

    if (!orden) return;

    this.actualizando.set(true);
    this.error.set(null);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    this.ordenesApiService
      .marcarPendiente(ordenId)
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: (err) => {
          console.error(err);
          this.error.set('No se ha podido actualizar el estado de la orden.');
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  pasarALista(ordenId: string): void {
    if (this.actualizando()) return;

    const orden = this.ordenesPreparacion().find((o) => o.id === ordenId);
    if (!orden) return;

    this.actualizando.set(true);
    this.error.set(null);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    this.ordenesApiService
      .marcarLista(ordenId)
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: (err) => {
          console.error(err);
          this.error.set('No se ha podido actualizar el estado de la orden.');
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  mesaDeOrden(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];

    if (mesas.length > 0) {
      if (mesas.length === 1) {
        return `Mesa ${mesas[0].id}`;
      }
      return `Mesas ${mesas.map((mesa) => mesa.id).join(', ')}`;
    }

    const mesaDesdeDetalles = this.extraerMesaDesdeTexto(orden.detalles);
    if (mesaDesdeDetalles) {
      return `Mesa ${mesaDesdeDetalles}`;
    }

    return 'Mesa sin asignar';
  }

  origenMesa(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];
    if (mesas.length > 0) return 'pedido.cuenta.mesas';

    const mesaDesdeDetalles = this.extraerMesaDesdeTexto(orden.detalles);
    if (mesaDesdeDetalles) return 'detalles';

    return 'no enviada por la API';
  }

  numeroMesaPlano(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];
    if (mesas.length > 0) {
      return mesas.map((mesa) => String(mesa.id)).join(', ');
    }

    const mesaDesdeDetalles = this.extraerMesaDesdeTexto(orden.detalles);
    if (mesaDesdeDetalles) {
      return mesaDesdeDetalles;
    }

    return 'Sin mesa';
  }

  cuentaIdDeOrden(orden: OrdenCocinaResponse): string {
    return orden.pedido?.cuenta?.id ?? 'Sin cuenta';
  }

  pedidoIdDeOrden(orden: OrdenCocinaResponse): string {
    return orden.pedido?.id ?? 'Sin pedido';
  }

  categoriaCorta(categoria: CategoriaPlatoBackend): string {
    switch (categoria) {
      case 'Entrante':
        return 'ENT';
      case 'Principal':
        return 'PPL';
      case 'Postre':
        return 'POS';
      case 'Bebida':
        return 'BEB';
      default:
        return categoria;
    }
  }

  tiempoTranscurrido(fechaIso: string): string {
    const diffMin = this.diferenciaEnMinutos(fechaIso);

    if (diffMin < 1) return '< 1 min';
    if (diffMin < 60) return `${diffMin} min`;

    const horas = Math.floor(diffMin / 60);
    const minutosRestantes = diffMin % 60;

    if (minutosRestantes === 0) return `${horas} h`;
    return `${horas} h ${minutosRestantes} min`;
  }

  tiempoTextoTarjeta(orden: OrdenCocinaResponse): string {
    const transcurrido = this.tiempoTranscurrido(orden.fecha);

    switch (orden.ordenEstado) {
      case 'Pendiente':
        return `En cola · ${transcurrido}`;
      case 'Preparación':
        return `Preparando · ${transcurrido}`;
      case 'Listo':
        return `Lista · ${transcurrido}`;
      default:
        return transcurrido;
    }
  }

  etaTexto(orden: OrdenCocinaResponse): string {
    const estimado = this.tiempoObjetivoMinutos(orden);
    const transcurrido = this.diferenciaEnMinutos(orden.fecha);

    if (orden.ordenEstado === 'Listo') {
      return `Lista desde hace ${this.tiempoTranscurrido(orden.fecha)}`;
    }

    const restantes = estimado - transcurrido;

    if (restantes > 0) return `ETA ${restantes} min`;
    if (restantes === 0) return 'ETA 0 min';
    return `Retraso ${Math.abs(restantes)} min`;
  }

  etaClase(orden: OrdenCocinaResponse): string {
    const estimado = this.tiempoObjetivoMinutos(orden);
    const transcurrido = this.diferenciaEnMinutos(orden.fecha);
    const restantes = estimado - transcurrido;

    if (orden.ordenEstado === 'Listo') return 'eta eta--ready';
    if (restantes <= 0) return 'eta eta--late';
    if (restantes <= 3) return 'eta eta--warning';
    return 'eta eta--ok';
  }

  fechaLarga(fechaIso?: string | null): string {
    if (!fechaIso) return 'Sin fecha';

    const fecha = new Date(fechaIso);
    if (Number.isNaN(fecha.getTime())) return fechaIso;

    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'short',
      timeStyle: 'medium',
    }).format(fecha);
  }

  detalleTecnico(orden: OrdenCocinaResponse): string {
    const detalles = orden.detalles?.trim();
    return detalles && detalles.length > 0 ? detalles : 'Sin detalles';
  }

  private pausarSincronizacion(ms: number): void {
    this.pausadoHasta.set(Date.now() + ms);
  }

  private estaSincronizacionPausada(): boolean {
    return Date.now() < this.pausadoHasta();
  }

  private recargarConRetardo(ms: number): void {
    window.setTimeout(() => {
      this.cargarTablero(false);
    }, ms);
  }

  private diferenciaEnMinutos(fechaIso: string): number {
    const fecha = new Date(fechaIso).getTime();
    if (Number.isNaN(fecha)) return 0;

    return Math.max(0, Math.floor((this.ahora() - fecha) / 60000));
  }

  private tiempoObjetivoMinutos(orden: OrdenCocinaResponse): number {
    switch (orden.plato.categoria) {
      case 'Entrante':
        return 10;
      case 'Principal':
        return 18;
      case 'Postre':
        return 8;
      case 'Bebida':
        return 4;
      default:
        return 12;
    }
  }

  private extraerMesaDesdeTexto(texto?: string | null): string | null {
    if (!texto) return null;
    const match = texto.match(/\bmesa\s*[:#-]?\s*(\d+)\b/i);
    return match?.[1] ?? null;
  }

  private filtrarNoPagadas(ordenes: OrdenCocinaResponse[]): OrdenCocinaResponse[] {
    return ordenes;
  }

  private cargarTablero(mostrarLoading: boolean): void {
    if (this.estaSincronizacionPausada()) return;

    if (mostrarLoading) {
      this.cargando.set(true);
    }

    const cargaSegura = (obs: ReturnType<OrdenesApiService['obtenerPendientesCocina']>) =>
      obs.pipe(
        map((data) => ({ ok: true, data }) as ResultadoCarga),
        catchError((err) => {
          console.error(err);
          return of({ ok: false, data: [] } as ResultadoCarga);
        }),
      );

    forkJoin({
      pendientes: cargaSegura(this.ordenesApiService.obtenerPendientesCocina()),
      preparacion: cargaSegura(this.ordenesApiService.obtenerEnPreparacionCocina()),
      listas: cargaSegura(this.ordenesApiService.obtenerListasCocina()),
    })
      .pipe(take(1))
      .subscribe({
        next: ({ pendientes, preparacion, listas }) => {
          const pendientesFiltradas = pendientes.data;
          const preparacionFiltradas = preparacion.data;
          const listasFiltradas = listas.data;

          this.ordenesPendientes.set(pendientesFiltradas);
          this.ordenesPreparacion.set(preparacionFiltradas);
          this.ordenesListas.set(listasFiltradas);

          const peticionesOk = [pendientes.ok, preparacion.ok, listas.ok].filter(Boolean).length;
          const total = pendientesFiltradas.length + preparacionFiltradas.length + listasFiltradas.length;

          if (peticionesOk === 0 && total === 0) {
            this.error.set('No se ha podido cargar el tablero de cocina.');
          } else {
            this.error.set(null);
          }

          this.cargando.set(false);
          this.actualizando.set(false);

          const abierta = this.ordenDetalleAbiertaId();
          if (abierta) {
            const sigueExistiendo = [
              ...pendientesFiltradas,
              ...preparacionFiltradas,
              ...listasFiltradas,
            ].some((orden) => orden.id === abierta);

            if (!sigueExistiendo) {
              this.ordenDetalleAbiertaId.set(null);
            }
          }
        },
        error: (err) => {
          console.error(err);
          this.error.set('No se ha podido cargar el tablero de cocina.');
          this.cargando.set(false);
          this.actualizando.set(false);
        },
      });
  }
}
