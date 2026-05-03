import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, forkJoin, of, take } from 'rxjs';
import { Header } from '../../../shared/header/header';
import {
  CuentaActivaResponse,
  CuentaApiService,
  EstadoCuentaResponse,
  OrdenCuentaResponse,
} from '../../../services/cuenta-api.service';
import { CocinaTableroResponse, OrdenesApiService } from '../../../services/ordenes-api.service';

interface ItemCuentaAgrupado {
  key: string;
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
  pagado: boolean;
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
  private readonly ordenesApiService = inject(OrdenesApiService);
  private readonly route = inject(ActivatedRoute);

  private intervaloRefresco?: number;
  private avisoPagoTimeoutRef?: number;
  private readonly mesaId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly cuentaActiva = signal<CuentaActivaResponse | null>(null);
  readonly ordenes = signal<OrdenCuentaResponse[]>([]);
  readonly importePendiente = signal(0);
  readonly estadoSaldada = signal<EstadoCuentaResponse | null>(null);
  readonly etaPorOrdenId = signal<Record<string, number>>({});
  readonly etaInicialPorOrdenId = signal<Record<string, number>>({});

  readonly procesandoPago = signal(false);
  readonly mostrarConfirmacionPago = signal(false);
  readonly ultimoPagoImporte = signal(0);

  readonly vistaPago = signal<'ninguna' | 'seleccion' | 'tarjeta'>('ninguna');
  readonly seleccionCantidadPorPlato = signal<Record<string, number>>({});
  readonly avisoPago = signal<string | null>(null);

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

