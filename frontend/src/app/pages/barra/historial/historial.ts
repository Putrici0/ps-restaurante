import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { Navbar } from '../../../shared/navbar/navbar';
import {
  CuentaApiService,
  CuentaDetalleResponse,
  CuentaPagadaResumenResponse,
  OrdenCuentaResponse,
} from '../../../services/cuenta-api.service';

interface ItemDetalleAgrupado {
  platoId: string;
  nombre: string;
  categoria: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
  estados: string[];
}

@Component({
  selector: 'app-historial',
  standalone: true,
  imports: [CommonModule, Navbar],
  templateUrl: './historial.html',
  styleUrl: './historial.css',
})
export class HistorialComponent {
  private readonly cuentaApi = inject(CuentaApiService);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly fechaFiltro = signal(this.hoyISO());
  readonly cuentas = signal<CuentaPagadaResumenResponse[]>([]);

  readonly paginaActual = signal(1);
  readonly pageSize = 10;

  readonly mostrarDetalle = signal(false);
  readonly cargandoDetalle = signal(false);
  readonly cuentaDetalle = signal<CuentaDetalleResponse | null>(null);
  readonly ordenesDetalle = signal<OrdenCuentaResponse[]>([]);
  readonly totalDetalle = signal<number>(0);

  readonly cuentasPaginadas = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.pageSize;
    return this.cuentas().slice(inicio, inicio + this.pageSize);
  });

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.cuentas().length / this.pageSize)),
  );

  readonly textoResumen = computed(() => {
    const total = this.cuentas().length;
    const mostradas = this.cuentasPaginadas().length;
    return `Mostrando ${mostradas} de ${total} transacciones`;
  });

  readonly detalleAgrupado = computed(() => this.agruparOrdenes(this.ordenesDetalle()));

  constructor() {
    this.cargarHistorial();
  }

  cargarHistorial(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.paginaActual.set(1);

    this.cuentaApi.obtenerCuentasPagadas(this.fechaFiltro()).subscribe({
      next: (cuentas) => {
        this.cuentas.set(cuentas);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error cargando historial:', err);
        this.cargando.set(false);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  actualizarFecha(valor: string): void {
    this.fechaFiltro.set(valor);
  }

  limpiarFiltro(): void {
    this.fechaFiltro.set('');
    this.cargarHistorial();
  }

  aplicarFiltro(): void {
    this.cargarHistorial();
  }

  irAPagina(pagina: number): void {
    if (pagina < 1 || pagina > this.totalPaginas()) {
      return;
    }
    this.paginaActual.set(pagina);
  }

  abrirDetalle(cuentaId: string): void {
    this.mostrarDetalle.set(true);
    this.cargandoDetalle.set(true);
    this.error.set(null);

    forkJoin({
      cuenta: this.cuentaApi.obtenerCuentaPorId(cuentaId),
      ordenes: this.cuentaApi.obtenerOrdenesDeCuenta(cuentaId),
      total: this.cuentaApi.obtenerTotalCuenta(cuentaId),
    }).subscribe({
      next: ({ cuenta, ordenes, total }) => {
        this.cuentaDetalle.set(cuenta);
        this.ordenesDetalle.set(ordenes);
        this.totalDetalle.set(Number(total.importe));
        this.cargandoDetalle.set(false);
      },
      error: (err) => {
        console.error('Error cargando detalle:', err);
        this.cargandoDetalle.set(false);
        this.mostrarDetalle.set(false);
        this.error.set(this.extraerMensaje(err));
      },
    });
  }

  cerrarDetalle(): void {
    this.mostrarDetalle.set(false);
    this.cargandoDetalle.set(false);
    this.cuentaDetalle.set(null);
    this.ordenesDetalle.set([]);
    this.totalDetalle.set(0);
  }

  formatearFecha(fechaIso: string): string {
    const fecha = new Date(fechaIso);
    return new Intl.DateTimeFormat('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(fecha);
  }

  obtenerMesaDetalle(): string {
    const mesas = this.cuentaDetalle()?.mesas ?? [];
    return mesas.length > 0 ? mesas[0].id : '-';
  }

  obtenerMetodoPagoDetalle(): string {
    return this.cuentaDetalle()?.metodoPago ?? '-';
  }

  obtenerResumenEstado(estados: string[]): string {
    const estadosNormalizados = estados.map((estado) =>
      estado === 'Preparación' || estado === 'Preparacion' ? 'En preparación' : estado,
    );

    return Array.from(new Set(estadosNormalizados)).join(' · ');
  }

  private agruparOrdenes(ordenes: OrdenCuentaResponse[]): ItemDetalleAgrupado[] {
    const mapa = new Map<string, ItemDetalleAgrupado>();

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
          cantidad: 0,
          precioUnitario,
          subtotal: 0,
          estados: [],
        });
      }

      const item = mapa.get(platoId)!;
      item.cantidad += 1;
      item.subtotal += precioUnitario;

      if (orden.ordenEstado) {
        item.estados.push(orden.ordenEstado);
      }
    }

    return Array.from(mapa.values()).sort((a, b) =>
      a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' }),
    );
  }

  private hoyISO(): string {
    const hoy = new Date();
    const yyyy = hoy.getFullYear();
    const mm = String(hoy.getMonth() + 1).padStart(2, '0');
    const dd = String(hoy.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  private extraerMensaje(error: unknown): string {
    const err = error as { error?: { message?: string } };
    return err?.error?.message ?? 'Ha ocurrido un error al comunicar con el backend';
  }
}
