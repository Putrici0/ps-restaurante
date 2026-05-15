import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';

import { MesaDetalle } from '../../../shared/mesa-detalle/mesa-detalle';
import { Navbar } from '../../../shared/navbar/navbar';
import { Mesa, ZonaMesa } from '../../../models/mesa.model';
import { CuentaApiService, OrdenCuentaResponse } from '../../../services/cuenta-api.service';
import { MesasApiService } from '../../../services/mesas-api.service';
import { MESAS_LAYOUT } from '../../../data/mesas-layout';
import { Reservas } from '../reservas/reservas';
import { ReservasApiService } from '../../../services/reservas-api.service';
import { Reserva } from '../../../models/reserva.model';

type DireccionUnion = 'arriba' | 'derecha' | 'abajo' | 'izquierda';

interface MesaCeldaVista {
  mesaFisicaId: string;
  mesa: Mesa;
  esPrincipal: boolean;
  esFantasma: boolean;
  fila: number;
  columna: number;
  vecinos: Partial<Record<DireccionUnion, string>>;
  // NUEVO: Estados visuales de reserva
  estadoReserva: 'ninguno' | 'reservada' | 'proxima' | 'alerta';
  reservaNombre?: string;
}

interface ItemCobroAgrupado {
  key: string;
  platoId: string;
  nombre: string;
  categoria: string;
  precioUnitario: number;
  cantidad: number;
  subtotal: number;
  estados: string[];
  ordenesIds: string[];
}

