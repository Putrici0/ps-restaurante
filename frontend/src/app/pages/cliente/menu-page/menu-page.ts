import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { take } from 'rxjs';
import { Header } from '../../../shared/header/header';
import {
  PedidosApiService,
  CrearPedidoClienteRequest,
} from '../../../services/pedidos-api.service';
import { PlatosApiService } from '../../../services/platos-api.service';
import {
  CuentaActivaResponse,
  CuentaApiService,
} from '../../../services/cuenta-api.service';
import { CategoriaPlato, PlatoApi, PlatoMenu } from '../../../models/plato.model';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  imports: [CommonModule, Header],
  templateUrl: './menu-page.html',
  styleUrls: ['./menu-page.css'],
})
export class MenuPage implements OnInit, OnDestroy {
  private readonly platosApiService = inject(PlatosApiService);
  private readonly pedidosApiService = inject(PedidosApiService);
  private readonly cuentaApiService = inject(CuentaApiService);
  private readonly route = inject(ActivatedRoute);

  private intervaloRefresco?: number;
  private toastTimeoutRef?: number;
  private readonly mesaId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly cargando = signal(true);
  readonly cargandoEstadoMesa = signal(true);
  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);
  readonly categoriaSeleccionada = signal<CategoriaPlato | 'Todos'>('Todos');
  readonly platos = signal<PlatoMenu[]>([]);
  readonly showConfirmPopup = signal(false);
  readonly showToast = signal(false);
  readonly toastMessage = signal('');
  readonly toastTipo = signal<'ok' | 'error'>('ok');
  readonly imagenAmpliada = signal<{ src: string; nombre: string } | null>(null);
  readonly cuentaActiva = signal<CuentaActivaResponse | null>(null);

  readonly categorias: ReadonlyArray<CategoriaPlato> = [
    'Bebida',
    'Entrante',
    'Principal',
    'Postre',
  ];

  readonly cuentaCerrada = computed(() => !!this.cuentaActiva()?.payed);

  readonly sinCuentaActiva = computed(
    () => !this.cargandoEstadoMesa() && this.cuentaActiva() === null,
  );

  readonly puedePedir = computed(() => {
    return (
      !this.cargando() &&
      !this.cargandoEstadoMesa() &&
      !this.enviando() &&
      !this.cuentaCerrada() &&
      this.cuentaActiva() !== null
    );
  });

  readonly platosFiltrados = computed(() => {
    const categoria = this.categoriaSeleccionada();
    const lista = this.platos();

    if (categoria === 'Todos') {
      return lista;
    }

    return lista.filter((plato) => plato.categoria === categoria);
  });

  readonly platosSeleccionados = computed(() =>
    this.platos().filter((plato) => plato.cantidad > 0),
  );

  readonly totalSeleccionado = computed(() =>
    this.platos().reduce((acc, plato) => acc + plato.cantidad, 0),
  );

  readonly importeSeleccionado = computed(() =>
    this.platos().reduce(
      (acc, plato) => acc + plato.cantidad * Number(plato.precio),
      0,
    ),
  );

  ngOnInit(): void {
    this.cargarMenu(true);
    this.cargarEstadoMesa(true);

    this.intervaloRefresco = window.setInterval(() => {
      this.cargarEstadoMesa(false);
      this.cargarMenu(false);
    }, 4000);
  }

  ngOnDestroy(): void {
    if (this.intervaloRefresco) {
      window.clearInterval(this.intervaloRefresco);
    }
    if (this.toastTimeoutRef) {
      window.clearTimeout(this.toastTimeoutRef);
    }
  }

  seleccionarCategoria(categoria: CategoriaPlato | 'Todos'): void {
    this.categoriaSeleccionada.set(categoria);
  }

  incrementar(id: string): void {
    if (!this.puedePedir()) {
      return;
    }

    this.platos.update((lista) =>
      lista.map((plato) =>
        plato.id === id
          ? { ...plato, cantidad: plato.cantidad + 1 }
          : plato,
      ),
    );
  }

  decrementar(id: string): void {
    if (!this.puedePedir()) {
      return;
    }

    this.platos.update((lista) =>
      lista.map((plato) =>
        plato.id === id && plato.cantidad > 0
          ? { ...plato, cantidad: plato.cantidad - 1 }
          : plato,
      ),
    );
  }

  abrirConfirmacionPedido(): void {
    if (!this.puedePedir() || this.totalSeleccionado() === 0) {
      return;
    }

    this.showConfirmPopup.set(true);
  }

  cerrarConfirmacionPedido(): void {
    if (this.enviando()) {
      return;
    }

    this.showConfirmPopup.set(false);
  }

  confirmarPedido(): void {
    if (!this.puedePedir() || this.enviando()) {
      return;
    }

    if (!this.mesaId) {
      this.mostrarToast('No se ha podido identificar la mesa.', 'error');
      return;
    }

    const platosSeleccionados = this.platosSeleccionados();

    if (platosSeleccionados.length === 0) {
      this.error.set('Debes seleccionar al menos un plato.');
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    const body: CrearPedidoClienteRequest = {
      items: platosSeleccionados.map((plato) => ({
        platoId: plato.id,
        cantidad: plato.cantidad,
        detalles: '',
      })),
    };

    this.pedidosApiService
      .crearPedidoDesdeMesa(this.mesaId, body)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.platos.update((lista) =>
            lista.map((plato) => ({ ...plato, cantidad: 0 })),
          );

          this.enviando.set(false);
          this.showConfirmPopup.set(false);
          this.mostrarToast('Pedido enviado correctamente', 'ok');
        },
        error: (err) => {
          const mensaje =
            err?.error?.message ||
            err?.error?.mensaje ||
            'No se ha podido guardar el pedido en este momento.';

          this.error.set(mensaje);
          this.enviando.set(false);
          this.mostrarToast(mensaje, 'error');
        },
      });
  }

  recargar(): void {
    this.cargarMenu(true);
    this.cargarEstadoMesa(true);
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src =
      'https://images.unsplash.com/photo-1544025162-d76694265947?q=80&w=1200&auto=format&fit=crop';
  }

  abrirImagen(plato: PlatoMenu): void {
    this.imagenAmpliada.set({
      src: plato.imagen,
      nombre: plato.nombre,
    });
  }

  cerrarImagen(): void {
    this.imagenAmpliada.set(null);
  }

  trackByPlato(_: number, plato: PlatoMenu): string {
    return plato.id;
  }

  private mostrarToast(mensaje: string, tipo: 'ok' | 'error'): void {
    this.toastMessage.set(mensaje);
    this.toastTipo.set(tipo);
    this.showToast.set(true);

    if (this.toastTimeoutRef) {
      window.clearTimeout(this.toastTimeoutRef);
    }

    this.toastTimeoutRef = window.setTimeout(() => {
      this.showToast.set(false);
    }, 2600);
  }

  private cargarEstadoMesa(mostrarLoading: boolean): void {
    if (!this.mesaId) {
      this.cargandoEstadoMesa.set(false);
      this.cuentaActiva.set(null);
      return;
    }

    if (mostrarLoading) {
      this.cargandoEstadoMesa.set(true);
    }

    this.cuentaApiService
      .obtenerCuentaActivaDeMesa(this.mesaId)
      .pipe(take(1))
      .subscribe({
        next: (cuenta) => {
          this.cuentaActiva.set(cuenta);

          if (cuenta?.payed) {
            this.showConfirmPopup.set(false);
            this.platos.update((lista) =>
              lista.map((plato) => ({ ...plato, cantidad: 0 })),
            );
          }

          this.cargandoEstadoMesa.set(false);
        },
        error: () => {
          this.cargandoEstadoMesa.set(false);
        },
      });
  }

  private cargarMenu(mostrarLoading: boolean): void {
    if (mostrarLoading) {
      this.cargando.set(true);
      this.error.set(null);
    }

    const cantidadesPrevias = new Map(
      this.platos().map((plato) => [plato.id, plato.cantidad]),
    );

    this.platosApiService
      .obtenerPlatos()
      .pipe(take(1))
      .subscribe({
        next: (platosApi: PlatoApi[]) => {
          const platosMenu: PlatoMenu[] = platosApi
            .slice()
            .sort(this.ordenarPorCategoriaYNombre)
            .map((plato) => ({
              ...plato,
              precio: Number(plato.precio),
              cantidad: cantidadesPrevias.get(plato.id) ?? 0,
            }));

          this.platos.set(platosMenu);
          this.cargando.set(false);
        },
        error: () => {
          this.error.set('No se ha podido cargar la carta en este momento.');
          this.cargando.set(false);
        },
      });
  }

  private ordenarPorCategoriaYNombre(a: PlatoApi, b: PlatoApi): number {
    const ordenCategorias: Record<CategoriaPlato, number> = {
      Bebida: 0,
      Entrante: 1,
      Principal: 2,
      Postre: 3,
    };

    const diferenciaCategoria =
      ordenCategorias[a.categoria] - ordenCategorias[b.categoria];

    if (diferenciaCategoria !== 0) {
      return diferenciaCategoria;
    }

    return a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' });
  }
}
