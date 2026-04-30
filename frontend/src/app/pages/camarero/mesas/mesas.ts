import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Mesa, ZonaMesa } from '../../../models/mesa.model';
import { CuentaApiService, OrdenCuentaResponse } from '../../../services/cuenta-api.service';
import { MesasApiService } from '../../../services/mesas-api.service';
import { CamareroHeader } from '../camarero-header/camarero-header';
import { MesaCardComponent } from '../../../shared/mesa-card/mesa-card';

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
  imports: [CommonModule, CamareroHeader, MesaCardComponent],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class MesasCamarero {
  private readonly mesasApi = inject(MesasApiService);
  private readonly cuentaApi = inject(CuentaApiService);
  private readonly router = inject(Router);

  readonly zonaActiva = signal<ZonaMesa>('interior');
  readonly mesaSeleccionada = signal<Mesa | null>(null);
  readonly mesas = signal<Mesa[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly accionMesaId = signal<string | null>(null);

  // --- ESTADO DE COBRO ---
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
    this.router.navigate(['/camarero/menu', mesaId]);
  }

  mostrarContrasena(mesaId: string): void {
    alert(`Contraseña de la mesa M${mesaId}: 1234 (Simulado)`);
  }

  verCuenta(mesa: Mesa): void {
    if (!mesa.cuentaActiva) {
      this.error.set('La mesa no tiene una cuenta activa asociada.');
      return;
    }
    this.cerrarModal();
    this.router.navigate(['/camarero/cuenta', mesa.id]);
  }

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
        this.error.set('Error al ocupar mesa.');
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
        this.error.set('Error al cargar mesas.');
      },
    });
  }
}
