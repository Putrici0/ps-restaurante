import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer, of } from 'rxjs';
import { take, switchMap, catchError, filter } from 'rxjs/operators';
import { OrdenCocinaResponse, OrdenesApiService } from '../../../services/ordenes-api.service';
import { CamareroHeader } from '../camarero-header/camarero-header';
import { PedidoCard } from '../../../shared/pedido-card/pedido-card';

@Component({
  selector: 'app-camarero-platos',
  standalone: true,
  imports: [CommonModule, CamareroHeader, PedidoCard],
  templateUrl: './platos.html',
  styleUrl: './platos.css',
})
export class PlatosCamarero implements OnInit, OnDestroy {
  private readonly ordenesApi = inject(OrdenesApiService);
  private pollingSub?: Subscription;

  readonly ordenes = signal<OrdenCocinaResponse[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly procesandoOrdenId = signal<string | null>(null);
  readonly ahora = signal(Date.now());

  readonly pendientesDeEntregaCount = computed(() => {
    return this.ordenes().filter((o) => o.ordenEstado === 'Listo').length;
  });

  readonly ordenesVisuales = computed(() => {
    this.ahora();
    return [...this.ordenes()]
      .sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())
      .map((o) => ({
        original: o,
        id: o.id,
        nombre: o.plato.nombre,
        mesa: o.pedido?.cuenta?.mesas?.map((m) => m.id).join(', ') || '?',
        estado: o.ordenEstado,
        etiqueta: this.procesandoOrdenId() === o.id ? '...' : o.ordenEstado.toUpperCase(),
        tiempo: this.calcularTiempo(o.fecha),
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
    this.pollingSub = timer(0, 5000)
      .pipe(
        filter(() => !this.procesandoOrdenId()),
        switchMap(() =>
          this.ordenesApi.obtenerPlatosSala().pipe(
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

  entregarPlato(orden: OrdenCocinaResponse) {
    if (this.procesandoOrdenId()) return;
    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);

    this.ordenesApi
      .marcarEntregada(orden.id)
      .pipe(take(1))
      .subscribe({
        next: (ordenActualizada) => {
          this.ordenes.update((l) =>
            l.map((o) => (o.id === ordenActualizada.id ? ordenActualizada : o)),
          );
          this.procesandoOrdenId.set(null);
        },
        error: () => {
          this.procesandoOrdenId.set(null);
          this.error.set('Error al entregar');
        },
      });
  }

  deshacerEntrega(orden: OrdenCocinaResponse) {
    if (this.procesandoOrdenId()) return;
    this.procesandoOrdenId.set(orden.id);
    this.error.set(null);

    this.ordenesApi
      .deshacerEntregaPlato(orden.id)
      .pipe(take(1))
      .subscribe({
        next: (ordenActualizada) => {
          this.ordenes.update((l) =>
            l.map((o) => (o.id === ordenActualizada.id ? ordenActualizada : o)),
          );
          this.procesandoOrdenId.set(null);
        },
        error: () => {
          this.procesandoOrdenId.set(null);
          this.error.set('Error al deshacer');
        },
      });
  }

  private calcularTiempo(fecha: string) {
    const min = Math.floor((Date.now() - new Date(fecha).getTime()) / 60000);
    return min < 1 ? '< 1m' : `${min}m`;
  }
}
