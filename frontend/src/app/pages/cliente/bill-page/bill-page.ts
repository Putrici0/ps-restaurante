import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin, take } from 'rxjs';
import { Header } from '../../../shared/header/header';
import {
  CuentaActivaResponse,
  CuentaApiService,
  EstadoCuentaResponse,
  OrdenCuentaResponse,
} from '../../../services/cuenta-api.service';

interface ItemCuentaAgrupado {
  platoId: string;
  nombre: string;
  categoria: string;
  descripcion: string;
  imagen: string;
  precioUnitario: number;
  cantidad: number;
  estados: string[];
  subtotal: number;
  ordenesIds: string[];
}

@Component({
  selector: 'app-bill-page',
  standalone: true,
  imports: [CommonModule, FormsModule, Header],
  templateUrl: './bill-page.html',
  styleUrls: ['./bill-page.css'],
})
export class BillPage implements OnInit, OnDestroy {
  private readonly cuentaApiService = inject(CuentaApiService);
  private readonly route = inject(ActivatedRoute);

  private intervaloRefresco?: number;
  private readonly mesaId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly cuentaActiva = signal<CuentaActivaResponse | null>(null);
  readonly ordenes = signal<OrdenCuentaResponse[]>([]);
  readonly importePendiente = signal(0);
  readonly estadoSaldada = signal<EstadoCuentaResponse | null>(null);

  readonly procesandoPago = signal(false);
  readonly mostrarConfirmacionPago = signal(false);
  readonly ultimoPagoImporte = signal(0);

  readonly vistaPago = signal<'ninguna' | 'seleccion' | 'tarjeta'>('ninguna');
  readonly ordenesSeleccionadas = signal<string[]>([]);

  numeroTarjeta = '';
  nombreCompleto = '';
  fechaCaducidad = '';
  cvc = '';

  readonly cuentaCerrada = computed(() => {
    return !!this.cuentaActiva()?.payed || !!this.estadoSaldada()?.saldada;
  });

  readonly sinCuentaActiva = computed(() => {
    return !this.cargando() && this.cuentaActiva() === null;
  });

  readonly itemsAgrupados = computed(() => {
    const mapa = new Map<string, ItemCuentaAgrupado>();

    for (const orden of this.ordenes()) {
      if (orden.pagada || orden.ordenEstado === 'Cancelado') {
        continue;
      }

      const plato = orden.plato;
      const key = plato.id;

      if (!mapa.has(key)) {
        mapa.set(key, {
          platoId: plato.id,
          nombre: plato.nombre,
          categoria: plato.categoria,
          descripcion: plato.descripcion,
          imagen: plato.imagen,
          precioUnitario: Number(orden.precio),
          cantidad: 0,
          estados: [],
          subtotal: 0,
          ordenesIds: [],
        });
      }

      const item = mapa.get(key)!;
      item.cantidad += 1;
      item.estados.push(orden.ordenEstado);
      item.subtotal += Number(orden.precio);

      if (orden.id) {
        item.ordenesIds.push(orden.id);
      }
    }

    return Array.from(mapa.values()).sort((a, b) =>
      a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
    );
  });

  readonly totalItems = computed(() =>
    this.itemsAgrupados().reduce((acc, item) => acc + item.cantidad, 0),
  );

  readonly totalSeleccionado = computed(() => {
    const seleccionadas = new Set(this.ordenesSeleccionadas());

    const total = this.itemsAgrupados().reduce((acc, item) => {
      const cantidadSeleccionada = item.ordenesIds.filter((id) =>
        seleccionadas.has(id),
      ).length;

      return acc + cantidadSeleccionada * item.precioUnitario;
    }, 0);

    return Number(total.toFixed(2));
  });

  readonly puedePagar = computed(() => {
    return (
      !this.cargando() &&
      !this.procesandoPago() &&
      !!this.cuentaActiva() &&
      !this.cuentaCerrada() &&
      this.itemsAgrupados().length > 0
    );
  });

  ngOnInit(): void {
    this.cargarCuentaCompleta(true);

    this.intervaloRefresco = window.setInterval(() => {
      if (!this.procesandoPago()) {
        this.cargarCuentaCompleta(false);
      }
    }, 4000);
  }

  ngOnDestroy(): void {
    if (this.intervaloRefresco) {
      window.clearInterval(this.intervaloRefresco);
    }
  }

  recargar(): void {
    this.cargarCuentaCompleta(true);
  }

  abrirPagoParcial(): void {
    const ids = this.itemsAgrupados().flatMap((item) => item.ordenesIds);
    this.ordenesSeleccionadas.set(ids);
    this.vistaPago.set('seleccion');
  }

  cerrarPago(): void {
    this.vistaPago.set('ninguna');
  }

  volverASeleccion(): void {
    this.vistaPago.set('seleccion');
  }

  itemSeleccionado(item: ItemCuentaAgrupado): boolean {
    if (!item.ordenesIds.length) {
      return false;
    }

    const seleccionadas = new Set(this.ordenesSeleccionadas());
    return item.ordenesIds.every((id) => seleccionadas.has(id));
  }

