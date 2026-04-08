import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin, take } from 'rxjs';
import {
  OrdenCocinaResponse,
  OrdenesApiService,
} from '../../services/ordenes-api.service';

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

  readonly cargando = signal(true);
  readonly actualizando = signal(false);
  readonly error = signal<string | null>(null);

  readonly ordenesPendientes = signal<OrdenCocinaResponse[]>([]);
  readonly ordenesPreparacion = signal<OrdenCocinaResponse[]>([]);
  readonly ordenesListas = signal<OrdenCocinaResponse[]>([]);

  readonly detallesAbiertos = signal<Set<string>>(new Set());

  readonly totalOrdenes = computed(
    () =>
      this.ordenesPendientes().length +
      this.ordenesPreparacion().length +
      this.ordenesListas().length,
  );

  ngOnInit(): void {
    this.cargarTablero(true);
    this.intervaloRefresco = window.setInterval(() => {
      this.cargarTablero(false);
    }, 2000);
  }

  ngOnDestroy(): void {
    if (this.intervaloRefresco) {
      window.clearInterval(this.intervaloRefresco);
    }
  }

  recargar(): void {
    this.cargarTablero(true);
  }

  pasarAPreparacion(ordenId: string): void {
    if (this.actualizando()) {
      return;
    }

    this.actualizando.set(true);
    this.moverOrdenLocal(ordenId, 'preparacion');

    this.ordenesApiService
      .marcarEnPreparacion(ordenId)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.actualizando.set(false);
          this.cargarTablero(false);
        },
        error: () => {
          this.actualizando.set(false);
          this.error.set('No se ha podido actualizar el estado de la orden.');
          this.cargarTablero(false);
        },
      });
  }

  pasarAPendiente(ordenId: string): void {
    if (this.actualizando()) {
      return;
    }

    this.actualizando.set(true);

    const orden =
      this.ordenesPreparacion().find((o) => o.id === ordenId) ??
      this.ordenesListas().find((o) => o.id === ordenId);

    if (orden) {
      this.ordenesPreparacion.update((lista) => lista.filter((o) => o.id !== ordenId));
      this.ordenesListas.update((lista) => lista.filter((o) => o.id !== ordenId));
      this.ordenesPendientes.update((lista) => [
        { ...orden, ordenEstado: 'Pendiente' },
        ...lista,
      ]);
    }

    this.ordenesApiService
      .marcarPendiente(ordenId)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.actualizando.set(false);
          this.cargarTablero(false);
        },
        error: () => {
          this.actualizando.set(false);
          this.error.set('No se ha podido actualizar el estado de la orden.');
          this.cargarTablero(false);
        },
      });
  }

  pasarALista(ordenId: string): void {
    if (this.actualizando()) {
      return;
    }

    this.actualizando.set(true);
    this.moverOrdenLocal(ordenId, 'lista');

    this.ordenesApiService
      .marcarLista(ordenId)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.actualizando.set(false);
          this.cargarTablero(false);
        },
        error: () => {
          this.actualizando.set(false);
          this.error.set('No se ha podido actualizar el estado de la orden.');
          this.cargarTablero(false);
        },
      });
  }

  mesaDeOrden(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];
    if (mesas.length === 0) {
      return 'Mesa ?';
    }
    if (mesas.length === 1) {
      return `Mesa ${mesas[0].id}`;
    }
    return `Mesas ${mesas.map((mesa) => mesa.id).join(', ')}`;
  }

  numeroMesaPlano(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];
    if (mesas.length === 0) {
      return '?';
    }
    if (mesas.length === 1) {
      return String(mesas[0].id);
    }
    return mesas.map((mesa) => String(mesa.id)).join(', ');
  }

  categoriaCorta(categoria: string): string {
    switch (categoria) {
      case 'Entrante':
        return 'ENT';
      case 'Principal':
        return 'PPL';
      case 'Postre':
        return 'PST';
      case 'Bebida':
        return 'BEB';
      default:
        return categoria?.slice(0, 3).toUpperCase() ?? '---';
    }
  }

  etaClase(orden: OrdenCocinaResponse): string {
    const minutos = this.minutosDesde(orden.fecha);

    if (minutos >= 20) {
      return 'eta eta--danger';
    }
    if (minutos >= 10) {
      return 'eta eta--warning';
    }
    return 'eta eta--ok';
  }

  etaTexto(orden: OrdenCocinaResponse): string {
    const minutos = this.minutosDesde(orden.fecha);

    if (minutos >= 20) {
      return 'Urgente';
    }
    if (minutos >= 10) {
      return 'En tiempo';
    }
    return 'Reciente';
  }

  tiempoTextoTarjeta(orden: OrdenCocinaResponse): string {
    const minutos = this.minutosDesde(orden.fecha);

    if (minutos < 1) {
      return 'Ahora mismo';
    }
    if (minutos === 1) {
      return 'Hace 1 min';
    }
    if (minutos < 60) {
      return `Hace ${minutos} min`;
    }

    const horas = Math.floor(minutos / 60);
    const resto = minutos % 60;

    if (resto === 0) {
      return horas === 1 ? 'Hace 1 h' : `Hace ${horas} h`;
    }

    return `Hace ${horas} h ${resto} min`;
  }

  tiempoTranscurrido(fechaIso: string): string {
    const fecha = new Date(fechaIso).getTime();
    const ahora = Date.now();
    const minutos = Math.max(0, Math.floor((ahora - fecha) / 60000));

    if (minutos < 1) {
      return 'Hace menos de 1 min';
    }
    if (minutos === 1) {
      return 'Hace 1 min';
    }
    if (minutos < 60) {
      return `Hace ${minutos} min`;
    }

    const horas = Math.floor(minutos / 60);
    if (horas === 1) {
      return 'Hace 1 h';
    }
    return `Hace ${horas} h`;
  }

  toggleDetalle(ordenId: string): void {
    this.detallesAbiertos.update((actuales) => {
      const nuevo = new Set(actuales);
      if (nuevo.has(ordenId)) {
        nuevo.delete(ordenId);
      } else {
        nuevo.add(ordenId);
      }
      return nuevo;
    });
  }

  detalleAbierto(ordenId: string): boolean {
    return this.detallesAbiertos().has(ordenId);
  }

  origenMesa(orden: OrdenCocinaResponse): string {
    const mesas = orden.pedido?.cuenta?.mesas ?? [];
    if (mesas.length === 0) {
      return 'Sin mesa asociada';
    }
    if (mesas.length === 1) {
      return `Mesa ${mesas[0].id}`;
    }
    return `Mesas ${mesas.map((mesa) => mesa.id).join(', ')}`;
  }

  pedidoIdDeOrden(orden: OrdenCocinaResponse): string {
    return orden.pedido?.id ?? '—';
  }

  cuentaIdDeOrden(orden: OrdenCocinaResponse): string {
    return orden.pedido?.cuenta?.id ?? '—';
  }

  fechaLarga(fechaIso: string): string {
    if (!fechaIso) {
      return '—';
    }

    const fecha = new Date(fechaIso);
    if (Number.isNaN(fecha.getTime())) {
      return '—';
    }

    return fecha.toLocaleString('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  detalleTecnico(orden: OrdenCocinaResponse): string {
    const partes: string[] = [];

    if (orden.id) {
      partes.push(`Orden ${orden.id}`);
    }
    if (orden.plato?.id) {
      partes.push(`Plato ${orden.plato.id}`);
    }
    if (orden.ordenEstado) {
      partes.push(`Estado ${orden.ordenEstado}`);
    }

    return partes.join(' · ') || 'Sin detalle';
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src =
      'https://images.unsplash.com/photo-1544025162-d76694265947?q=80&w=1200&auto=format&fit=crop';
  }

  private minutosDesde(fechaIso: string): number {
    const fecha = new Date(fechaIso).getTime();
    const ahora = Date.now();
    return Math.max(0, Math.floor((ahora - fecha) / 60000));
  }

  private moverOrdenLocal(
    ordenId: string,
    destino: 'preparacion' | 'lista',
  ): void {
    const origen = this.ordenesPendientes().find((o) => o.id === ordenId)
      ? 'pendientes'
      : this.ordenesPreparacion().find((o) => o.id === ordenId)
        ? 'preparacion'
        : null;

    if (!origen) {
      return;
    }

    const orden =
      origen === 'pendientes'
        ? this.ordenesPendientes().find((o) => o.id === ordenId)
        : this.ordenesPreparacion().find((o) => o.id === ordenId);

    if (!orden) {
      return;
    }

    if (origen === 'pendientes') {
      this.ordenesPendientes.update((lista) => lista.filter((o) => o.id !== ordenId));
    } else {
      this.ordenesPreparacion.update((lista) => lista.filter((o) => o.id !== ordenId));
    }

    if (destino === 'preparacion') {
      this.ordenesPreparacion.update((lista) => [
        { ...orden, ordenEstado: 'Preparación' },
        ...lista,
      ]);
    } else {
      this.ordenesListas.update((lista) => [
        { ...orden, ordenEstado: 'Listo' },
        ...lista,
      ]);
    }
  }

  private cargarTablero(mostrarLoading: boolean): void {
    if (mostrarLoading) {
      this.cargando.set(true);
    }

    this.error.set(null);

    forkJoin({
      pendientes: this.ordenesApiService.obtenerPendientesCocina(),
      preparacion: this.ordenesApiService.obtenerEnPreparacionCocina(),
      listas: this.ordenesApiService.obtenerListasCocina(),
    })
      .pipe(take(1))
      .subscribe({
        next: ({ pendientes, preparacion, listas }) => {
          this.ordenesPendientes.set(
            pendientes.filter((orden) => orden.pedido?.cuenta?.payed !== true),
          );
          this.ordenesPreparacion.set(
            preparacion.filter((orden) => orden.pedido?.cuenta?.payed !== true),
          );
          this.ordenesListas.set(
            listas.filter((orden) => orden.pedido?.cuenta?.payed !== true),
          );
          this.cargando.set(false);
        },
        error: () => {
          this.error.set('No se ha podido cargar el tablero de cocina.');
          this.cargando.set(false);
        },
      });
  }
}
