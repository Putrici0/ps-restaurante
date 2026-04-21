import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, of, take } from 'rxjs';
import {
  CategoriaPlatoBackend,
  OrdenCocinaResponse,
  OrdenesApiService,
} from '../../services/ordenes-api.service';
import { Navbar } from '../../components/navbar/navbar';

type EstadoVisualOrden = OrdenCocinaResponse['ordenEstado'];

type TransicionOrden = {
  estadoObjetivo: EstadoVisualOrden;
  expiraEn: number;
};

@Component({
  selector: 'app-platos',
  standalone: true,
  imports: [CommonModule, Navbar],
  templateUrl: './platos.html',
  styleUrl: './platos.css',
})
export class PlatosComponent implements OnInit, OnDestroy {
  private readonly ordenesApi = inject(OrdenesApiService);

  private intervaloRefresco?: number;
  private intervaloReloj?: number;

  private readonly pollingMs = 3000;
  private readonly refreshAfterWriteMs = 1500;
  private readonly transicionVisualMs = 2500;

  readonly cargando = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly procesandoOrdenId = signal<string | null>(null);
  readonly pausadoHasta = signal(0);
  readonly ahora = signal(Date.now());

  readonly ordenes = signal<OrdenCocinaResponse[]>([]);
  readonly transiciones = signal<Record<string, TransicionOrden>>({});

  readonly ordenesOrdenadas = computed(() =>
    [...this.ordenes()].sort((a, b) => {
      const fechaA = new Date(a.pedido?.fechaPedido ?? a.fecha).getTime();
      const fechaB = new Date(b.pedido?.fechaPedido ?? b.fecha).getTime();
      return fechaA - fechaB;
    }),
  );

  readonly pendientesDeEntregaCount = computed(
    () => this.ordenes().filter((orden) => this.estadoVisual(orden) === 'Listo').length,
  );

  readonly hayDatos = computed(() => this.ordenesOrdenadas().length > 0);

  ngOnInit(): void {
    this.cargarPlatos(true);

    this.intervaloRefresco = window.setInterval(() => {
      this.limpiarTransicionesExpiradas();

      if (!this.estaSincronizacionPausada()) {
        this.cargarPlatos(false);
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

  entregarOrden(orden: OrdenCocinaResponse): void {
    const estadoActual = this.estadoVisual(orden);

    if (this.procesandoOrdenId() || estadoActual === 'Entregado') {
      return;
    }

    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);
    this.marcarTransicion(orden.id, 'Entregado');
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    this.ordenesApi
      .marcarEntregada(orden.id)
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: (err) => {
          console.error(err);
          this.error.set('No se pudo marcar la orden como entregada.');
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

  estadoVisual(orden: OrdenCocinaResponse): EstadoVisualOrden {
    return this.transiciones()[orden.id]?.estadoObjetivo ?? orden.ordenEstado;
  }

  estaCambiando(orden: OrdenCocinaResponse): boolean {
    return this.procesandoOrdenId() === orden.id;
  }

  etiquetaEstado(orden: OrdenCocinaResponse): string {
    const estado = this.estadoVisual(orden);

    if (this.estaCambiando(orden)) {
      return 'CAMBIANDO...';
    }

    return estado === 'Entregado' ? 'ENTREGADO' : 'LISTO';
  }

  detallesVisibles(orden: OrdenCocinaResponse): string {
    const detalles = orden.detalles?.trim();
    return detalles && detalles.length > 0 ? detalles : 'Sin detalles adicionales';
  }

  tiempoLista(orden: OrdenCocinaResponse): string {
    const referencia = orden.pedido?.fechaPedido ?? orden.fecha;
    const diffMin = this.diferenciaEnMinutos(referencia);

    if (this.estadoVisual(orden) === 'Entregado') {
      if (diffMin < 1) return 'Entregado hace < 1 min';
      if (diffMin < 60) return `Entregado hace ${diffMin} min`;

      const horas = Math.floor(diffMin / 60);
      const minutosRestantes = diffMin % 60;

      return minutosRestantes === 0
        ? `Entregado hace ${horas} h`
        : `Entregado hace ${horas} h ${minutosRestantes} min`;
    }

    if (diffMin < 1) return 'Lista hace < 1 min';
    if (diffMin < 60) return `Lista hace ${diffMin} min`;

    const horas = Math.floor(diffMin / 60);
    const minutosRestantes = diffMin % 60;

    return minutosRestantes === 0
      ? `Lista hace ${horas} h`
      : `Lista hace ${horas} h ${minutosRestantes} min`;
  }

  pedidoIdDeOrden(orden: OrdenCocinaResponse): string {
    return orden.pedido?.id ?? 'Sin pedido';
  }

  yaEntregada(orden: OrdenCocinaResponse): boolean {
    return this.estadoVisual(orden) === 'Entregado';
  }

  textoBotonEntrega(orden: OrdenCocinaResponse): string {
    if (this.estaCambiando(orden)) return 'ENTREGANDO...';
    if (this.yaEntregada(orden)) return 'ENTREGADO';
    return 'MARCAR COMO ENTREGADO';
  }

  private cargarPlatos(mostrarLoading: boolean): void {
    if (this.estaSincronizacionPausada()) return;

    if (mostrarLoading) {
      this.cargando.set(true);
    }

    this.ordenesApi
      .obtenerPlatosSala()
      .pipe(
        take(1),
        catchError((err) => {
          console.error(err);
          this.error.set('No se pudieron cargar los platos de sala.');
          this.cargando.set(false);
          this.procesandoOrdenId.set(null);
          return of([] as OrdenCocinaResponse[]);
        }),
      )
      .subscribe((ordenes) => {
        const visibles = this.filtrarVisibles(ordenes);
        const reconciliadas = visibles.map((orden) => this.reconciliarTransicion(orden));

        this.ordenes.set(reconciliadas);
        this.cargando.set(false);

        if (this.procesandoOrdenId()) {
          const sigueEnLista = reconciliadas.some((o) => o.id === this.procesandoOrdenId());
          if (!sigueEnLista || !this.transiciones()[this.procesandoOrdenId()!]) {
            this.procesandoOrdenId.set(null);
          }
        }

        if (reconciliadas.length > 0 || !this.error()) {
          this.error.set(null);
        }
      });
  }

  private filtrarVisibles(ordenes: OrdenCocinaResponse[]): OrdenCocinaResponse[] {
    return ordenes.filter((orden) => {
      const esComida = orden.plato?.categoria !== 'Bebida';
      const estadoVisible =
        orden.ordenEstado === 'Listo' || orden.ordenEstado === 'Entregado';
      const cuentaPagada = this.estaPagada(orden);

      return esComida && estadoVisible && !cuentaPagada;
    });
  }

  private estaPagada(orden: OrdenCocinaResponse): boolean {
    const cuenta = orden.pedido?.cuenta as
      | ({ payed?: boolean; paid?: boolean } | undefined);

    return cuenta?.payed === true || cuenta?.paid === true;
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

  private pausarSincronizacion(ms: number): void {
    this.pausadoHasta.set(Date.now() + ms);
  }

  private estaSincronizacionPausada(): boolean {
    return Date.now() < this.pausadoHasta();
  }

  private recargarConRetardo(ms: number): void {
    window.setTimeout(() => this.cargarPlatos(false), ms);
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
}
