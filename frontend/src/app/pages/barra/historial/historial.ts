import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import jsPDF from 'jspdf';

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

interface PagoDetalleAgrupado {
  clave: string;
  numero: number;
  fechaPago: string;
  metodoPago: string;
  total: number;
  items: ItemDetalleAgrupado[];
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
  private readonly apiUrl = `http://${window.location.hostname}:7070`;
  private toastTimeoutRef?: number;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly fechaFiltro = signal(this.hoyISO());
  readonly mesaFiltro = signal('');
  readonly cuentas = signal<CuentaPagadaResumenResponse[]>([]);

  readonly paginaActual = signal(1);
  readonly pageSize = 10;

  readonly mostrarDetalle = signal(false);
  readonly cargandoDetalle = signal(false);
  readonly cuentaDetalle = signal<CuentaDetalleResponse | null>(null);
  readonly ordenesDetalle = signal<OrdenCuentaResponse[]>([]);
  readonly totalDetalle = signal<number>(0);

  readonly mostrarModalCorreo = signal(false);
  readonly correoTique = signal('');
  readonly errorCorreoTique = signal<string | null>(null);
  readonly enviandoTique = signal(false);
  readonly showToast = signal(false);
  readonly toastMessage = signal('');
  readonly toastTipo = signal<'ok' | 'error'>('ok');

  readonly cuentasFiltradas = computed(() => {
    const filtroMesa = this.mesaFiltro().trim().toLowerCase();

    if (!filtroMesa) {
      return this.cuentas();
    }

    return this.cuentas().filter((cuenta) =>
      this.obtenerMesaResumen(cuenta)
        .split(',')
        .map((mesa) => mesa.trim().toLowerCase())
        .some((mesa) => mesa === filtroMesa),
    );
  });

