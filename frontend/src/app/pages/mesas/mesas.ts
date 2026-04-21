import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { MesaCardComponent } from '../../components/mesa-card/mesa-card';
import { MesaDetalle } from '../../components/mesa-detalle/mesa-detalle';
import { Navbar } from '../../components/navbar/navbar';
import { Mesa, ZonaMesa } from '../../models/mesa.model';
import { CuentaApiService, OrdenCuentaResponse } from '../../services/cuenta-api.service';
import { MesasApiService } from '../../services/mesas-api.service';

interface ItemCobroAgrupado {
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
  imports: [CommonModule, MesaCardComponent, MesaDetalle, Navbar],
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

  readonly mostrarModalCobro = signal(false);
  readonly cuentaCobroId = signal<string | null>(null);
  readonly mesaCobroId = signal<string | null>(null);
  readonly totalCobro = signal<number | null>(null);
  readonly cargandoCobro = signal(false);
  readonly procesandoCobro = signal(false);
  readonly resumenCobro = signal<ItemCobroAgrupado[]>([]);
  readonly metodoPago = signal<'EFECTIVO' | 'TARJETA'>('EFECTIVO');
  readonly importeRecibido = signal<number | null>(null);
  readonly eliminandoOrdenId = signal<string | null>(null);

  readonly mostrarConfirmacionEliminar = signal(false);
  readonly itemPendienteEliminar = signal<ItemCobroAgrupado | null>(null);

  readonly mesasActuales = computed(() =>
    this.mesas()
      .filter((mesa) => mesa.zona === this.zona())
      .sort((a, b) => Number(a.id) - Number(b.id)),
  );

  readonly cambioCobro = computed(() => {
    const total = this.totalCobro();
    const recibido = this.importeRecibido();

    if (this.metodoPago() !== 'EFECTIVO' || total == null || recibido == null) {
      return null;
    }

    return Number((recibido - total).toFixed(2));
  });

  readonly faltaCobro = computed(() => {
    const total = this.totalCobro();
    const recibido = this.importeRecibido();

    if (this.metodoPago() !== 'EFECTIVO' || total == null || recibido == null) {
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
      total == null ||
      this.procesandoCobro() ||
      this.eliminandoOrdenId() !== null ||
      this.mostrarConfirmacionEliminar()
    ) {
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

  seleccionar(mesa: Mesa): void {
    if (this.mesaSeleccionada()?.id === mesa.id) {
      this.mesaSeleccionada.set(null);
      return;
    }

    this.mesaSeleccionada.set(mesa);
  }

  cambiarZona(nuevaZona: ZonaMesa): void {
    this.zona.set(nuevaZona);
    this.mesaSeleccionada.set(null);
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
    this.totalCobro.set(null);
    this.resumenCobro.set([]);
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

    if (!cuentaId || !mesaId || !this.puedeConfirmarCobro()) {
      return;
    }

    this.error.set(null);
    this.procesandoCobro.set(true);
    this.accionMesaId.set(mesaId);

    this.mesasApi.pagarCuentaCompleta(cuentaId, metodoPago).subscribe({
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

    this.itemPendienteEliminar.set(item);
    this.mostrarConfirmacionEliminar.set(true);
  }

  cancelarConfirmacionEliminar(): void {
    this.mostrarConfirmacionEliminar.set(false);
    this.itemPendienteEliminar.set(null);
  }

  confirmarEliminarUnidad(): void {
    const cuentaId = this.cuentaCobroId();
    const item = this.itemPendienteEliminar();
    const ordenId = item?.ordenesIds?.[0];

    if (!cuentaId || !item || !ordenId || this.eliminandoOrdenId() !== null) {
      this.cancelarConfirmacionEliminar();
      return;
    }

    this.error.set(null);
    this.eliminandoOrdenId.set(ordenId);

    this.cuentaApi.eliminarOrdenDeCuenta(cuentaId, ordenId).subscribe({
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
    this.totalCobro.set(null);
    this.resumenCobro.set([]);
    this.metodoPago.set('EFECTIVO');
    this.importeRecibido.set(null);
    this.eliminandoOrdenId.set(null);
    this.mostrarConfirmacionEliminar.set(false);
    this.itemPendienteEliminar.set(null);
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

  private recargarResumenCobro(): void {
    const cuentaId = this.cuentaCobroId();

    if (!cuentaId) {
      return;
    }

    this.cargandoCobro.set(true);

    forkJoin({
      total: this.mesasApi.obtenerTotalCuenta(cuentaId),
      ordenes: this.cuentaApi.obtenerOrdenesDeCuenta(cuentaId),
    }).subscribe({
      next: ({ total, ordenes }) => {
        this.totalCobro.set(Number(total.importe));
        this.resumenCobro.set(this.agruparOrdenes(ordenes));
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
      const precioUnitario = Number(orden.precio ?? 0);

      if (!mapa.has(platoId)) {
        mapa.set(platoId, {
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

      const item = mapa.get(platoId)!;
      item.cantidad += 1;
      item.subtotal += precioUnitario;

      if (orden.ordenEstado) {
        item.estados.push(orden.ordenEstado);
      }

      if (orden.id) {
        item.ordenesIds.push(orden.id);
      }
    }

    return Array.from(mapa.values()).sort((a, b) =>
      a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
    );
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
          const nuevaMesa = mesas.find((m) => m.id === mesaAReseleccionar) ?? null;
          this.mesaSeleccionada.set(nuevaMesa);
          return;
        }

        const seleccionActual = this.mesaSeleccionada();

        if (!seleccionActual) {
          return;
        }

        const mesaRefrescada = mesas.find((m) => m.id === seleccionActual.id) ?? null;
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

    if (!target.closest('app-mesa-card') && !target.closest('.sidebar-detalle')) {
      this.mesaSeleccionada.set(null);
    }
  }
}
