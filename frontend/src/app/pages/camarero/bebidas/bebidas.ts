import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer, of } from 'rxjs';
import { take, switchMap, catchError, filter } from 'rxjs/operators';
import { OrdenCocinaResponse, OrdenesApiService } from '../../../services/ordenes-api.service';
import { CamareroHeader } from '../camarero-header/camarero-header';
import { PedidoCard } from '../../../shared/pedido-card/pedido-card';

@Component({
  selector: 'app-camarero-bebidas',
  standalone: true,
  imports: [CommonModule, CamareroHeader, PedidoCard],
  templateUrl: './bebidas.html',
  styleUrl: './bebidas.css',
})
export class BebidasCamarero implements OnInit, OnDestroy {
  private readonly ordenesApi = inject(OrdenesApiService);
  private pollingSub?: Subscription;

  readonly ordenes = signal<OrdenCocinaResponse[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly procesandoOrdenId = signal<string | null>(null);
  readonly ahora = signal(Date.now());

  readonly pendientesCount = computed(() =>
    this.ordenes().filter(
      o =>
        o.ordenEstado === 'Pendiente' ||
        o.ordenEstado === 'Preparación' ||
        o.ordenEstado === 'Listo',
    ).length
  );

  readonly ordenesVisuales = computed(() => {
    this.ahora();

    return [...this.ordenes()]
      .sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())
      .map(o => ({
        original: o,
        id: o.id,
        nombre: o.plato.nombre,
        mesa: o.pedido?.cuenta?.mesas?.map(m => m.id).join(', ') || '?',
        estado: o.ordenEstado,
        etiqueta: this.procesandoOrdenId() === o.id ? '...' : o.ordenEstado.toUpperCase(),
        tiempo: this.calcularTiempo(o.fecha),
        esListo: o.ordenEstado === 'Listo',
        esPendiente: o.ordenEstado === 'Pendiente',
        esPreparacion: o.ordenEstado === 'Preparación',
        esEntregado: o.ordenEstado === 'Entregado',
        procesando: this.procesandoOrdenId() === o.id,
      }));
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
        filter(() => !this.procesandoOrdenId()),
        switchMap(() =>
          this.ordenesApi.obtenerBebidasActivasBarra().pipe(
            catchError(() => {
              this.error.set('Error de conexión');
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

  avanzarEstado(orden: OrdenCocinaResponse) {
    if (this.procesandoOrdenId()) return;

    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);

    let peticion;

    if (orden.ordenEstado === 'Pendiente') {
      peticion = this.ordenesApi.marcarEnPreparacion(orden.id);
    } else if (orden.ordenEstado === 'Preparación') {
      peticion = this.ordenesApi.marcarLista(orden.id);
    } else if (orden.ordenEstado === 'Listo') {
      peticion = this.ordenesApi.marcarEntregada(orden.id);
    } else {
      this.procesandoOrdenId.set(null);
      return;
    }

    peticion.pipe(take(1)).subscribe({
      next: (ordenActualizada) => {
        this.ordenes.update(list =>
          list.map(o => o.id === ordenActualizada.id ? ordenActualizada : o)
        );

        this.procesandoOrdenId.set(null);
      },
      error: () => {
        this.procesandoOrdenId.set(null);
        this.error.set('Error al actualizar');
      },
    });
  }

  retrocederEstado(orden: OrdenCocinaResponse) {
    if (this.procesandoOrdenId()) return;

    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);

    const peticion = orden.ordenEstado === 'Entregado'
      ? this.ordenesApi.marcarLista(orden.id)
      : this.ordenesApi.marcarPendiente(orden.id);

    peticion.pipe(take(1)).subscribe({
      next: (ordenActualizada) => {
        this.ordenes.update(list =>
          list.map(o => o.id === ordenActualizada.id ? ordenActualizada : o)
        );

        this.procesandoOrdenId.set(null);
      },
      error: () => {
        this.procesandoOrdenId.set(null);
        this.error.set('Error al retroceder');
      },
    });
  }

  private calcularTiempo(fecha: string) {
    const min = Math.floor((Date.now() - new Date(fecha).getTime()) / 60000);
    return min < 1 ? '< 1m' : `${min}m`;
  }
}