  readonly cuentasPaginadas = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.pageSize;
    return this.cuentasFiltradas().slice(inicio, inicio + this.pageSize);
  });

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.cuentasFiltradas().length / this.pageSize)),
  );

  readonly textoResumen = computed(() => {
    const total = this.cuentasFiltradas().length;
    const mostradas = this.cuentasPaginadas().length;
    return `Mostrando ${mostradas} de ${total} transacciones`;
  });

  readonly detalleAgrupado = computed(() =>
    this.agruparOrdenes(
      this.ordenesDetalle().filter((orden) => orden.ordenEstado !== 'Cancelado'),
    ),
  );

  readonly pagosDetalle = computed(() =>
    this.agruparOrdenesPorPago(
      this.ordenesDetalle().filter((orden) => orden.ordenEstado !== 'Cancelado'),
    ),
  );

  readonly canceladosDetalle = computed(() =>
    this.agruparOrdenes(
      this.ordenesDetalle().filter((orden) => orden.ordenEstado === 'Cancelado'),
    ),
  );

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

  actualizarMesa(valor: string): void {
    this.mesaFiltro.set(valor);
  }

  limpiarFiltro(): void {
    this.fechaFiltro.set('');
    this.mesaFiltro.set('');
    this.cargarHistorial();
  }

  aplicarFiltro(): void {
    this.paginaActual.set(1);
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
      ordenes: this.cuentaApi.obtenerTodasLasOrdenesDeCuenta(cuentaId),
    }).subscribe({
      next: ({ cuenta, ordenes }) => {
        this.cuentaDetalle.set(cuenta);
        this.ordenesDetalle.set(ordenes);
        const total = ordenes
          .filter((orden) => orden.ordenEstado !== 'Cancelado')
          .reduce((acc, orden) => acc + Number(orden.precio ?? 0), 0);
        this.totalDetalle.set(Number(total));
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
  descargarTique(): void {
    const pdf = this.construirPdfTique();
    pdf.save(this.obtenerNombreArchivoTique());
  }
  enviarTique(): void {
    this.correoTique.set('');
    this.errorCorreoTique.set(null);
    this.mostrarModalCorreo.set(true);
  }
  actualizarCorreoTique(valor: string): void {
    this.correoTique.set(valor);
    this.errorCorreoTique.set(null);
  }
  cerrarModalCorreo(): void {
    if (this.enviandoTique()) {
      return;
    }
    this.mostrarModalCorreo.set(false);
    this.correoTique.set('');
    this.errorCorreoTique.set(null);
  }
  confirmarEnvioTique(): void {
    const correo = this.correoTique().trim();
    if (!correo) {
      this.errorCorreoTique.set('Introduce un correo.');
      return;
    }
    if (!this.esCorreoValido(correo)) {
      this.errorCorreoTique.set('Correo no válido.');
      return;
    }
    this.enviandoTique.set(true);
    this.errorCorreoTique.set(null);
    const pdf = this.construirPdfTique();
    const pdfBase64 = pdf.output('datauristring');
    fetch(`${this.apiUrl}/tiques/enviar`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        correo,
        nombreArchivo: this.obtenerNombreArchivoTique(),
        pdfBase64,
      }),
    })
      .then(async (respuesta) => {
        if (!respuesta.ok) {
          const error = await respuesta.json().catch(() => null);
          throw new Error(error?.error ?? error?.message ?? 'No se pudo enviar el ticket');
        }
        return respuesta.json();
      })
      .then(() => {
        this.enviandoTique.set(false);
        this.cerrarModalCorreo();
        this.mostrarToast(`Ticket enviado correctamente a ${correo}`, 'ok');
      })
      .catch((error) => {
        console.error('Error enviando ticket:', error);
        this.enviandoTique.set(false);
        this.errorCorreoTique.set(error.message ?? 'No se pudo enviar el ticket');
      });
  }
  private construirPdfTique(): jsPDF {
    const pdf = new jsPDF();
    const margenIzq = 14;
    const margenDer = 196;
    let y = 18;
    const ahora = new Date();
    const cuenta = this.cuentaDetalle();
    const mesaTexto = this.obtenerMesaDetalle();
    const identificador = `TK-${ahora.getFullYear()}${String(ahora.getMonth() + 1).padStart(2, '0')}${String(ahora.getDate()).padStart(2, '0')}-${cuenta?.id?.slice(-6).toUpperCase() ?? 'SINID'}`;
    const saltarPaginaSiHaceFalta = () => {
      if (y > 280) {
        pdf.addPage();
        y = 18;
      }
    };
    const escribirTexto = (
      texto: string,
      x = margenIzq,
      salto = 7,
      opciones?: { negrita?: boolean; tamano?: number },
    ) => {
      pdf.setFontSize(opciones?.tamano ?? 11);
      pdf.setFont('helvetica', opciones?.negrita ? 'bold' : 'normal');
      const lineas = pdf.splitTextToSize(texto, margenDer - margenIzq);
      for (const linea of lineas) {
        saltarPaginaSiHaceFalta();
        pdf.text(linea, x, y);
        y += salto;
      }
    };
    const escribirImporte = (texto: string, importe: number) => {
      saltarPaginaSiHaceFalta();
      pdf.setFontSize(11);
      pdf.setFont('helvetica', 'normal');
      pdf.text(texto, margenIzq, y);
      pdf.text(`${importe.toFixed(2)} €`, margenDer, y, { align: 'right' });
      y += 7;
    };
    escribirTexto('RESTAURANTE EJEMPLO', margenIzq, 8, {
      negrita: true,
      tamano: 15,
    });
    escribirTexto('TICKET');
    escribirTexto(`No. ticket: ${identificador}`);
    escribirTexto(`Fecha: ${ahora.toLocaleDateString('es-ES')}  Hora: ${ahora.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}`);
    escribirTexto(`Mesa(s): ${mesaTexto}`);
    if (cuenta?.fechaPago) {
      escribirTexto(`Fecha de pago: ${this.formatearFecha(cuenta.fechaPago)}`);
    }
    escribirTexto(`Metodo de pago: ${this.obtenerMetodoPagoDetalle()}`);
    y += 4;
    pdf.line(margenIzq, y, margenDer, y);
    y += 9;
    escribirTexto('Productos', margenIzq, 8, { negrita: true, tamano: 13 });
    for (const item of this.detalleAgrupado()) {
      escribirImporte(`${item.cantidad}x ${item.nombre}`, item.subtotal);
    }
    y += 4;
    pdf.line(margenIzq, y, margenDer, y);
    y += 9;
    pdf.setFont('helvetica', 'bold');
    pdf.setFontSize(13);
    pdf.text('TOTAL', margenIzq, y);
    pdf.text(`${this.totalDetalle().toFixed(2)} €`, margenDer, y, { align: 'right' });
    y += 12;
    const pagos = this.pagosDetalle();
    if (pagos.length > 0) {
      escribirTexto('Pagos', margenIzq, 8, { negrita: true, tamano: 13 });
      for (const pago of pagos) {
        escribirTexto(
          `Pago ${pago.numero} - Metodo: ${pago.metodoPago}${
            pago.fechaPago ? ` - ${this.formatearFecha(pago.fechaPago)}` : ''
          }`,
          margenIzq,
          7,
          { negrita: true },
        );
        for (const item of pago.items) {
          escribirImporte(`  ${item.cantidad}x ${item.nombre}`, item.subtotal);
        }
        escribirImporte('  Total pago', pago.total);
        y += 4;
      }
    }
    y += 4;
    pdf.setFont('helvetica', 'normal');
    pdf.setFontSize(10);
    pdf.text('Gracias por su visita', 105, y, { align: 'center' });
    return pdf;
  }
  private obtenerNombreArchivoTique(): string {
    const mesa = this.obtenerMesaDetalle();
    const fecha = new Date().toISOString().slice(0, 10);
    return `ticket-mesa-${mesa}-${fecha}.pdf`;
  }
  private esCorreoValido(correo: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo);
  }

  cerrarDetalle(): void {
    this.mostrarDetalle.set(false);
    this.cargandoDetalle.set(false);
    this.cuentaDetalle.set(null);
    this.ordenesDetalle.set([]);
    this.totalDetalle.set(0);
    this.cerrarModalCorreo();
    if (this.toastTimeoutRef) {
      window.clearTimeout(this.toastTimeoutRef);
    }
    this.showToast.set(false);
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
    if (mesas.length === 0) {
      return '-';
    }

    return mesas.map((mesa) => mesa.id).join(', ');
  }

  obtenerMesaResumen(cuenta: CuentaPagadaResumenResponse): string {
    const mesas = cuenta.mesas ?? [];
    if (mesas.length > 0) {
      return mesas.map((mesa) => mesa.id).join(', ');
    }

    return cuenta.mesa || '-';
  }

  obtenerMetodoPagoDetalle(): string {
    return this.cuentaDetalle()?.metodoPago ?? '-';
  }

  obtenerResumenEstado(estados: string[]): string {
    const estadosNormalizados = estados.map((estado) =>
      estado === 'Preparación' || estado === 'Preparacion'
        ? 'En preparación'
        : estado,
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

  private agruparOrdenesPorPago(
    ordenes: OrdenCuentaResponse[],
  ): PagoDetalleAgrupado[] {
    const mapa = new Map<string, OrdenCuentaResponse[]>();

    for (const orden of ordenes) {
      const fechaPago = orden.fechaPago ?? 'SIN_FECHA';
      const metodoPago = orden.metodoPago ?? 'SIN_METODO';
      const clave = `${fechaPago}-${metodoPago}`;

      if (!mapa.has(clave)) {
        mapa.set(clave, []);
      }

      mapa.get(clave)!.push(orden);
    }

    return Array.from(mapa.entries())
      .map(([clave, ordenesPago], index) => {
        const fechaPago = ordenesPago[0].fechaPago ?? '';
        const metodoPago = ordenesPago[0].metodoPago ?? '-';

        return {
          clave,
          numero: index + 1,
          fechaPago,
          metodoPago,
          total: ordenesPago.reduce(
            (acc, orden) => acc + Number(orden.precio ?? 0),
            0,
          ),
          items: this.agruparOrdenes(ordenesPago),
        };
      })
      .sort((a, b) => {
        if (!a.fechaPago) return 1;
        if (!b.fechaPago) return -1;

        return a.fechaPago.localeCompare(b.fechaPago);
      })
      .map((pago, index) => ({
        ...pago,
        numero: index + 1,
      }));
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
}

