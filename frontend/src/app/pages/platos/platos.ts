import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, of, take } from 'rxjs';
import {
  CategoriaPlatoBackend,
  OrdenCocinaResponse,
  OrdenesApiService,
} from '../../services/ordenes-api.service';

@Component({
  selector: 'app-platos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './platos.html',
  styleUrl: './platos.css',
})
export class PlatosComponent implements OnInit, OnDestroy {
  private readonly ordenesApi = inject(OrdenesApiService);

  private intervaloRefresco?: number;
  private intervaloReloj?: number;

  private readonly pollingMs = 3000;
  private readonly refreshAfterWriteMs = 1500;

  readonly cargando = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly procesandoOrdenId = signal<string | null>(null);
  readonly pausadoHasta = signal(0);
  readonly ahora = signal(Date.now());

  readonly ordenes = signal<OrdenCocinaResponse[]>([]);

  readonly ordenesOrdenadas = computed(() =>
    [...this.ordenes()].sort((a, b) => {
      const fechaA = new Date(a.pedido?.fechaPedido ?? a.fecha).getTime();
      const fechaB = new Date(b.pedido?.fechaPedido ?? b.fecha).getTime();
      return fechaA - fechaB;
    }),
  );

  readonly pendientesDeEntregaCount = computed(
    () => this.ordenes().filter((orden) => orden.ordenEstado === 'Listo').length,
  );

  readonly hayDatos = computed(() => this.ordenesOrdenadas().length > 0);

  ngOnInit(): void {
    this.cargarPlatos(true);

    this.intervaloRefresco = window.setInterval(() => {
      if (!this.estaSincronizacionPausada()) {
        this.cargarPlatos(false);
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

  entregarOrden(orden: OrdenCocinaResponse): void {
    if (this.procesandoOrdenId() || orden.ordenEstado === 'Entregado') {
      return;
    }

    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);
    this.pausarSincronizacion(this.refreshAfterWriteMs + 500);

    this.ordenesApi
      .marcarEntregada(orden.id)
      .pipe(take(1))
      .subscribe({
        next: () => this.recargarConRetardo(this.refreshAfterWriteMs),
        error: (err) => {
          console.error(err);
          this.error.set('No se pudo marcar la orden como entregada.');
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

  etiquetaEstado(orden: OrdenCocinaResponse): string {
    return orden.ordenEstado === 'Entregado' ? 'ENTREGADO' : 'LISTO';
  }

  detallesVisibles(orden: OrdenCocinaResponse): string {
    const detalles = orden.detalles?.trim();
    return detalles && detalles.length > 0 ? detalles : 'Sin detalles adicionales';
  }

  tiempoLista(orden: OrdenCocinaResponse): string {
    const referencia = orden.pedido?.fechaPedido ?? orden.fecha;
    const diffMin = this.diferenciaEnMinutos(referencia);

    if (orden.ordenEstado === 'Entregado') {
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
    return orden.ordenEstado === 'Entregado';
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
        this.ordenes.set(this.filtrarVisibles(ordenes));
        this.cargando.set(false);
        this.procesandoOrdenId.set(null);

        if (this.ordenes().length > 0 || !this.error()) {
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
