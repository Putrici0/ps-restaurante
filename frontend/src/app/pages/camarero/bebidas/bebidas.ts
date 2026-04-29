import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer, of } from 'rxjs';
import { take, switchMap, catchError, filter } from 'rxjs/operators';
import { OrdenCocinaResponse, OrdenesApiService } from '../../../services/ordenes-api.service';
import { PedidoCard } from '../../../shared/pedido-card/pedido-card';


@Component({
  selector: 'app-bebidas',
  standalone: true,
  imports: [CommonModule, PedidoCard],
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

  readonly pendientesCount = computed(
    () =>
      this.ordenes().filter((o) => o.ordenEstado === 'Pendiente' || o.ordenEstado === 'Preparación')
        .length,
  );

  readonly ordenesVisuales = computed(() => {
    this.ahora();
    // Clonamos para evitar errores de mutación en el sort
    return [...this.ordenes()]
      .sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())
      .map((o) => ({
        original: o,
        id: o.id,
        nombre: o.plato.nombre,
        mesa: o.pedido?.cuenta?.mesas?.map((m) => m.id).join(', ') || '?',
        estado: o.ordenEstado,
        etiqueta:
          this.procesandoOrdenId() === o.id ? 'ACTUALIZANDO...' : o.ordenEstado.toUpperCase(),
        tiempo: this.calcularTiempo(o.fecha),
        esListo: o.ordenEstado === 'Listo',
        esPendiente: o.ordenEstado === 'Pendiente',
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
    // timer(0, 8000) respeta tu intervalo original de 8 segundos
    this.pollingSub = timer(0, 8000)
      .pipe(
        filter(() => !this.procesandoOrdenId()),
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

  avanzarEstado(orden: OrdenCocinaResponse) {
    if (this.procesandoOrdenId()) return;
    this.procesandoOrdenId.set(orden.id);
    this.error.set(null); // Limpiamos errores previos

    const peticion =
      orden.ordenEstado === 'Pendiente'
        ? this.ordenesApi.marcarEnPreparacion(orden.id)
        : this.ordenesApi.marcarLista(orden.id);

    peticion.pipe(take(1)).subscribe({
      next: (ordenActualizada) => {
        this.ordenes.update((list) =>
          list.map((o) => (o.id === ordenActualizada.id ? ordenActualizada : o)),
        );
        this.procesandoOrdenId.set(null);
      },
      error: () => {
        this.procesandoOrdenId.set(null);
        this.error.set('No se pudo actualizar el estado. Revisa la conexión.');
      },
    });
  }

  retrocederEstado(orden: OrdenCocinaResponse) {
    if (this.procesandoOrdenId()) return;
    this.procesandoOrdenId.set(orden.id);
    this.error.set(null); // Limpiamos errores previos

    this.ordenesApi
      .marcarPendiente(orden.id)
      .pipe(take(1))
      .subscribe({
        next: (ordenActualizada) => {
          this.ordenes.update((list) =>
            list.map((o) => (o.id === ordenActualizada.id ? ordenActualizada : o)),
          );
          this.procesandoOrdenId.set(null);
        },
        error: () => {
          this.procesandoOrdenId.set(null);
          this.error.set('No se pudo actualizar el estado. Revisa la conexión.');
        },
      });
  }

  private calcularTiempo(fecha: string) {
    const min = Math.floor((Date.now() - new Date(fecha).getTime()) / 60000);
    return min < 1 ? '< 1 min' : `${min} min`;
  }
}
