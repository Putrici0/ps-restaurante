import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, forkJoin, map, of, take } from 'rxjs';
import {
  OrdenCocinaResponse,
  OrdenesApiService,
} from '../../../services/ordenes-api.service';
import { Navbar } from '../../../shared/navbar/navbar';

type ResultadoCarga = {
  ok: boolean;
  data: OrdenCocinaResponse[];
};

type EstadoVisualOrden = OrdenCocinaResponse['ordenEstado'];

type TransicionOrden = {
  estadoObjetivo: EstadoVisualOrden;
  expiraEn: number;
};

@Component({
  selector: 'app-bebidas',
  standalone: true,
  imports: [CommonModule, Navbar],
  templateUrl: './bebidas.html',
  styleUrl: './bebidas.css',
})
export class Bebidas implements OnInit, OnDestroy {
  private readonly ordenesApi = inject(OrdenesApiService);

  private intervaloRefresco?: number;
  private intervaloReloj?: number;

  private readonly pollingMs = 8000;
  private readonly refreshAfterWriteMs = 1500;
  private readonly transicionVisualMs = 2500;

  readonly cargando = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly procesandoOrdenId = signal<string | null>(null);
  readonly pausadoHasta = signal(0);
  readonly ahora = signal(Date.now());

  readonly ordenes = signal<OrdenCocinaResponse[]>([]);
  readonly transiciones = signal<Record<string, TransicionOrden>>({});

  readonly ordenesOrdenadas = computed(() => {
    const prioridad: Record<string, number> = {
      Pendiente: 1,
      Preparación: 2,
      Listo: 3,
    };

    return [...this.ordenes()].sort((a, b) => {
      const estadoA = this.estadoVisual(a);
      const estadoB = this.estadoVisual(b);

      const porEstado = (prioridad[estadoA] ?? 99) - (prioridad[estadoB] ?? 99);
      if (porEstado !== 0) return porEstado;

      const fechaA = new Date(a.pedido?.fechaPedido ?? a.fecha).getTime();
      const fechaB = new Date(b.pedido?.fechaPedido ?? b.fecha).getTime();
      return fechaA - fechaB;
    });
  });

  readonly pendientesCount = computed(
    () => this.ordenes().filter((o) => this.estadoVisual(o) !== 'Listo').length,
  );

  readonly listasCount = computed(
    () => this.ordenes().filter((o) => this.estadoVisual(o) === 'Listo').length,
  );

  readonly hayDatos = computed(() => this.ordenesOrdenadas().length > 0);