  toggleSeleccionItem(item: ItemCuentaAgrupado): void {
    const seleccionadas = new Set(this.ordenesSeleccionadas());

    const todasSeleccionadas = item.ordenesIds.every((id) =>
      seleccionadas.has(id),
    );

    if (todasSeleccionadas) {
      item.ordenesIds.forEach((id) => seleccionadas.delete(id));
    } else {
      item.ordenesIds.forEach((id) => seleccionadas.add(id));
    }

    this.ordenesSeleccionadas.set(Array.from(seleccionadas));
  }

  irATarjeta(): void {
    if (this.totalSeleccionado() <= 0) {
      return;
    }

    this.vistaPago.set('tarjeta');
  }

  datosTarjetaValidos(): boolean {
    const tarjetaValida = /^[0-9]{16}$/.test(this.numeroTarjeta.trim());
    const nombreValido = this.nombreCompleto.trim().length > 3;
    const cvcValido = /^[0-9]{3}$/.test(this.cvc.trim());

    if (!/^(0[1-9]|1[0-2])\/[0-9]{2}$/.test(this.fechaCaducidad.trim())) {
      return false;
    }

    const [mes, anio] = this.fechaCaducidad.split('/').map(Number);
    const ahora = new Date();
    const anioCompleto = 2000 + anio;
    const fechaTarjeta = new Date(anioCompleto, mes);

    return tarjetaValida && nombreValido && cvcValido && fechaTarjeta > ahora;
  }

  pagarCuenta(): void {
    const cuenta = this.cuentaActiva();
    const ordenesSeleccionadas = this.ordenesSeleccionadas();

    const totalOrdenesPendientes = this.itemsAgrupados().reduce(
      (acc, item) => acc + item.ordenesIds.length,
      0,
    );

    if (
      !cuenta ||
      this.procesandoPago() ||
      this.cuentaCerrada() ||
      ordenesSeleccionadas.length === 0
    ) {
      return;
    }

    if (!this.datosTarjetaValidos()) {
      return;
    }

    this.error.set(null);
    this.procesandoPago.set(true);

    const request$ =
      ordenesSeleccionadas.length === totalOrdenesPendientes
        ? this.cuentaApiService.pagarCuentaCompleta(cuenta.id, 'TARJETA')
        : this.cuentaApiService.pagarCuentaParcial(
          cuenta.id,
          ordenesSeleccionadas,
          'TARJETA',
        );

    request$.pipe(take(1)).subscribe({
      next: () => {
        const importePagado = this.totalSeleccionado();
        this.procesandoPago.set(false);
        this.mostrarConfirmacionPago.set(true);
        this.ultimoPagoImporte.set(importePagado);

        this.numeroTarjeta = '';
        this.nombreCompleto = '';
        this.fechaCaducidad = '';
        this.cvc = '';

        this.ordenesSeleccionadas.set([]);
        this.vistaPago.set('ninguna');

        this.cargarCuentaCompleta(false);
      },
      error: () => {
        this.procesandoPago.set(false);
        this.error.set('No se ha podido procesar el pago en este momento.');
      },
    });
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src =
      'https://images.unsplash.com/photo-1544025162-d76694265947?q=80&w=1200&auto=format&fit=crop';
  }

  obtenerResumenEstado(item: ItemCuentaAgrupado): string {
    const estadosNormalizados = item.estados.map((estado) => {
      if (estado === 'Preparación' || estado === 'Preparacion') {
        return 'En preparación';
      }

      return estado;
    });

    const unicos = Array.from(new Set(estadosNormalizados));
    return unicos.join(' · ');
  }

  private cargarCuentaCompleta(mostrarLoading: boolean): void {
    if (!this.mesaId) {
      this.error.set('No se ha podido identificar la mesa.');
      this.cargando.set(false);
      return;
    }

    if (mostrarLoading) {
      this.cargando.set(true);
      this.error.set(null);
    }

    this.cuentaApiService
      .obtenerCuentaActivaDeMesa(this.mesaId)
      .pipe(take(1))
      .subscribe({
        next: (cuenta) => {
          this.cuentaActiva.set(cuenta);

          if (!cuenta) {
            this.ordenes.set([]);
            this.importePendiente.set(0);
            this.estadoSaldada.set(null);
            this.ordenesSeleccionadas.set([]);
            this.cargando.set(false);
            return;
          }

          forkJoin({
            ordenes: this.cuentaApiService.obtenerOrdenesDeCuenta(cuenta.id),
            pendiente: this.cuentaApiService.obtenerPendienteCuenta(cuenta.id),
            saldada: this.cuentaApiService.obtenerEstadoSaldada(cuenta.id),
          })
            .pipe(take(1))
            .subscribe({
              next: ({ ordenes, pendiente, saldada }) => {
                this.ordenes.set(ordenes);
                this.importePendiente.set(Number(pendiente.importe));
                this.estadoSaldada.set(saldada);
                this.cargando.set(false);
              },
              error: () => {
                this.error.set('No se ha podido cargar la cuenta en este momento.');
                this.cargando.set(false);
              },
            });
        },
        error: () => {
          this.error.set('No se ha podido cargar la cuenta en este momento.');
          this.cargando.set(false);
        },
      });
  }

  onFechaInput(valor: string): void {
    let limpio = valor.replace(/\D/g, '');

    if (limpio.length > 4) {
      limpio = limpio.slice(0, 4);
    }

    if (limpio.length >= 3) {
      limpio = limpio.slice(0, 2) + '/' + limpio.slice(2);
    }

    this.fechaCaducidad = limpio;
  }
}