@Component({
  selector: 'app-mesas',
  standalone: true,
  imports: [CommonModule, MesaDetalle, Navbar, Reservas],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class Mesas implements OnInit, OnDestroy {
  private readonly mesasApi = inject(MesasApiService);
  private readonly cuentaApi = inject(CuentaApiService);
  private readonly reservasApi = inject(ReservasApiService); // API Inyectada

  private reservasSub: Subscription | null = null;
  private relojInterval: any;

  readonly mostrarReservas = signal(false);
  readonly zona = signal<ZonaMesa>('interior');
  readonly mesaSeleccionada = signal<Mesa | null>(null);
  readonly mesas = signal<Mesa[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly accionMesaId = signal<string | null>(null);
  readonly modoEdicion = signal(false);
  readonly accionAgrupacionMesaId = signal<string | null>(null);

  // NUEVO: Reloj y Reservas del día
  readonly reservasHoy = signal<Reserva[]>([]);
  readonly minutosActuales = signal<number>(this.horaActualMinutos());

  // NUEVO: Variables Modal Advertencia Ocupar
  readonly mostrarAvisoReservaModal = signal(false);
  readonly avisoReservaMinutos = signal<number>(0);
  readonly mesaPendienteOcuparId = signal<string>('');

  readonly mostrarModalCobro = signal(false);
  readonly cuentaCobroId = signal<string | null>(null);
  readonly mesaCobroId = signal<string | null>(null);
  readonly totalCuentaCobro = signal<number | null>(null);
  readonly cargandoCobro = signal(false);
  readonly procesandoCobro = signal(false);
  readonly resumenCobro = signal<ItemCobroAgrupado[]>([]);
  readonly metodoPago = signal<'EFECTIVO' | 'TARJETA'>('EFECTIVO');
  readonly importeRecibido = signal<number | null>(null);
  readonly eliminandoOrdenId = signal<string | null>(null);

  readonly mostrarConfirmacionEliminar = signal(false);
  readonly itemPendienteEliminar = signal<ItemCobroAgrupado | null>(null);
  readonly ordenesPendientesEliminar = signal<string[]>([]);
  readonly mostrarConfirmacionLiberar = signal(false);
  readonly mesaPendienteLiberarId = signal<string | null>(null);

  readonly seleccionCantidadPorPlato = signal<Record<string, number>>({});

  readonly celdasActuales = computed(() => this.construirCeldasZona(this.zona()));
  readonly puedeSepararSeleccion = computed(() => {
    const mesa = this.mesaSeleccionada();
    return !!mesa && mesa.grupoMesaIds.length > 1 && mesa.estado === 'libre';
  });

  // (Computados de cobro se mantienen igual...)
  readonly totalCobro = computed(() => {
    const seleccion = this.seleccionCantidadPorPlato();
    const total = this.resumenCobro().reduce((acc, item) => {
      if (!this.puedeCobrarItem(item)) return acc;
      const cantidad = Math.max(0, Math.min(seleccion[item.key] ?? 0, item.ordenesIds.length));
      return acc + cantidad * item.precioUnitario;
    }, 0);
    return Number(total.toFixed(2));
  });

  readonly ordenesSeleccionadas = computed(() => {
    const seleccion = this.seleccionCantidadPorPlato();
    const ids: string[] = [];
    for (const item of this.resumenCobro()) {
      if (!this.puedeCobrarItem(item)) continue;
      const cantidad = Math.max(0, Math.min(seleccion[item.key] ?? 0, item.ordenesIds.length));
      ids.push(...item.ordenesIds.slice(0, cantidad));
    }
    return ids;
  });

  readonly cambioCobro = computed(() => {
    const total = this.totalCobro();
    const recibido = this.importeRecibido();
    if (this.metodoPago() !== 'EFECTIVO' || recibido == null) return null;
    return Number((recibido - total).toFixed(2));
  });

  readonly faltaCobro = computed(() => {
    const total = this.totalCobro();
    const recibido = this.importeRecibido();
    if (this.metodoPago() !== 'EFECTIVO' || recibido == null) return null;
    if (recibido >= total) return 0;
    return Number((total - recibido).toFixed(2));
  });

  readonly puedeConfirmarCobro = computed(() => {
    const total = this.totalCobro();
    if (total <= 0 || this.procesandoCobro() || this.eliminandoOrdenId() !== null || this.mostrarConfirmacionEliminar()) return false;
    if (this.ordenesSeleccionadas().length === 0) return false;
    if (this.metodoPago() === 'TARJETA') return true;
    const recibido = this.importeRecibido();
    return recibido != null && recibido >= total;
  });

  constructor() {
    this.recargarMesas();
  }

  private getLocalHoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  }

  ngOnInit() {
    const hoy = this.getLocalHoy();
    this.reservasSub = this.reservasApi.obtenerReservasPorFecha(hoy).subscribe(res => {
      this.reservasHoy.set(res);
    });

    this.relojInterval = setInterval(() => {
      this.minutosActuales.set(this.horaActualMinutos());
    }, 60000); // Refresca 1 vez por minuto
  }

  ngOnDestroy() {
    this.reservasSub?.unsubscribe();
    if (this.relojInterval) clearInterval(this.relojInterval);
  }

  horaActualMinutos(): number {
    const d = new Date();
    return d.getHours() * 60 + d.getMinutes();
  }

  timeToMins(hora: string): number {
    const [h, m] = hora.split(':').map(Number);
    return h * 60 + m;
  }

  seleccionarCelda(celda: MesaCeldaVista): void {
    const mesa = celda.mesa;
    const actual = this.mesaSeleccionada();
    if (actual && this.claveGrupo(actual.grupoMesaIds) === this.claveGrupo(mesa.grupoMesaIds)) {
      this.mesaSeleccionada.set(null);
      return;
    }
    this.mesaSeleccionada.set(mesa);
  }

  toggleModoEdicion(): void { this.modoEdicion.update((value) => !value); }

  unirMesaLado(celda: MesaCeldaVista, direccion: DireccionUnion, event: MouseEvent): void {
    event.stopPropagation();
    const mesaIdDestino = celda.vecinos[direccion];
    if (!mesaIdDestino || this.accionAgrupacionMesaId() !== null) return;

    this.error.set(null);
    this.accionAgrupacionMesaId.set(celda.mesaFisicaId);

    this.mesasApi.unirMesas(celda.mesaFisicaId, mesaIdDestino).subscribe({
      next: () => {
        this.accionAgrupacionMesaId.set(null);
        this.recargarMesas(celda.mesa.mesaPrincipalId ?? celda.mesa.id);
      },
      error: (err) => {
        this.accionAgrupacionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  separarMesaSeleccionada(): void {
    const mesa = this.mesaSeleccionada();
    if (!mesa || mesa.grupoMesaIds.length <= 1 || this.accionAgrupacionMesaId() !== null) return;

    const mesaId = mesa.mesaPrincipalId ?? mesa.id;
    this.error.set(null);
    this.accionAgrupacionMesaId.set(mesaId);

    this.mesasApi.separarMesa(mesaId).subscribe({
      next: () => {
        this.accionAgrupacionMesaId.set(null);
        this.recargarMesas(mesaId);
      },
      error: (err) => {
        this.accionAgrupacionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  cambiarZona(nuevaZona: ZonaMesa): void {
    this.zona.set(nuevaZona);
    this.mesaSeleccionada.set(null);
  }

  esMesaSeleccionada(mesa: Mesa): boolean {
    const seleccionada = this.mesaSeleccionada();
    return !!seleccionada && this.claveGrupo(seleccionada.grupoMesaIds) === this.claveGrupo(mesa.grupoMesaIds);
  }

  etiquetaMesa(mesa: Mesa): string {
    const ids = this.normalizarGrupoMesaIds(mesa.grupoMesaIds);
    return ids.length <= 1 ? `M${mesa.id}` : ids.map((id) => `M${id}`).join('+');
  }

  puedeUnirsePor(direccion: DireccionUnion, celda: MesaCeldaVista): boolean {
    const vecinoId = celda.vecinos[direccion];
    return !!vecinoId && !celda.mesa.grupoMesaIds.includes(vecinoId);
  }

  // NUEVO: Validador antes de Ocupar Mesa
  solicitarOcuparMesa(mesaId: string): void {
    const minNow = this.minutosActuales();
    const reservas = this.reservasHoy().filter(r => r.estado === 'Confirmado');
    const mesaLogica = this.mesas().find(m => m.id === mesaId);
    const mesasIdsGrupo = mesaLogica ? mesaLogica.grupoMesaIds : [mesaId];

    // NUEVO: Si la mesa ya está en estado reservada/próxima, ocupamos directamente
    // (el personal está confirmando la llegada del cliente que reservó)
    if (mesaLogica?.estado === 'reservada' || mesaLogica?.estado === 'proximamente-reservada') {
      this.ocuparMesaFinal(mesaId);
      return;
    }

    // Busca colisiones en la próxima hora
    const conflict = reservas.find(r =>
      r.mesasIds?.some(id => mesasIdsGrupo.includes(id)) &&
      this.timeToMins(r.hora) - minNow <= 60 &&
      this.timeToMins(r.hora) + 15 >= minNow
    );

    if (conflict) {
      const diff = this.timeToMins(conflict.hora) - minNow;
      if (diff <= 30 && diff >= -15) {
        this.error.set(`Mesa bloqueada por reserva de ${conflict.nombre} a las ${conflict.hora}`);
        return;
      }
      this.avisoReservaMinutos.set(diff);
      this.mesaPendienteOcuparId.set(mesaId);
      this.mostrarAvisoReservaModal.set(true);
    } else {
      this.ocuparMesaFinal(mesaId);
    }
  }

  confirmarOcuparConAviso(): void {
    this.mostrarAvisoReservaModal.set(false);
    this.ocuparMesaFinal(this.mesaPendienteOcuparId());
  }

  private ocuparMesaFinal(mesaId: string): void {
    this.error.set(null);
    this.accionMesaId.set(mesaId);

    this.mesasApi.ocuparMesa(mesaId).subscribe({
      next: () => this.recargarMesas(mesaId),
      error: (err) => {
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  // El resto de métodos de cobro, eliminar, etc...
  abrirCobro(payload: { mesaId: string; cuentaId: string }): void {
    this.error.set(null);
    this.cargandoCobro.set(true);
    this.mesaCobroId.set(payload.mesaId);
    this.cuentaCobroId.set(payload.cuentaId);
    this.totalCuentaCobro.set(null);
    this.resumenCobro.set([]);
    this.seleccionCantidadPorPlato.set({});
    this.metodoPago.set('EFECTIVO');
    this.importeRecibido.set(null);
    this.eliminandoOrdenId.set(null);
    this.mostrarConfirmacionEliminar.set(false);
    this.itemPendienteEliminar.set(null);
    this.mostrarModalCobro.set(true);
    this.recargarResumenCobro();
  }

  confirmarCobro(): void {
    const cuentaId = this.cuentaCobroId();
    const mesaId = this.mesaCobroId();
    const metodoPago = this.metodoPago();
    const ordenesSeleccionadas = this.ordenesSeleccionadas();
    const totalOrdenesPendientes = this.resumenCobro().reduce((acc, item) => acc + item.ordenesIds.length, 0);

    if (!cuentaId || !mesaId || ordenesSeleccionadas.length === 0 || !this.puedeConfirmarCobro()) return;

    this.error.set(null);
    this.procesandoCobro.set(true);
    this.accionMesaId.set(mesaId);

    const request$ = ordenesSeleccionadas.length === totalOrdenesPendientes
      ? this.mesasApi.pagarCuentaCompleta(cuentaId, metodoPago)
      : this.mesasApi.pagarCuentaParcial(cuentaId, ordenesSeleccionadas, metodoPago);

    request$.subscribe({
      next: () => {
        this.procesandoCobro.set(false);
        this.cerrarModalCobro();
        this.recargarMesas(mesaId);
      },
      error: (err) => {
        this.procesandoCobro.set(false);
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  eliminarUnaUnidad(item: ItemCobroAgrupado): void {
    if (!this.puedeEliminarItem(item)) return;
    const cantidadSolicitada = this.cantidadSeleccionada(item);
    const cantidadAEliminar = Math.max(1, Math.min(cantidadSolicitada > 0 ? cantidadSolicitada : 1, item.ordenesIds.length));

    this.itemPendienteEliminar.set(item);
    this.ordenesPendientesEliminar.set(item.ordenesIds.slice(0, cantidadAEliminar));
    this.mostrarConfirmacionEliminar.set(true);
  }

  solicitarLiberarMesa(mesaId: string): void {
    if (!mesaId || this.accionMesaId() !== null) return;
    this.mesaPendienteLiberarId.set(mesaId);
    this.mostrarConfirmacionLiberar.set(true);
  }

  cancelarConfirmacionLiberar(): void {
    this.mostrarConfirmacionLiberar.set(false);
    this.mesaPendienteLiberarId.set(null);
  }

  confirmarLiberarMesa(): void {
    const mesaId = this.mesaPendienteLiberarId();
    if (!mesaId) return;

    this.error.set(null);
    this.accionMesaId.set(mesaId);

    this.mesasApi.liberarMesa(mesaId).subscribe({
      next: () => {
        this.mostrarConfirmacionLiberar.set(false);
        this.mesaPendienteLiberarId.set(null);
        this.recargarMesas(mesaId);
      },
      error: (err) => {
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  cancelarConfirmacionEliminar(): void {
    this.mostrarConfirmacionEliminar.set(false);
    this.itemPendienteEliminar.set(null);
    this.ordenesPendientesEliminar.set([]);
  }

  confirmarEliminarUnidad(): void {
    const cuentaId = this.cuentaCobroId();
    const ordenesIds = this.ordenesPendientesEliminar();
    const primeraOrdenId = ordenesIds[0];

    if (!cuentaId || ordenesIds.length === 0 || !primeraOrdenId || this.eliminandoOrdenId() !== null) {
      this.cancelarConfirmacionEliminar();
      return;
    }

    this.error.set(null);
    this.eliminandoOrdenId.set(primeraOrdenId);

    forkJoin(ordenesIds.map((ordenId) => this.cuentaApi.eliminarOrdenDeCuenta(cuentaId, ordenId))).subscribe({
      next: () => {
        this.eliminandoOrdenId.set(null);
        this.cancelarConfirmacionEliminar();
        this.recargarResumenCobro();
      },
      error: (err) => {
        this.eliminandoOrdenId.set(null);
        this.cancelarConfirmacionEliminar();
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  puedeEliminarItem(item: ItemCobroAgrupado): boolean {
    return (item.ordenesIds.length > 0 && this.eliminandoOrdenId() === null && !this.procesandoCobro() && !this.mostrarConfirmacionEliminar());
  }

  cerrarModalCobro(): void {
    this.mostrarModalCobro.set(false);
    this.cargandoCobro.set(false);
    this.procesandoCobro.set(false);
    this.cuentaCobroId.set(null);
    this.mesaCobroId.set(null);
    this.totalCuentaCobro.set(null);
    this.resumenCobro.set([]);
    this.seleccionCantidadPorPlato.set({});
    this.metodoPago.set('EFECTIVO');
    this.importeRecibido.set(null);
    this.eliminandoOrdenId.set(null);
    this.mostrarConfirmacionEliminar.set(false);
    this.itemPendienteEliminar.set(null);
    this.ordenesPendientesEliminar.set([]);
  }

  cambiarMetodoPago(metodo: 'EFECTIVO' | 'TARJETA'): void {
    this.metodoPago.set(metodo);
    if (metodo === 'TARJETA') this.importeRecibido.set(null);
  }

  actualizarImporteRecibido(valor: string): void {
    if (valor.trim() === '') {
      this.importeRecibido.set(null);
      return;
    }
    const numero = Number(valor);
    this.importeRecibido.set(Number.isNaN(numero) ? null : numero);
  }

  obtenerResumenEstado(estados: string[]): string {
    const estadosNormalizados = estados.map((e) => (e === 'Preparación' || e === 'Preparacion') ? 'En preparación' : e);
    return Array.from(new Set(estadosNormalizados)).join(' · ');
  }

  cantidadSeleccionada(item: ItemCobroAgrupado): number {
    const seleccion = this.seleccionCantidadPorPlato();
    return Math.max(0, Math.min(seleccion[item.key] ?? 0, item.ordenesIds.length));
  }

  incrementarSeleccion(item: ItemCobroAgrupado): void {
    const actual = this.cantidadSeleccionada(item);
    if (actual >= item.ordenesIds.length) return;
    this.seleccionCantidadPorPlato.update((prev) => ({ ...prev, [item.key]: actual + 1 }));
  }

  decrementarSeleccion(item: ItemCobroAgrupado): void {
    const actual = this.cantidadSeleccionada(item);
    if (actual <= 0) return;
    this.seleccionCantidadPorPlato.update((prev) => ({ ...prev, [item.key]: actual - 1 }));
  }

  itemSeleccionado(item: ItemCobroAgrupado): boolean { return this.cantidadSeleccionada(item) === item.cantidad; }

  toggleSeleccionItem(item: ItemCobroAgrupado): void {
    const actual = this.cantidadSeleccionada(item);
    this.seleccionCantidadPorPlato.update((prev) => ({ ...prev, [item.key]: actual === item.cantidad ? 0 : item.cantidad }));

    if (this.metodoPago() === 'EFECTIVO') {
      const recibido = this.importeRecibido();
      if (recibido != null && recibido < this.totalCobro()) this.importeRecibido.set(null);
    }
  }

  seleccionarTodoCobro(): void {
    const seleccion: Record<string, number> = {};
    for (const item of this.resumenCobro()) seleccion[item.key] = this.puedeCobrarItem(item) ? item.cantidad : 0;
    this.seleccionCantidadPorPlato.set(seleccion);
  }

  limpiarSeleccionCobro(): void {
    const seleccion: Record<string, number> = {};
    for (const item of this.resumenCobro()) seleccion[item.key] = 0;
    this.seleccionCantidadPorPlato.set(seleccion);
    if (this.metodoPago() === 'EFECTIVO') this.importeRecibido.set(null);
  }

  private recargarResumenCobro(): void {
    const cuentaId = this.cuentaCobroId();
    if (!cuentaId) return;

    this.cargandoCobro.set(true);
    this.cuentaApi.obtenerResumenCuenta(cuentaId).subscribe({
      next: (cuentaResumen) => {
        const ordenesPendientes = cuentaResumen.ordenes.filter((orden) => orden.ordenEstado !== 'Cancelado' && !orden.pagada);
        this.totalCuentaCobro.set(Number(cuentaResumen.total));
        const resumenAgrupado = this.agruparOrdenes(ordenesPendientes);
        this.resumenCobro.set(resumenAgrupado);

        const seleccionInicial: Record<string, number> = {};
        for (const item of resumenAgrupado) seleccionInicial[item.key] = 0;
        this.seleccionCantidadPorPlato.set(seleccionInicial);
        this.cargandoCobro.set(false);
      },
      error: (err) => {
        this.cargandoCobro.set(false);
        this.cerrarModalCobro();
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  private agruparOrdenes(ordenes: OrdenCuentaResponse[]): ItemCobroAgrupado[] {
    const mapa = new Map<string, ItemCobroAgrupado>();

    for (const orden of ordenes) {
      const plato = orden.plato;
      const platoId = plato?.id ?? `sin-id-${Math.random()}`;
      const nombre = plato?.nombre?.trim() || 'Producto';
      const categoria = plato?.categoria?.trim() || '';
      const estado = orden.ordenEstado?.trim() || 'Sin estado';
      const key = `${platoId}::${estado}`;
      const precioUnitario = Number(orden.precio ?? 0);

      if (!mapa.has(key)) {
        mapa.set(key, { key, platoId, nombre, categoria, precioUnitario, cantidad: 0, subtotal: 0, estados: [], ordenesIds: [] });
      }

      const item = mapa.get(key)!;
      item.cantidad += 1;
      item.subtotal += precioUnitario;
      if (orden.ordenEstado) item.estados.push(orden.ordenEstado);
      if (orden.id) item.ordenesIds.push(orden.id);
    }

    return Array.from(mapa.values()).sort((a, b) => {
      const entregaA = this.puedeCobrarItem(a) ? 1 : 0;
      const entregaB = this.puedeCobrarItem(b) ? 1 : 0;
      if (entregaA !== entregaB) return entregaB - entregaA;
      const nombre = a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' });
      if (nombre !== 0) return nombre;
      return this.obtenerResumenEstado(a.estados).localeCompare(this.obtenerResumenEstado(b.estados), 'es', { sensitivity: 'base' });
    });
  }

  puedeCobrarItem(item: ItemCobroAgrupado): boolean {
    const estado = (item.estados[0] ?? '').trim().toLowerCase();
    return estado === 'entregado';
  }

  private recargarMesas(mesaAReseleccionar?: string): void {
    this.cargando.set(true);
    this.error.set(null);

    this.mesasApi.cargarMesasParaVista().subscribe({
      next: (mesas) => {
        this.mesas.set(mesas);
        this.cargando.set(false);
        this.accionMesaId.set(null);

        if (mesaAReseleccionar) {
          const nuevaMesa = this.buscarMesaLogicaPorId(mesas, mesaAReseleccionar);
          this.mesaSeleccionada.set(nuevaMesa);
          return;
        }

        const seleccionActual = this.mesaSeleccionada();
        if (!seleccionActual) return;
        const mesaRefrescada = this.buscarMesaLogicaPorId(mesas, seleccionActual.mesaPrincipalId ?? seleccionActual.id);
        this.mesaSeleccionada.set(mesaRefrescada);
      },
      error: (err) => {
        this.cargando.set(false);
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  private extraerMensaje(error: unknown): string {
    const err = error as { error?: { message?: string } };
    return err?.error?.message ?? 'Ha ocurrido un error al comunicar con el backend';
  }

  private construirCeldasZona(zona: ZonaMesa): MesaCeldaVista[] {
    const mesasZona = this.mesas().filter((m) => m.zona === zona).sort((a, b) => this.compararMesaIds(a.id, b.id));
    const layoutZona = MESAS_LAYOUT.filter((m) => m.zona === zona);
    const ordenMesaIds = layoutZona.map((m) => m.id);
    const indiceMesaId = new Map(ordenMesaIds.map((id, index) => [id, index]));
    const mesasPorId = new Map(mesasZona.map((m) => [m.id, m]));
    const mesasLogicasPorClave = new Map<string, Mesa>();

    for (const mesa of mesasZona) {
      const grupoMesaIds = this.normalizarGrupoMesaIds(mesa.grupoMesaIds);
      const clave = this.claveGrupo(grupoMesaIds);
      if (mesasLogicasPorClave.has(clave)) continue;

      const miembros = grupoMesaIds.map((id) => mesasPorId.get(id)).filter((v): v is Mesa => !!v).sort((a, b) => this.compararMesaIds(a.id, b.id));
      const mesaPrincipalId = [...grupoMesaIds].sort((a, b) => {
        const indiceA = indiceMesaId.get(a) ?? Number.MAX_SAFE_INTEGER;
        const indiceB = indiceMesaId.get(b) ?? Number.MAX_SAFE_INTEGER;
        return indiceA - indiceB || this.compararMesaIds(a, b);
      })[0];

      const mesaPrincipal = mesasPorId.get(mesaPrincipalId) ?? mesa;
      const cuentaActiva = miembros.find((item) => item.cuentaActiva)?.cuentaActiva ?? null;

      mesasLogicasPorClave.set(clave, {
        id: mesaPrincipal.id,
        capacidad: miembros.reduce((acc, item) => acc + item.capacidad, 0),
        zona,
        estado: cuentaActiva ? 'ocupada' : 'libre',
        cuentaActivaId: cuentaActiva?.id ?? null,
        cuentaActiva,
        grupoMesaIds,
        mesaPrincipalId,
      });
    }

    const reservasActivas = this.reservasHoy().filter(r => r.estado === 'Confirmado');
    const minNow = this.minutosActuales();

    return ordenMesaIds.map((mesaId, index) => {
      const mesaFisica = mesasPorId.get(mesaId);
      const grupoMesaIds = this.normalizarGrupoMesaIds(mesaFisica?.grupoMesaIds ?? [mesaId]);
      const mesaLogicaOriginal = mesasLogicasPorClave.get(this.claveGrupo(grupoMesaIds)) ?? ({
        id: mesaId, capacidad: mesaFisica?.capacidad ?? 0, zona, estado: mesaFisica?.estado ?? 'libre', cuentaActivaId: mesaFisica?.cuentaActivaId ?? null, cuentaActiva: mesaFisica?.cuentaActiva ?? null, grupoMesaIds, mesaPrincipalId: mesaId,
      } as Mesa);

      // Creamos una copia para no mutar el original en el Map
      const mesaLogica = { ...mesaLogicaOriginal };

      // NUEVO: Cálculo de Estado de Reserva Visual
      let estadoReserva: 'ninguno' | 'reservada' | 'proxima' | 'alerta' = 'ninguno';
      let reservaNombre = '';

      const resRelacionada = reservasActivas.find(r => r.mesasIds?.includes(mesaId) && (this.timeToMins(r.hora) + 15 >= minNow));

      if (resRelacionada) {
        const diff = this.timeToMins(resRelacionada.hora) - minNow;

        if (mesaLogica.estado === 'libre') {
          // Si faltan 15 mins o menos (hasta 15 mins de retraso), se bloquea y se pone MORADA
          if (diff <= 15 && diff >= -15) {
            estadoReserva = 'reservada';
            reservaNombre = resRelacionada.nombre;
            mesaLogica.estado = 'reservada';
          }
          // Si falta entre 15 min y 1 hora, se pone NARANJA (Aviso para no sentar gente con calma)
          else if (diff <= 60 && diff > 15) {
            estadoReserva = 'proxima';
            mesaLogica.estado = 'proximamente-reservada';
          }
        } else if (mesaLogica.estado === 'ocupada') {
          // Si hay gente sentada y se acerca la reserva
          if (diff <= 15) estadoReserva = 'alerta';
          else if (diff <= 60) estadoReserva = 'proxima';
        }
      }

      const fila = Math.floor(index / 3) + 1;
      const columna = (index % 3) + 1;

      return {
        mesaFisicaId: mesaId,
        mesa: mesaLogica,
        esPrincipal: mesaLogica.mesaPrincipalId === mesaId,
        esFantasma: mesaLogica.grupoMesaIds.length > 1 && mesaLogica.mesaPrincipalId !== mesaId,
        fila,
        columna,
        vecinos: {
          arriba: fila > 1 ? ordenMesaIds[index - 3] : undefined,
          derecha: columna < 3 ? ordenMesaIds[index + 1] : undefined,
          abajo: index + 3 < ordenMesaIds.length ? ordenMesaIds[index + 3] : undefined,
          izquierda: columna > 1 ? ordenMesaIds[index - 1] : undefined,
        },
        estadoReserva,
        reservaNombre
      };
    });
  }

  private buscarMesaLogicaPorId(mesas: Mesa[], mesaId: string): Mesa | null {
    if (!mesaId) return null;
    const mesaFisica = mesas.find((m) => m.id === mesaId);
    if (!mesaFisica) return null;

    const grupoMesaIds = this.normalizarGrupoMesaIds(mesaFisica.grupoMesaIds);
    const miembros = grupoMesaIds.map((id) => mesas.find((m) => m.id === id)).filter((v): v is Mesa => !!v).sort((a, b) => this.compararMesaIds(a.id, b.id));
    const cuentaActiva = miembros.find((item) => item.cuentaActiva)?.cuentaActiva ?? null;
    const mesaPrincipalId = miembros[0]?.id ?? mesaFisica.id;

    return {
      id: mesaPrincipalId, capacidad: miembros.reduce((acc, item) => acc + item.capacidad, 0), zona: mesaFisica.zona, estado: cuentaActiva ? 'ocupada' : 'libre', cuentaActivaId: cuentaActiva?.id ?? null, cuentaActiva, grupoMesaIds, mesaPrincipalId,
    };
  }

  private normalizarGrupoMesaIds(grupoMesaIds: string[]): string[] { return Array.from(new Set(grupoMesaIds)).sort((a, b) => this.compararMesaIds(a, b)); }
  private claveGrupo(grupoMesaIds: string[]): string { return this.normalizarGrupoMesaIds(grupoMesaIds).join('|'); }
  private compararMesaIds(left: string, right: string): number { return Number(left) - Number(right); }
}
