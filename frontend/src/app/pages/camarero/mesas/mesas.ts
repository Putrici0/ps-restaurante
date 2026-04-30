import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Mesa, ZonaMesa } from '../../../models/mesa.model';
import { CuentaApiService, OrdenCuentaResponse } from '../../../services/cuenta-api.service';
import { MesasApiService } from '../../../services/mesas-api.service';

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
  selector: 'app-mesas-camarero',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class MesasCamarero {
  // --- INYECCIÓN DE DEPENDENCIAS ---
  private readonly mesasApi = inject(MesasApiService);
  private readonly cuentaApi = inject(CuentaApiService);
  private readonly router = inject(Router);

  // --- ESTADO PRINCIPAL (Signals) ---
  readonly zonaActiva = signal<ZonaMesa>('interior');
  readonly mesaSeleccionada = signal<Mesa | null>(null); // Para el modal pequeño de opciones
  readonly mesas = signal<Mesa[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly accionMesaId = signal<string | null>(null);

  // --- ESTADO DE COBRO (Signals de tu código de barra) ---
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
  readonly ordenesSeleccionadas = signal<string[]>([]);

  // --- COMPUTADOS ---
  readonly mesasFiltradas = computed(() =>
    this.mesas()
      .filter((mesa) => mesa.zona === this.zonaActiva())
      .sort((a, b) => Number(a.id.replace('M', '')) - Number(b.id.replace('M', '')))
  );

  readonly totalCobro = computed(() => {
    const seleccionadas = new Set(this.ordenesSeleccionadas());
    const total = this.resumenCobro().reduce((acc, item) => {
      const cantidadSeleccionada = item.ordenesIds.filter((id) => seleccionadas.has(id)).length;
      return acc + cantidadSeleccionada * item.precioUnitario;
    }, 0);
    return Number(total.toFixed(2));
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
    if (total <= 0 || this.procesandoCobro() || this.eliminandoOrdenId() !== null || this.mostrarConfirmacionEliminar()) {
      return false;
    }
    if (this.ordenesSeleccionadas().length === 0) return false;
    if (this.metodoPago() === 'TARJETA') return true;
    const recibido = this.importeRecibido();
    return recibido != null && recibido >= total;
  });

  constructor() {
    this.recargarMesas();
  }

  // --- INTERACCIÓN DE LA VISTA MÓVIL ---
  cambiarZona(nuevaZona: ZonaMesa): void {
    this.zonaActiva.set(nuevaZona);
    this.mesaSeleccionada.set(null);
  }

  abrirMesa(mesa: Mesa): void {
    this.mesaSeleccionada.set(mesa);
  }

  cerrarModal(): void {
    this.mesaSeleccionada.set(null);
  }

  hacerPedido(mesaId: string): void {
    this.router.navigate(['/tpv', mesaId]);
  }

  mostrarContrasena(mesaId: string): void {
    // Si tu modelo Mesa no tiene la contraseña, debes sacarla de tu API
    alert(`Contraseña de la mesa ${mesaId}: PENDIENTE_DE_API`);
  }

  verCuenta(mesa: Mesa): void {
    // IMPORTANTE: Asegúrate de que el modelo Mesa que estás usando tenga la ID de la cuenta.
    // He puesto "cuentaId" o "cuentaActivaId" asumiendo que tu backend lo envía ahí.
    const cuentaId = (mesa as any).cuentaId || (mesa as any).cuentaActivaId;

    if (!cuentaId) {
      this.error.set('La mesa no tiene una cuenta activa asociada.');
      return;
    }

    this.cerrarModal(); // Cierra el pop-up pequeño de la mesa
    this.abrirCobro({ mesaId: mesa.id, cuentaId: cuentaId }); // Lanza el modal gigante de cobro
  }

  // --- LÓGICA DE LA API Y MESAS ---
  ocuparMesa(mesaId: string): void {
    this.error.set(null);
    this.accionMesaId.set(mesaId);

    this.mesasApi.ocuparMesa(mesaId).subscribe({
      next: () => {
        this.recargarMesas(mesaId);
        this.cerrarModal();
      },
      error: (err) => {
        console.error('Error ocupando mesa:', err);
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
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
        }
      },
      error: (err) => {
        console.error('Error recargando mesas:', err);
        this.cargando.set(false);
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  // --- TODA LA LÓGICA DE COBRO (IDÉNTICA A TU BARRA) ---
  abrirCobro(payload: { mesaId: string; cuentaId: string }): void {
    this.error.set(null);
    this.cargandoCobro.set(true);
    this.mesaCobroId.set(payload.mesaId);
    this.cuentaCobroId.set(payload.cuentaId);
    this.totalCuentaCobro.set(null);
    this.resumenCobro.set([]);
    this.ordenesSeleccionadas.set([]);
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
        console.error('Error cobrando cuenta:', err);
        this.procesandoCobro.set(false);
        this.accionMesaId.set(null);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  eliminarUnaUnidad(item: ItemCobroAgrupado): void {
    if (!this.puedeEliminarItem(item)) return;
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
    this.ordenesSeleccionadas.set([]);
    this.metodoPago.set('EFECTIVO');
    this.importeRecibido.set(null);
    this.eliminandoOrdenId.set(null);
    this.mostrarConfirmacionEliminar.set(false);
    this.itemPendienteEliminar.set(null);
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
    const estadosNormalizados = estados.map((estado) => (estado === 'Preparación' || estado === 'Preparacion' ? 'En preparación' : estado));
    const unicos = Array.from(new Set(estadosNormalizados));
    return unicos.join(' · ');
  }

  itemSeleccionado(item: ItemCobroAgrupado): boolean {
    if (!item.ordenesIds.length) return false;
    const seleccionadas = new Set(this.ordenesSeleccionadas());
    return item.ordenesIds.every((id) => seleccionadas.has(id));
  }

  toggleSeleccionItem(item: ItemCobroAgrupado): void {
    const seleccionadas = new Set(this.ordenesSeleccionadas());
    const todasSeleccionadas = item.ordenesIds.every((id) => seleccionadas.has(id));

    if (todasSeleccionadas) {
      item.ordenesIds.forEach((id) => seleccionadas.delete(id));
    } else {
      item.ordenesIds.forEach((id) => seleccionadas.add(id));
    }

    this.ordenesSeleccionadas.set(Array.from(seleccionadas));

    if (this.metodoPago() === 'EFECTIVO') {
      const recibido = this.importeRecibido();
      if (recibido != null && recibido < this.totalCobro()) {
        this.importeRecibido.set(null);
      }
    }
  }

  seleccionarTodoCobro(): void {
    const ids = this.resumenCobro().flatMap((item) => item.ordenesIds);
    this.ordenesSeleccionadas.set(ids);
  }

  limpiarSeleccionCobro(): void {
    this.ordenesSeleccionadas.set([]);
    if (this.metodoPago() === 'EFECTIVO') this.importeRecibido.set(null);
  }

  private recargarResumenCobro(): void {
    const cuentaId = this.cuentaCobroId();
    if (!cuentaId) return;

    this.cargandoCobro.set(true);

    forkJoin({
      total: this.mesasApi.obtenerTotalCuenta(cuentaId),
      ordenes: this.cuentaApi.obtenerOrdenesDeCuenta(cuentaId),
    }).subscribe({
      next: ({ total, ordenes }) => {
        const ordenesPendientes = ordenes.filter((orden) => orden.ordenEstado !== 'Cancelado' && !orden.pagada);
        this.totalCuentaCobro.set(Number(total.importe));
        this.resumenCobro.set(this.agruparOrdenes(ordenesPendientes));
        this.ordenesSeleccionadas.set(ordenesPendientes.map((orden) => orden.id).filter((id): id is string => !!id));
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
        mapa.set(platoId, { platoId, nombre, categoria, precioUnitario, cantidad: 0, subtotal: 0, estados: [], ordenesIds: [] });
      }

      const item = mapa.get(platoId)!;
      item.cantidad += 1;
      item.subtotal += precioUnitario;

      if (orden.ordenEstado) item.estados.push(orden.ordenEstado);
      if (orden.id) item.ordenesIds.push(orden.id);
    }

    return Array.from(mapa.values()).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }));
  }

  private extraerMensaje(error: unknown): string {
    const err = error as { error?: { message?: string } };
    return err?.error?.message ?? 'Ha ocurrido un error al comunicar con el backend';
  }
}