  ngOnInit(): void {
    this.cargarBebidas(true);

    this.intervaloRefresco = window.setInterval(() => {
      this.limpiarTransicionesExpiradas();

      if (!this.estaSincronizacionPausada()) {
        this.cargarBebidas(false);
      }
    }, this.pollingMs);

    this.intervaloReloj = window.setInterval(() => {
      this.ahora.set(Date.now());
      this.limpiarTransicionesExpiradas();
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

  avanzarEstado(orden: OrdenCocinaResponse): void {
    if (this.procesandoOrdenId()) return;

    const estadoActual = this.estadoVisual(orden);
    if (estadoActual === 'Listo') return;

    const estadoObjetivo: EstadoVisualOrden =
      estadoActual === 'Pendiente' ? 'Preparación' : 'Listo';

    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);
    this.marcarTransicion(orden.id, estadoObjetivo);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    const accion =
      estadoActual === 'Pendiente'
        ? this.ordenesApi.marcarEnPreparacion(orden.id)
        : this.ordenesApi.marcarLista(orden.id);

    accion.pipe(take(1)).subscribe({
      next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
      error: (err) => {
        console.error(err);
        this.error.set('No se pudo actualizar la bebida.');
        this.limpiarTransicion(orden.id);
        this.procesandoOrdenId.set(null);
        this.recargarConRetardo(this.refreshAfterWriteMs);
      },
    });
  }

  retrocederEstado(orden: OrdenCocinaResponse): void {
    if (this.procesandoOrdenId()) return;

    const estadoActual = this.estadoVisual(orden);
    if (estadoActual === 'Pendiente') return;

    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);
    this.marcarTransicion(orden.id, 'Pendiente');
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    this.ordenesApi
      .marcarPendiente(orden.id)
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: (err) => {
          console.error(err);
          this.error.set('No se pudo actualizar la bebida.');
          this.limpiarTransicion(orden.id);
          this.procesandoOrdenId.set(null);
          this.recargarConRetardo(this.refreshAfterWriteMs);
        },
      });
  }

  mesaDeOrden(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];
    if (mesas.length === 1) return `Mesa ${mesas[0].id}`;
    if (mesas.length > 1) return `Mesas ${mesas.map((m) => m.id).join(', ')}`;

    const mesaDetalles = this.extraerMesaDesdeTexto(orden.detalles);
    return mesaDetalles ? `Mesa ${mesaDetalles}` : 'Mesa sin asignar';
  }

  numeroMesaPlano(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];
    if (mesas.length > 0) return mesas.map((m) => String(m.id)).join(', ');

    return this.extraerMesaDesdeTexto(orden.detalles) ?? 'Sin mesa';
  }

  estadoVisual(orden: OrdenCocinaResponse): EstadoVisualOrden {
    return this.transiciones()[orden.id]?.estadoObjetivo ?? orden.ordenEstado;
  }

  estaCambiando(orden: OrdenCocinaResponse): boolean {
    return this.procesandoOrdenId() === orden.id;
  }

  etiquetaEstado(orden: OrdenCocinaResponse): string {
    const estado = this.estadoVisual(orden);

    switch (estado) {
      case 'Pendiente':
        return this.estaCambiando(orden) ? 'CAMBIANDO...' : 'PENDIENTE';
      case 'Preparación':
        return this.estaCambiando(orden) ? 'CAMBIANDO...' : 'PREPARANDO';
      case 'Listo':
        return this.estaCambiando(orden) ? 'CAMBIANDO...' : 'LISTO';
      default:
        return estado.toUpperCase();
    }
  }

  tiempoTexto(orden: OrdenCocinaResponse): string {
    const referencia = orden.pedido?.fechaPedido ?? orden.fecha;
    const diffMin = this.diferenciaEnMinutos(referencia);

    if (diffMin < 1) return '< 1 min';
    if (diffMin < 60) return `${diffMin} min`;

    const horas = Math.floor(diffMin / 60);
    const minutosRestantes = diffMin % 60;

    return minutosRestantes === 0
      ? `${horas} h`
      : `${horas} h ${minutosRestantes} min`;
  }

  accionPrincipalTexto(orden: OrdenCocinaResponse): string {
    if (this.estaCambiando(orden)) {
      const estado = this.estadoVisual(orden);

      if (estado === 'Preparación') return 'PREPARANDO...';
      if (estado === 'Listo') return 'MARCANDO LISTO...';
      return 'CAMBIANDO...';
    }

    switch (this.estadoVisual(orden)) {
      case 'Pendiente':
        return 'PASAR A PREPARACIÓN';
      case 'Preparación':
        return 'MARCAR COMO LISTO';
      case 'Listo':
        return 'LISTO';
      default:
        return 'ACTUALIZAR';
    }
  }

  accionSecundariaTexto(orden: OrdenCocinaResponse): string {
    if (this.estaCambiando(orden)) {
      return 'CAMBIANDO...';
    }

    return this.estadoVisual(orden) === 'Pendiente'
      ? 'PENDIENTE'
      : 'VOLVER A PENDIENTE';
  }

  detallesVisibles(orden: OrdenCocinaResponse): string {
    const detalles = orden.detalles?.trim();
    return detalles && detalles.length > 0 ? detalles : 'Sin detalles';
  }

  private cargarBebidas(mostrarLoading: boolean): void {
    if (this.estaSincronizacionPausada()) return;

    if (mostrarLoading) {
      this.cargando.set(true);
    }

    const cargaSegura = (obs: ReturnType<OrdenesApiService['obtenerPendientesBarra']>) =>
      obs.pipe(
        map((data) => ({ ok: true, data }) as ResultadoCarga),
        catchError((err) => {
          console.error(err);
          return of({ ok: false, data: [] } as ResultadoCarga);
        }),
      );

    forkJoin({
      pendientes: cargaSegura(this.ordenesApi.obtenerPendientesBarra()),
      preparacion: cargaSegura(this.ordenesApi.obtenerEnPreparacionBarra()),
      listas: cargaSegura(this.ordenesApi.obtenerListasBarra()),
    })
      .pipe(take(1))
      .subscribe({
        next: ({ pendientes, preparacion, listas }) => {
          const combinadas = [
            ...pendientes.data,
            ...preparacion.data,
            ...listas.data,
          ];

          const visibles = this.filtrarVisibles(combinadas);
          const reconciliadas = visibles.map((orden) => this.reconciliarTransicion(orden));

          this.ordenes.set(reconciliadas);

          const peticionesOk = [pendientes.ok, preparacion.ok, listas.ok].filter(Boolean).length;

          if (peticionesOk === 0 && reconciliadas.length === 0) {
            this.error.set('No se pudieron cargar las bebidas.');
          } else {
            this.error.set(null);
          }

          this.cargando.set(false);

          if (this.procesandoOrdenId()) {
            const sigueEnLista = reconciliadas.some((o) => o.id === this.procesandoOrdenId());
            if (!sigueEnLista || !this.transiciones()[this.procesandoOrdenId()!]) {
              this.procesandoOrdenId.set(null);
            }
          }
        },
        error: (err) => {
          console.error(err);
          this.error.set('No se pudieron cargar las bebidas.');
          this.cargando.set(false);
          this.procesandoOrdenId.set(null);
        },
      });
  }

  private filtrarVisibles(ordenes: OrdenCocinaResponse[]): OrdenCocinaResponse[] {
    return ordenes.filter((orden) => {
      const esBebida = orden.plato?.categoria === 'Bebida';
      const estadoVisible =
        orden.ordenEstado === 'Pendiente' ||
        orden.ordenEstado === 'Preparación' ||
        orden.ordenEstado === 'Listo';
      const cuentaPagada = orden.pedido?.cuenta?.payed === true;

      return esBebida && estadoVisible && !cuentaPagada;
    });
  }

  private reconciliarTransicion(orden: OrdenCocinaResponse): OrdenCocinaResponse {
    const transicion = this.transiciones()[orden.id];
    if (!transicion) return orden;

    if (orden.ordenEstado === transicion.estadoObjetivo) {
      this.limpiarTransicion(orden.id);
      if (this.procesandoOrdenId() === orden.id) {
        this.procesandoOrdenId.set(null);
      }
      return orden;
    }

    return {
      ...orden,
      ordenEstado: transicion.estadoObjetivo,
    };
  }

  private marcarTransicion(ordenId: string, estadoObjetivo: EstadoVisualOrden): void {
    this.transiciones.update((actual) => ({
      ...actual,
      [ordenId]: {
        estadoObjetivo,
        expiraEn: Date.now() + this.transicionVisualMs,
      },
    }));
  }

  private limpiarTransicion(ordenId: string): void {
    this.transiciones.update((actual) => {
      const copia = { ...actual };
      delete copia[ordenId];
      return copia;
    });
  }

  private limpiarTransicionesExpiradas(): void {
    const ahora = Date.now();

    this.transiciones.update((actual) => {
      const nuevas = Object.fromEntries(
        Object.entries(actual).filter(([, valor]) => valor.expiraEn > ahora),
      ) as Record<string, TransicionOrden>;

      return nuevas;
    });

    const procesandoId = this.procesandoOrdenId();
    if (procesandoId && !this.transiciones()[procesandoId]) {
      this.procesandoOrdenId.set(null);
    }
  }

  private diferenciaEnMinutos(fechaIso: string): number {
    const fecha = new Date(fechaIso).getTime();
    if (Number.isNaN(fecha)) return 0;

    return Math.max(0, Math.floor((this.ahora() - fecha) / 60000));
  }

  private extraerMesaDesdeTexto(texto?: string | null): string | null {
    if (!texto) return null;
    const match = texto.match(/\bmesa\s*[:#-]?\s*(\d+)\b/i);
    return match?.[1] ?? null;
  }

  private pausarSincronizacion(ms: number): void {
    this.pausadoHasta.set(Date.now() + ms);
  }

  private estaSincronizacionPausada(): boolean {
    return Date.now() < this.pausadoHasta();
  }

  private recargarConRetardo(ms: number): void {
    window.setTimeout(() => this.cargarBebidas(false), ms);
  }
}
