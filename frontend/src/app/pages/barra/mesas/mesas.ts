import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { MesaDetalle } from '../../../shared/mesa-detalle/mesa-detalle';
import { Navbar } from '../../../shared/navbar/navbar';
import { Mesa, ZonaMesa } from '../../../models/mesa.model';
import { CuentaApiService, OrdenCuentaResponse } from '../../../services/cuenta-api.service';
import { MesasApiService } from '../../../services/mesas-api.service';
import { MESAS_LAYOUT } from '../../../data/mesas-layout';

type DireccionUnion = 'arriba' | 'derecha' | 'abajo' | 'izquierda';

interface MesaCeldaVista {
  mesaFisicaId: string;
  mesa: Mesa;
  esPrincipal: boolean;
  esFantasma: boolean;
  fila: number;
  columna: number;
  vecinos: Partial<Record<DireccionUnion, string>>;
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
  imports: [CommonModule, MesaDetalle, Navbar],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class Mesas {
  private readonly mesasApi = inject(MesasApiService);
  private readonly cuentaApi = inject(CuentaApiService);

  readonly zona = signal<ZonaMesa>('interior');
  readonly mesaSeleccionada = signal<Mesa | null>(null);
  readonly mesas = signal<Mesa[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly accionMesaId = signal<string | null>(null);
  readonly modoEdicion = signal(false);
  readonly accionAgrupacionMesaId = signal<string | null>(null);

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

  readonly totalCobro = computed(() => {
    const seleccion = this.seleccionCantidadPorPlato();

    const total = this.resumenCobro().reduce((acc, item) => {
      if (!this.puedeCobrarItem(item)) {
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

  readonly ordenesSeleccionadas = computed(() => {
    const seleccion = this.seleccionCantidadPorPlato();
    const ids: string[] = [];

    for (const item of this.resumenCobro()) {
      if (!this.puedeCobrarItem(item)) {
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

  readonly cambioCobro = computed(() => {
    const total = this.totalCobro();
    const recibido = this.importeRecibido();

    if (this.metodoPago() !== 'EFECTIVO' || recibido == null) {
      return null;
    }

    return Number((recibido - total).toFixed(2));
  });

  readonly faltaCobro = computed(() => {
    const total = this.totalCobro();
    const recibido = this.importeRecibido();

    if (this.metodoPago() !== 'EFECTIVO' || recibido == null) {
      return null;
    }

    if (recibido >= total) {
      return 0;
    }

    return Number((total - recibido).toFixed(2));
  });

  readonly puedeConfirmarCobro = computed(() => {
    const total = this.totalCobro();

    if (
      total <= 0 ||
      this.procesandoCobro() ||
      this.eliminandoOrdenId() !== null ||
      this.mostrarConfirmacionEliminar()
    ) {
      return false;
    }

    if (this.ordenesSeleccionadas().length === 0) {
      return false;
    }

    if (this.metodoPago() === 'TARJETA') {
      return true;
    }

    const recibido = this.importeRecibido();
    return recibido != null && recibido >= total;
  });

  constructor() {
    this.recargarMesas();
  }

  seleccionarCelda(celda: MesaCeldaVista): void {
    const mesa = celda.mesa;
    const actual = this.mesaSeleccionada();

    if (
      actual &&
      this.claveGrupo(actual.grupoMesaIds) === this.claveGrupo(mesa.grupoMesaIds)
    ) {
      this.mesaSeleccionada.set(null);
      return;
    }

    this.mesaSeleccionada.set(mesa);
  }

  toggleModoEdicion(): void {
    this.modoEdicion.update((value) => !value);
  }

  unirMesaLado(celda: MesaCeldaVista, direccion: DireccionUnion, event: MouseEvent): void {
    event.stopPropagation();

    const mesaIdDestino = celda.vecinos[direccion];
    if (!mesaIdDestino || this.accionAgrupacionMesaId() !== null) {
      return;
    }

    this.error.set(null);
    this.accionAgrupacionMesaId.set(celda.mesaFisicaId);

    this.mesasApi.unirMesas(celda.mesaFisicaId, mesaIdDestino).subscribe({
      next: () => {
        this.accionAgrupacionMesaId.set(null);
        this.recargarMesas(celda.mesa.mesaPrincipalId ?? celda.mesa.id);
      },
      error: (err) => {
        console.error('Error uniendo mesas:', err);
        this.accionAgrupacionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  separarMesaSeleccionada(): void {
    const mesa = this.mesaSeleccionada();
    if (!mesa || mesa.grupoMesaIds.length <= 1 || this.accionAgrupacionMesaId() !== null) {
      return;
    }

    const mesaId = mesa.mesaPrincipalId ?? mesa.id;
    this.error.set(null);
    this.accionAgrupacionMesaId.set(mesaId);

    this.mesasApi.separarMesa(mesaId).subscribe({
      next: () => {
        this.accionAgrupacionMesaId.set(null);
        this.recargarMesas(mesaId);
      },
      error: (err) => {
        console.error('Error separando mesas:', err);
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
    if (ids.length <= 1) {
      return `M${mesa.id}`;
    }

    return ids.map((id) => `M${id}`).join(' + ');
  }

  subEtiquetaMesa(mesa: Mesa): string {
    const ids = this.normalizarGrupoMesaIds(mesa.grupoMesaIds);
    return ids.length <= 1 ? 'Mesa individual' : `${ids.length} mesas unidas`;
  }

  puedeUnirsePor(direccion: DireccionUnion, celda: MesaCeldaVista): boolean {
    const vecinoId = celda.vecinos[direccion];
    return !!vecinoId && !celda.mesa.grupoMesaIds.includes(vecinoId);
  }

  ocuparMesa(mesaId: string): void {
    this.error.set(null);
    this.accionMesaId.set(mesaId);

    this.mesasApi.ocuparMesa(mesaId).subscribe({
      next: () => this.recargarMesas(mesaId),
      error: (err) => {
        console.error('Error ocupando mesa:', err);
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

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
    const totalOrdenesPendientes = this.resumenCobro().reduce(
      (acc, item) => acc + item.ordenesIds.length,
      0,
    );

    if (
      !cuentaId ||
      !mesaId ||
      ordenesSeleccionadas.length === 0 ||
      !this.puedeConfirmarCobro()
    ) {
      return;
    }

    this.error.set(null);
    this.procesandoCobro.set(true);
    this.accionMesaId.set(mesaId);

    const request$ =
      ordenesSeleccionadas.length === totalOrdenesPendientes
        ? this.mesasApi.pagarCuentaCompleta(cuentaId, metodoPago)
        : this.mesasApi.pagarCuentaParcial(cuentaId, ordenesSeleccionadas, metodoPago);

    request$.subscribe({
      next: () => {
        this.procesandoCobro.set(false);
        this.cerrarModalCobro();
        this.recargarMesas(mesaId);
      },
      error: (err) => {
        console.error('Error cobrando cuenta:', err);
        this.procesandoCobro.set(false);
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  eliminarUnaUnidad(item: ItemCobroAgrupado): void {
    if (!this.puedeEliminarItem(item)) {
      return;
    }

    const cantidadSolicitada = this.cantidadSeleccionada(item);
    const cantidadAEliminar = Math.max(
      1,
      Math.min(cantidadSolicitada > 0 ? cantidadSolicitada : 1, item.ordenesIds.length),
    );

    this.itemPendienteEliminar.set(item);
    this.ordenesPendientesEliminar.set(item.ordenesIds.slice(0, cantidadAEliminar));
    this.mostrarConfirmacionEliminar.set(true);
  }

  solicitarLiberarMesa(mesaId: string): void {
    if (!mesaId || this.accionMesaId() !== null) {
      return;
    }

    this.mesaPendienteLiberarId.set(mesaId);
    this.mostrarConfirmacionLiberar.set(true);
  }

  cancelarConfirmacionLiberar(): void {
    this.mostrarConfirmacionLiberar.set(false);
    this.mesaPendienteLiberarId.set(null);
  }

  confirmarLiberarMesa(): void {
    const mesaId = this.mesaPendienteLiberarId();
    if (!mesaId) {
      return;
    }

    this.error.set(null);
    this.accionMesaId.set(mesaId);

    this.mesasApi.liberarMesa(mesaId).subscribe({
      next: () => {
        this.mostrarConfirmacionLiberar.set(false);
        this.mesaPendienteLiberarId.set(null);
        this.recargarMesas(mesaId);
      },
      error: (err) => {
        console.error('Error liberando mesa:', err);
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

    forkJoin(
      ordenesIds.map((ordenId) => this.cuentaApi.eliminarOrdenDeCuenta(cuentaId, ordenId)),
    ).subscribe({
      next: () => {
        this.eliminandoOrdenId.set(null);
        this.cancelarConfirmacionEliminar();
        this.recargarResumenCobro();
      },
      error: (err) => {
        console.error('Error eliminando orden de la cuenta:', err);
        this.eliminandoOrdenId.set(null);
        this.cancelarConfirmacionEliminar();
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  puedeEliminarItem(item: ItemCobroAgrupado): boolean {
    return (
      item.ordenesIds.length > 0 &&
      this.eliminandoOrdenId() === null &&
      !this.procesandoCobro() &&
      !this.mostrarConfirmacionEliminar()
    );
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

    if (metodo === 'TARJETA') {
      this.importeRecibido.set(null);
    }
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
    const estadosNormalizados = estados.map((estado) => {
      if (estado === 'Preparación' || estado === 'Preparacion') {
        return 'En preparación';
      }
      return estado;
    });

    const unicos = Array.from(new Set(estadosNormalizados));
    return unicos.join(' · ');
  }

  cantidadSeleccionada(item: ItemCobroAgrupado): number {
    const seleccion = this.seleccionCantidadPorPlato();
    const cantidad = seleccion[item.key] ?? 0;
    return Math.max(0, Math.min(cantidad, item.ordenesIds.length));
  }

  incrementarSeleccion(item: ItemCobroAgrupado): void {
    const actual = this.cantidadSeleccionada(item);
    if (actual >= item.ordenesIds.length) {
      return;
    }
    this.seleccionCantidadPorPlato.update((prev) => ({
      ...prev,
      [item.key]: actual + 1,
    }));
  }

  decrementarSeleccion(item: ItemCobroAgrupado): void {
    const actual = this.cantidadSeleccionada(item);
    if (actual <= 0) {
      return;
    }
    this.seleccionCantidadPorPlato.update((prev) => ({
      ...prev,
      [item.key]: actual - 1,
    }));
  }

  itemSeleccionado(item: ItemCobroAgrupado): boolean {
    return this.cantidadSeleccionada(item) === item.cantidad;
  }

  toggleSeleccionItem(item: ItemCobroAgrupado): void {
    const actual = this.cantidadSeleccionada(item);
    this.seleccionCantidadPorPlato.update((prev) => ({
      ...prev,
      [item.key]: actual === item.cantidad ? 0 : item.cantidad,
    }));

    if (this.metodoPago() === 'EFECTIVO') {
      const recibido = this.importeRecibido();
      if (recibido != null && recibido < this.totalCobro()) {
        this.importeRecibido.set(null);
      }
    }
  }

  seleccionarTodoCobro(): void {
    const seleccion: Record<string, number> = {};
    for (const item of this.resumenCobro()) {
      seleccion[item.key] = this.puedeCobrarItem(item) ? item.cantidad : 0;
    }
    this.seleccionCantidadPorPlato.set(seleccion);
  }

  limpiarSeleccionCobro(): void {
    const seleccion: Record<string, number> = {};
    for (const item of this.resumenCobro()) {
      seleccion[item.key] = 0;
    }
    this.seleccionCantidadPorPlato.set(seleccion);
    if (this.metodoPago() === 'EFECTIVO') {
      this.importeRecibido.set(null);
    }
  }

  private recargarResumenCobro(): void {
    const cuentaId = this.cuentaCobroId();

    if (!cuentaId) {
      return;
    }

    this.cargandoCobro.set(true);

    this.cuentaApi.obtenerResumenCuenta(cuentaId).subscribe({
      next: (cuentaResumen) => {
        const ordenesPendientes = cuentaResumen.ordenes.filter(
          (orden) => orden.ordenEstado !== 'Cancelado' && !orden.pagada,
        );

        this.totalCuentaCobro.set(Number(cuentaResumen.total));
        const resumenAgrupado = this.agruparOrdenes(ordenesPendientes);
        this.resumenCobro.set(resumenAgrupado);
        const seleccionInicial: Record<string, number> = {};
        for (const item of resumenAgrupado) {
          seleccionInicial[item.key] = 0;
        }
        this.seleccionCantidadPorPlato.set(seleccionInicial);
        this.cargandoCobro.set(false);
      },
      error: (err) => {
        console.error('Error obteniendo datos del cobro:', err);
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
        mapa.set(key, {
          key,
          platoId,
          nombre,
          categoria,
          precioUnitario,
          cantidad: 0,
          subtotal: 0,
          estados: [],
          ordenesIds: [],
        });
      }

      const item = mapa.get(key)!;
      item.cantidad += 1;
      item.subtotal += precioUnitario;

      if (orden.ordenEstado) {
        item.estados.push(orden.ordenEstado);
      }

      if (orden.id) {
        item.ordenesIds.push(orden.id);
      }
    }

    return Array.from(mapa.values()).sort((a, b) => {
      const entregaA = this.puedeCobrarItem(a) ? 1 : 0;
      const entregaB = this.puedeCobrarItem(b) ? 1 : 0;

      if (entregaA !== entregaB) {
        return entregaB - entregaA;
      }

      const nombre = a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' });
      if (nombre !== 0) {
        return nombre;
      }

      return this.obtenerResumenEstado(a.estados).localeCompare(
        this.obtenerResumenEstado(b.estados),
        'es',
        { sensitivity: 'base' },
      );
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

        if (!seleccionActual) {
          return;
        }

        const mesaRefrescada = this.buscarMesaLogicaPorId(
          mesas,
          seleccionActual.mesaPrincipalId ?? seleccionActual.id,
        );
        this.mesaSeleccionada.set(mesaRefrescada);
      },
      error: (err) => {
        console.error('Error recargando mesas:', err);
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

  cerrarSeleccion(event: MouseEvent): void {
    const target = event.target as HTMLElement;

    if (!target.closest('.mesa-grid-cell') && !target.closest('.sidebar-detalle')) {
      this.mesaSeleccionada.set(null);
    }
  }

  private construirCeldasZona(zona: ZonaMesa): MesaCeldaVista[] {
    const mesasZona = this.mesas()
      .filter((mesa) => mesa.zona === zona)
      .sort((a, b) => this.compararMesaIds(a.id, b.id));

    const layoutZona = MESAS_LAYOUT.filter((mesa) => mesa.zona === zona);
    const ordenMesaIds = layoutZona.map((mesa) => mesa.id);
    const indiceMesaId = new Map(ordenMesaIds.map((id, index) => [id, index]));
    const mesasPorId = new Map(mesasZona.map((mesa) => [mesa.id, mesa]));
    const mesasLogicasPorClave = new Map<string, Mesa>();

    for (const mesa of mesasZona) {
      const grupoMesaIds = this.normalizarGrupoMesaIds(mesa.grupoMesaIds);
      const clave = this.claveGrupo(grupoMesaIds);

      if (mesasLogicasPorClave.has(clave)) {
        continue;
      }

      const miembros = grupoMesaIds
        .map((id) => mesasPorId.get(id))
        .filter((value): value is Mesa => !!value)
        .sort((a, b) => this.compararMesaIds(a.id, b.id));

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

    return ordenMesaIds.map((mesaId, index) => {
      const mesaFisica = mesasPorId.get(mesaId);
      const grupoMesaIds = this.normalizarGrupoMesaIds(mesaFisica?.grupoMesaIds ?? [mesaId]);
      const mesaLogica =
        mesasLogicasPorClave.get(this.claveGrupo(grupoMesaIds)) ??
        ({
          id: mesaId,
          capacidad: mesaFisica?.capacidad ?? 0,
          zona,
          estado: mesaFisica?.estado ?? 'libre',
          cuentaActivaId: mesaFisica?.cuentaActivaId ?? null,
          cuentaActiva: mesaFisica?.cuentaActiva ?? null,
          grupoMesaIds,
          mesaPrincipalId: mesaId,
        } as Mesa);

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
      };
    });
  }

  private buscarMesaLogicaPorId(mesas: Mesa[], mesaId: string): Mesa | null {
    if (!mesaId) {
      return null;
    }

    const mesaFisica = mesas.find((mesa) => mesa.id === mesaId);
    if (!mesaFisica) {
      return null;
    }

    const grupoMesaIds = this.normalizarGrupoMesaIds(mesaFisica.grupoMesaIds);
    const miembros = grupoMesaIds
      .map((id) => mesas.find((mesa) => mesa.id === id))
      .filter((value): value is Mesa => !!value)
      .sort((a, b) => this.compararMesaIds(a.id, b.id));

    const cuentaActiva = miembros.find((item) => item.cuentaActiva)?.cuentaActiva ?? null;
    const mesaPrincipalId = miembros[0]?.id ?? mesaFisica.id;

    return {
      id: mesaPrincipalId,
      capacidad: miembros.reduce((acc, item) => acc + item.capacidad, 0),
      zona: mesaFisica.zona,
      estado: cuentaActiva ? 'ocupada' : 'libre',
      cuentaActivaId: cuentaActiva?.id ?? null,
      cuentaActiva,
      grupoMesaIds,
      mesaPrincipalId,
    };
  }

  private normalizarGrupoMesaIds(grupoMesaIds: string[]): string[] {
    return Array.from(new Set(grupoMesaIds))
      .sort((a, b) => this.compararMesaIds(a, b));
  }

  private claveGrupo(grupoMesaIds: string[]): string {
    return this.normalizarGrupoMesaIds(grupoMesaIds).join('|');
  }

  private compararMesaIds(left: string, right: string): number {
    return Number(left) - Number(right);
  }
}