  readonly itemsPendientes = computed(() => {
    const mapa = new Map<string, ItemCuentaAgrupado>();

    for (const orden of this.ordenes()) {
      if (orden.pagada || orden.ordenEstado === 'Cancelado') {
        continue;
      }

      const plato = orden.plato;
      const estado = orden.ordenEstado;
      const key = `${plato.id}::${estado}`;

      if (!mapa.has(key)) {
        mapa.set(key, {
          key,
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
          pagado: false,
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

    return Array.from(mapa.values()).sort((a, b) => {
      const prioridadA = this.puedePagarItem(a) ? 1 : 0;
      const prioridadB = this.puedePagarItem(b) ? 1 : 0;

      if (prioridadA !== prioridadB) {
        return prioridadB - prioridadA;
      }

      return a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' });
    });
  });

  readonly itemsPagados = computed(() => {
    const mapa = new Map<string, ItemCuentaAgrupado>();

    for (const orden of this.ordenes()) {
      if (!orden.pagada || orden.ordenEstado === 'Cancelado') {
        continue;
      }

      const plato = orden.plato;
      const estado = orden.ordenEstado;
      const key = `${plato.id}::${estado}`;

      if (!mapa.has(key)) {
        mapa.set(key, {
          key,
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
          pagado: true,
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

  readonly itemsAgrupados = computed(() => [
    ...this.itemsPendientes(),
    ...this.itemsPagados(),
  ]);

  readonly totalItems = computed(() =>
    this.itemsAgrupados().reduce((acc, item) => acc + item.cantidad, 0),
  );

  readonly ordenesSeleccionadas = computed(() => {
    const seleccion = this.seleccionCantidadPorPlato();
    const ids: string[] = [];

    for (const item of this.itemsPendientes()) {
      if (!this.puedePagarItem(item)) {
        continue;
      }

      const cantidad = Math.max(
        0,
        Math.min(seleccion[item.key] ?? 0, item.ordenesIds.length),
      );
      ids.push(...item.ordenesIds.slice(0, cantidad));
    }

    return ids;
  });

  readonly totalSeleccionado = computed(() => {
    const seleccion = this.seleccionCantidadPorPlato();

    const total = this.itemsPendientes().reduce((acc, item) => {
      if (!this.puedePagarItem(item)) {
        return acc;
      }

      const cantidad = Math.max(
        0,
        Math.min(seleccion[item.key] ?? 0, item.ordenesIds.length),
      );
      return acc + cantidad * item.precioUnitario;
    }, 0);

    return Number(total.toFixed(2));
  });

  readonly puedePagar = computed(() => {
    return (
      !this.cargando() &&
      !this.procesandoPago() &&
      !!this.cuentaActiva() &&
      !this.cuentaCerrada() &&
      this.itemsPendientes().length > 0
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
    if (this.avisoPagoTimeoutRef) {
      window.clearTimeout(this.avisoPagoTimeoutRef);
    }
  }

  recargar(): void {
    this.cargarCuentaCompleta(true);
  }

  abrirPagoParcial(): void {
    const seleccionInicial: Record<string, number> = {};
    for (const item of this.itemsPendientes()) {
      seleccionInicial[item.key] = 0;
    }
    this.seleccionCantidadPorPlato.set(seleccionInicial);
    this.vistaPago.set('seleccion');
  }

  cerrarPago(): void {
    this.vistaPago.set('ninguna');
  }

  volverASeleccion(): void {
    this.vistaPago.set('seleccion');
  }

  cantidadSeleccionada(item: ItemCuentaAgrupado): number {
    const seleccion = this.seleccionCantidadPorPlato();
    const cantidad = seleccion[item.key] ?? 0;
    return Math.max(0, Math.min(cantidad, item.ordenesIds.length));
  }

  incrementarSeleccion(item: ItemCuentaAgrupado): void {
    if (!this.puedePagarItem(item)) {
      this.mostrarAvisoPagoNoEntregado();
      return;
    }

    const actual = this.cantidadSeleccionada(item);
    if (actual >= item.ordenesIds.length) {
      return;
    }
    this.seleccionCantidadPorPlato.update((prev) => ({
      ...prev,
      [item.key]: actual + 1,
    }));
  }

  decrementarSeleccion(item: ItemCuentaAgrupado): void {
    if (!this.puedePagarItem(item)) {
      this.mostrarAvisoPagoNoEntregado();
      return;
    }

    const actual = this.cantidadSeleccionada(item);
    if (actual <= 0) {
      return;
    }
    this.seleccionCantidadPorPlato.update((prev) => ({
      ...prev,
      [item.key]: actual - 1,
    }));
  }

  irATarjeta(): void {
    if (this.totalSeleccionado() <= 0) {
      return;
    }

    this.vistaPago.set('tarjeta');
  }

  seleccionarTodoPago(): void {
    const seleccion: Record<string, number> = {};
    for (const item of this.itemsPendientes()) {
      seleccion[item.key] = this.puedePagarItem(item) ? item.cantidad : 0;
    }
    this.seleccionCantidadPorPlato.set(seleccion);
  }

  limpiarSeleccionPago(): void {
    const seleccion: Record<string, number> = {};
    for (const item of this.itemsPendientes()) {
      seleccion[item.key] = 0;
    }
    this.seleccionCantidadPorPlato.set(seleccion);
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

    const totalOrdenesPendientes = this.itemsPendientes().reduce(
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

        this.seleccionCantidadPorPlato.set({});
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

  trackByItemAgrupado(_: number, item: ItemCuentaAgrupado): string {
    return `${item.key}-${item.pagado ? 'paid' : 'pending'}`;
  }

  puedePagarItem(item: ItemCuentaAgrupado): boolean {
    const estado = this.normalizarEstado(item.estados[0] ?? '');
    return estado === 'Entregado';
  }

  onIntentarSeleccionarItemNoEntregado(item: ItemCuentaAgrupado): void {
    if (!this.puedePagarItem(item)) {
      this.mostrarAvisoPagoNoEntregado();
    }
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

  etaClienteTexto(item: ItemCuentaAgrupado): string {
    if (item.pagado) return 'Completado';

    const estados = item.estados.map((estado) => this.normalizarEstado(estado));

    if (estados.some((e) => e === 'Listo' || e === 'Entregado')) {
      return 'Listo para servir';
    }

    const etasReales = item.ordenesIds
      .map((id) => ({ id, eta: this.etaPorOrdenId()[id] }))
      .filter((v): v is { id: string; eta: number } => typeof v.eta === 'number' && v.eta > 0)
      .sort((a, b) => a.eta - b.eta);

    if (etasReales.length > 0) {
      const principal = etasReales[0];
      let minutos = principal.eta;
      const enPreparacion = estados.some((e) => e === 'Preparacion');
      if (enPreparacion && minutos <= 3) {
        minutos = 2;
      }
      const etiqueta = this.etiquetaProgresoPorOrden(principal.id) ?? (enPreparacion ? 'En cocina' : 'En cola');
      const hora = this.horaEstimadaTexto(minutos);
      return `${minutos} min (${etiqueta.toLowerCase()}) · aprox ${hora}`;
    }

    const base = this.minutosBaseCategoria(item.categoria);
    const estimado = estados.some((e) => e === 'Preparacion')
      ? Math.max(1, Math.round(base * 0.6))
      : base;
    const etiqueta = estados.some((e) => e === 'Preparacion') ? 'en cocina' : 'en cola';
    const minutos = estados.some((e) => e === 'Preparacion') && estimado <= 3 ? 2 : estimado;
    return `${minutos} min (${etiqueta}) · aprox ${this.horaEstimadaTexto(minutos)}`;
  }

  private normalizarEstado(estado: string): string {
    if (estado === 'Preparación' || estado === 'Preparacion') {
      return 'Preparacion';
    }
    return estado;
  }

  private minutosBaseCategoria(categoria: string): number {
    switch (categoria) {
      case 'Entrante':
        return 10;
      case 'Principal':
        return 18;
      case 'Postre':
        return 8;
      case 'Bebida':
        return 4;
      default:
        return 12;
    }
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
            this.etaPorOrdenId.set({});
            this.etaInicialPorOrdenId.set({});
            this.seleccionCantidadPorPlato.set({});
            this.cargando.set(false);
            return;
          }

          forkJoin({
            ordenes: this.cuentaApiService.obtenerOrdenesDeCuenta(cuenta.id),
            pendiente: this.cuentaApiService.obtenerPendienteCuenta(cuenta.id),
            saldada: this.cuentaApiService.obtenerEstadoSaldada(cuenta.id),
            tablero: this.ordenesApiService.obtenerTableroCocina().pipe(
              catchError(() => of(null as CocinaTableroResponse | null)),
            ),
          })
            .pipe(take(1))
            .subscribe({
              next: ({ ordenes, pendiente, saldada, tablero }) => {
                this.ordenes.set(ordenes);
                this.importePendiente.set(Number(pendiente.importe));
                this.estadoSaldada.set(saldada);
                const etaActual = this.extraerEtaPorOrden(tablero);
                this.etaPorOrdenId.set(etaActual);
                this.etaInicialPorOrdenId.set(this.reconciliarEtaInicial(etaActual));
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

  private extraerEtaPorOrden(tablero: CocinaTableroResponse | null): Record<string, number> {
    if (!tablero) return {};

    const mapa: Record<string, number> = {};
    const ordenes = [...(tablero.pendientes ?? []), ...(tablero.enPreparacion ?? [])];

    for (const orden of ordenes) {
      const eta = orden.prioridad?.etaMinutos;
      if (orden.id && typeof eta === 'number' && Number.isFinite(eta) && eta > 0) {
        mapa[orden.id] = eta;
      }
    }

    return mapa;
  }

  private reconciliarEtaInicial(etaActual: Record<string, number>): Record<string, number> {
    const previo = this.etaInicialPorOrdenId();
    const actualizado: Record<string, number> = {};

    for (const [ordenId, eta] of Object.entries(etaActual)) {
      const anterior = previo[ordenId];
      if (typeof anterior === 'number' && anterior > 0) {
        actualizado[ordenId] = Math.max(anterior, eta);
      } else {
        actualizado[ordenId] = eta;
      }
    }

    return actualizado;
  }

  private etiquetaProgresoPorOrden(ordenId: string): string | null {
    const actual = this.etaPorOrdenId()[ordenId];
    const inicial = this.etaInicialPorOrdenId()[ordenId];

    if (
      typeof actual !== 'number' ||
      !Number.isFinite(actual) ||
      actual <= 0 ||
      typeof inicial !== 'number' ||
      !Number.isFinite(inicial) ||
      inicial <= 0
    ) {
      return null;
    }

    const progreso = Math.max(0, Math.min(1, (inicial - actual) / inicial));
    if (progreso >= 0.9) return 'Emplatando';
    if (progreso >= 0.7) return 'Terminando';
    return 'En cocina';
  }

  private horaEstimadaTexto(minutos: number): string {
    const fecha = new Date(Date.now() + minutos * 60000);
    return new Intl.DateTimeFormat('es-ES', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(fecha);
  }

  private mostrarAvisoPagoNoEntregado(): void {
    this.avisoPago.set('No se puede pagar un producto que todavía no ha sido entregado.');

    if (this.avisoPagoTimeoutRef) {
      window.clearTimeout(this.avisoPagoTimeoutRef);
    }

    this.avisoPagoTimeoutRef = window.setTimeout(() => {
      this.avisoPago.set(null);
    }, 2600);
  }
}
