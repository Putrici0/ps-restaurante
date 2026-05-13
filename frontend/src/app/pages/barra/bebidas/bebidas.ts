import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer, of, forkJoin } from 'rxjs';
import { take, switchMap, catchError, filter } from 'rxjs/operators';
import { OrdenCocinaResponse, OrdenesApiService } from '../../../services/ordenes-api.service';
import { Navbar } from '../../../shared/navbar/navbar';
import { PedidoCard } from '../../../shared/pedido-card/pedido-card';

interface GrupoBebidaVisual {
  key: string;
  ordenes: OrdenCocinaResponse[];
  nombre: string;
  cantidad: number;
  mesa: string;
  estado: string;
  etiqueta: string;
  tiempo: string;
  esListo: boolean;
  esPendiente: boolean;
  esEntregado: boolean;
  procesando: boolean;
}

@Component({
  selector: 'app-bebidas',
  standalone: true,
  imports: [CommonModule, Navbar, PedidoCard],
  templateUrl: './bebidas.html',
  styleUrl: './bebidas.css',
})
export class Bebidas implements OnInit, OnDestroy {
  private readonly ordenesApi = inject(OrdenesApiService);
  private pollingSub?: Subscription;

  readonly ordenes = signal<OrdenCocinaResponse[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly procesandoGrupoKey = signal<string | null>(null);
  readonly ahora = signal(Date.now());

  readonly pendientesCount = computed(() =>
    this.ordenes().filter(
      (o) =>
        o.ordenEstado === 'Pendiente' ||
        o.ordenEstado === 'Preparación' ||
        o.ordenEstado === 'Listo',
    ).length,
  );

  readonly gruposVisuales = computed<GrupoBebidaVisual[]>(() => {
    this.ahora();
    const mapa = new Map<string, OrdenCocinaResponse[]>();

    for (const orden of this.ordenes()) {
      const mesa = orden.pedido?.cuenta?.mesas?.map((m) => m.id).join(', ') || '?';
      const nombre = orden.plato?.nombre || 'Bebida';
      const key = `${mesa}::${nombre}::${orden.ordenEstado}`;
      const actuales = mapa.get(key) ?? [];
      actuales.push(orden);
      mapa.set(key, actuales);
    }

    return Array.from(mapa.entries())
      .map(([key, ordenes]) => {
        const estado = ordenes[0].ordenEstado;
        const mesa = ordenes[0].pedido?.cuenta?.mesas?.map((m) => m.id).join(', ') || '?';
        const fechaBase = ordenes
          .map((o) => new Date(o.fecha).getTime())
          .sort((a, b) => a - b)[0];

        return {
          key,
          ordenes,
          nombre: ordenes[0].plato?.nombre || 'Bebida',
          cantidad: ordenes.length,
          mesa,
          estado,
          etiqueta:
            this.procesandoGrupoKey() === key
              ? 'ACTUALIZANDO...'
              : `${estado.toUpperCase()} x${ordenes.length}`,
          tiempo: this.calcularTiempo(new Date(fechaBase).toISOString()),
          esListo: estado === 'Listo',
          esPendiente: estado === 'Pendiente',
          esEntregado: estado === 'Entregado',
          procesando: this.procesandoGrupoKey() === key,
        };
      })
      .sort((a, b) => {
        const grupoA = this.grupoOrden(a.estado);
        const grupoB = this.grupoOrden(b.estado);
        if (grupoA !== grupoB) return grupoA - grupoB;
        const fechaA = Math.min(...a.ordenes.map((o) => new Date(o.fecha).getTime()));
        const fechaB = Math.min(...b.ordenes.map((o) => new Date(o.fecha).getTime()));
        return fechaA - fechaB;
      });
  });

  ngOnInit() {
    this.iniciarPolling();
  }

  ngOnDestroy() {
    this.pollingSub?.unsubscribe();
  }

  iniciarPolling() {
    this.pollingSub = timer(0, 8000)
      .pipe(
        filter(() => !this.procesandoGrupoKey()),
        switchMap(() =>
          this.ordenesApi.obtenerBebidasActivasBarra().pipe(
            catchError(() => {
              this.error.set('Error de conexión con el servidor');
              this.cargando.set(false);
              return of(null);
            }),
          ),
        ),
      )
      .subscribe((data) => {
        if (data) {
          this.ordenes.set(data);
          this.cargando.set(false);
          this.error.set(null);
        }
      });
  }

  avanzarUna(grupo: GrupoBebidaVisual) {
    if (this.procesandoGrupoKey() || grupo.ordenes.length === 0) return;
    const orden = grupo.ordenes[0];
    const peticion = this.peticionAvance(orden);
    if (!peticion) return;

    this.procesandoGrupoKey.set(grupo.key);
    this.error.set(null);

    peticion.pipe(take(1)).subscribe({
      next: (ordenActualizada) => {
        this.ordenes.update((list) =>
          list.map((o) => (o.id === ordenActualizada.id ? ordenActualizada : o)),
        );
        this.procesandoGrupoKey.set(null);
      },
      error: () => {
        this.procesandoGrupoKey.set(null);
        this.error.set('No se pudo actualizar el estado. Revisa la conexión.');
      },
    });
  }

  avanzarTodas(grupo: GrupoBebidaVisual) {
    if (this.procesandoGrupoKey() || grupo.ordenes.length === 0) return;

    const peticiones = grupo.ordenes
      .map((orden) => this.peticionAvance(orden))
      .filter((p): p is NonNullable<ReturnType<Bebidas['peticionAvance']>> => !!p);

    if (peticiones.length === 0) return;

    this.procesandoGrupoKey.set(grupo.key);
    this.error.set(null);

    forkJoin(peticiones.map((p) => p.pipe(take(1)))).subscribe({
      next: (ordenesActualizadas) => {
        const mapa = new Map(ordenesActualizadas.map((o) => [o.id, o]));
        this.ordenes.update((list) => list.map((o) => mapa.get(o.id) ?? o));
        this.procesandoGrupoKey.set(null);
      },
      error: () => {
        this.procesandoGrupoKey.set(null);
        this.error.set('No se pudo actualizar el estado. Revisa la conexión.');
      },
    });
  }

  retrocederEstado(grupo: GrupoBebidaVisual) {
    if (this.procesandoGrupoKey() || grupo.ordenes.length === 0) return;

    const orden = grupo.ordenes[0];
    this.procesandoGrupoKey.set(grupo.key);
    this.error.set(null);

    const peticion =
      orden.ordenEstado === 'Entregado'
        ? this.ordenesApi.marcarLista(orden.id)
        : this.ordenesApi.marcarPendiente(orden.id);

    peticion.pipe(take(1)).subscribe({
      next: (ordenActualizada) => {
        this.ordenes.update((list) =>
          list.map((o) => (o.id === ordenActualizada.id ? ordenActualizada : o)),
        );
        this.procesandoGrupoKey.set(null);
      },
      error: () => {
        this.procesandoGrupoKey.set(null);
        this.error.set('No se pudo actualizar el estado. Revisa la conexión.');
      },
    });
  }

  private peticionAvance(orden: OrdenCocinaResponse) {
    if (orden.ordenEstado === 'Pendiente') {
      return this.ordenesApi.marcarEnPreparacion(orden.id);
    }
    if (orden.ordenEstado === 'Preparación') {
      return this.ordenesApi.marcarLista(orden.id);
    }
    if (orden.ordenEstado === 'Listo') {
      return this.ordenesApi.marcarEntregada(orden.id);
    }
    return null;
  }

  private calcularTiempo(fecha: string) {
    const min = Math.floor((Date.now() - new Date(fecha).getTime()) / 60000);
    return min < 1 ? '< 1 min' : `${min} min`;
  }

  private grupoOrden(estado: string): number {
    if (estado === 'Listo') return 0;
    if (estado === 'Preparación' || estado === 'Preparacion') return 1;
    if (estado === 'Pendiente') return 2;
    if (estado === 'Entregado') return 3;
    return 4;
  }
}
