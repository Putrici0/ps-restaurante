import { CommonModule } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, forkJoin, take, timer } from 'rxjs';

import { Mesa, ZonaMesa } from '../../../models/mesa.model';
import { CuentaApiService } from '../../../services/cuenta-api.service';
import { MesasApiService } from '../../../services/mesas-api.service';
import {NotificacionesApiService} from '../../../services/notificaciones-api.service';
import { CamareroAuthService } from '../../../services/camarero-auth.service';
import { CamareroHeader } from '../camarero-header/camarero-header';
import {Notificacion} from '../../../models/notificacion.model';

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
  imports: [CommonModule, CamareroHeader],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class MesasCamarero implements OnDestroy {
  private readonly mesasApi = inject(MesasApiService);
  private readonly cuentaApi = inject(CuentaApiService);
  private readonly notificacionesApi = inject(NotificacionesApiService);
  private readonly camareroAuth = inject(CamareroAuthService);
  private readonly router = inject(Router);

  readonly zonaActiva = signal<ZonaMesa>('interior');
  readonly mesaSeleccionada = signal<Mesa | null>(null);
  readonly mesas = signal<Mesa[]>([]);
  readonly notificacionesAtencion = signal<Notificacion[]>([]);
  readonly asignacionesActivas = signal<Notificacion[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly accionMesaId = signal<string | null>(null);
  readonly accionAtencionMesaId = signal<string | null>(null);
  readonly camareroUidActual = signal<string | null>(null);

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
  readonly mostrarContrasenaModal = signal(false);
  readonly mostrarConfirmacionLiberar = signal(false);
  readonly mesaPendienteLiberarId = signal<string | null>(null);
  private pollingSub?: Subscription;
  private readonly pollingMs = 5000;

  readonly mesasFiltradas = computed(() => {
    const activas = this.notificacionesAtencion();
    const idsMesasConAtencion = new Set<string>();

    activas.forEach((n: Notificacion) => {
      if (n.enCurso && n.camareroUid && n.camareroUid !== this.camareroUidActual()) {
        return;
      }
      n.cuenta?.mesas?.forEach((m: any) => idsMesasConAtencion.add(m.id));
    });

    const mesasZona = this.mesas()
      .filter((mesa) => mesa.zona === this.zonaActiva())
      .sort((a, b) => this.compararMesaIds(a.id, b.id));

    const mesasAgrupadas = new Map<string, (Mesa & { tieneAtencion: boolean })>();

    for (const mesa of mesasZona) {
      const grupoMesaIds = this.normalizarGrupoMesaIds(mesa.grupoMesaIds);
      const clave = this.claveGrupo(grupoMesaIds);
      if (mesasAgrupadas.has(clave)) {
        continue;
      }

      const miembros = grupoMesaIds
        .map((id) => mesasZona.find((mesaZona) => mesaZona.id === id))
        .filter((value): value is Mesa => !!value)
        .sort((a, b) => this.compararMesaIds(a.id, b.id));

      const cuentaActiva = miembros.find((item) => item.cuentaActiva)?.cuentaActiva ?? null;
      const mesaPrincipalId = miembros[0]?.id ?? mesa.id;

      mesasAgrupadas.set(clave, {
        id: mesaPrincipalId,
        capacidad: miembros.reduce((acc, item) => acc + item.capacidad, 0),
        zona: mesa.zona,
        estado: cuentaActiva ? 'ocupada' : 'libre',
        cuentaActivaId: cuentaActiva?.id ?? null,
        cuentaActiva,
        grupoMesaIds,
        mesaPrincipalId,
        tieneAtencion: grupoMesaIds.some((id) => idsMesasConAtencion.has(id)),
      });
    }

    return Array.from(mesasAgrupadas.values()).sort((a, b) =>
      this.compararMesaIds(a.id, b.id),
    );
  });

  readonly misMesasAsignadas = computed(() =>
    this.mesasFiltradas().filter((mesa) => {
      const atencion = this.obtenerAsignacionResponsableDeMesa(mesa);
      return !!atencion?.enCurso && atencion.camareroUid === this.camareroUidActual();
    }),
  );
  readonly misMesasAsignadasTexto = computed(() =>
    this.misMesasAsignadas().map((mesa) => this.etiquetaMesa(mesa)).join(', '),
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
    this.cargarPerfilCamareroActual();
    this.recargarMesas();
    this.iniciarPolling();
  }

  ngOnDestroy(): void {
    this.pollingSub?.unsubscribe();
  }

  cambiarZona(nuevaZona: ZonaMesa): void {
    this.zonaActiva.set(nuevaZona);
    this.mesaSeleccionada.set(null);
  }

  abrirMesa(mesa: Mesa): void {
    const actual = this.mesaSeleccionada();
    if (actual && this.claveGrupo(actual.grupoMesaIds) === this.claveGrupo(mesa.grupoMesaIds)) {
      this.mesaSeleccionada.set(null);
      return;
    }
    this.mesaSeleccionada.set(mesa);
  }

  cerrarModal(): void {
    this.mesaSeleccionada.set(null);
    this.mostrarContrasenaModal.set(false);
  }

  hacerPedido(mesaId: string): void {
    this.router.navigate(['/camarero/menu', mesaId]);
  }

  toggleContrasena(): void {
    this.mostrarContrasenaModal.update(v => !v);
  }

  async asignarmeAtencionMesa(mesa: Mesa): Promise<void> {
    if (this.accionAtencionMesaId()) {
      return;
    }
    if (!mesa.cuentaActiva?.id) {
      this.error.set('La mesa debe tener una cuenta activa para asignar responsable.');
      return;
    }

    this.error.set(null);
    this.accionAtencionMesaId.set(mesa.id);

    try {
      const perfil = await this.camareroAuth.obtenerPerfilCamareroActual();
      this.notificacionesApi
        .asignarResponsableMesa(mesa.cuentaActiva.id, perfil.uid, perfil.nombreCompleto)
        .pipe(take(1))
        .subscribe({
          next: () => {
            forkJoin({
              notifs: this.notificacionesApi.obtenerActivas(),
              asignaciones: this.notificacionesApi.obtenerAsignacionesActivas(),
            }).pipe(take(1)).subscribe({
              next: ({ notifs, asignaciones }) => {
                this.notificacionesAtencion.set(notifs);
                this.asignacionesActivas.set(asignaciones);
                this.accionAtencionMesaId.set(null);
              },
              error: (err) => {
                console.error('Error refrescando atenciones:', err);
                this.accionAtencionMesaId.set(null);
              },
            });
          },
          error: (err) => {
            console.error('Error asignando responsable de mesa:', err);
            this.error.set('No se ha podido asignar el responsable de esta mesa.');
            this.accionAtencionMesaId.set(null);
          },
        });
    } catch (err) {
      console.error(err);
      this.error.set('No se ha podido identificar al camarero actual.');
      this.accionAtencionMesaId.set(null);
    }
  }

  desasignarAtencionMesa(mesa: Mesa): void {
    if (!mesa.cuentaActiva?.id || this.accionAtencionMesaId()) {
      return;
    }

    this.error.set(null);
    this.accionAtencionMesaId.set(mesa.id);

    this.notificacionesApi
      .liberarResponsableMesa(mesa.cuentaActiva.id)
      .pipe(take(1))
      .subscribe({
        next: () => {
          forkJoin({
            notifs: this.notificacionesApi.obtenerActivas(),
            asignaciones: this.notificacionesApi.obtenerAsignacionesActivas(),
          }).pipe(take(1)).subscribe({
            next: ({ notifs, asignaciones }) => {
              this.notificacionesAtencion.set(notifs);
              this.asignacionesActivas.set(asignaciones);
              this.accionAtencionMesaId.set(null);
            },
            error: (err) => {
              console.error('Error refrescando atenciones:', err);
              this.accionAtencionMesaId.set(null);
            },
          });
        },
        error: (err) => {
          console.error('Error desasignando atencion:', err);
          this.error.set('No se ha podido cancelar el responsable de la mesa.');
          this.accionAtencionMesaId.set(null);
        },
      });
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
      error: (err: any) => {
        console.error('Error ocupando mesa:', err);
        this.accionMesaId.set(null);
        this.error.set('Error al ocupar mesa.');
      },
    });
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
        this.cerrarModal();
      },
      error: (err: any) => {
        console.error('Error liberando mesa:', err);
        this.accionMesaId.set(null);
        this.error.set('Error al liberar mesa.');
      },
    });
  }

  private iniciarPolling(): void {
    this.pollingSub = timer(this.pollingMs, this.pollingMs).subscribe(() => {
      this.recargarMesas(undefined, false);
    });
  }

  private recargarMesas(mesaAReseleccionar?: string, mostrarLoading = true): void {
    if (mostrarLoading) {
      this.cargando.set(true);
    }
    this.error.set(null);

    forkJoin({
      notifs: this.notificacionesApi.obtenerActivas(),
      asignaciones: this.notificacionesApi.obtenerAsignacionesActivas(),
    }).subscribe({
      next: ({ notifs, asignaciones }) => {
        this.notificacionesAtencion.set(notifs);
        this.asignacionesActivas.set(asignaciones);
      },
      error: (err: any) => console.error('Error cargando estado de atenciones:', err),
    });

    this.mesasApi.cargarMesasParaVista().subscribe({
      next: (mesas: Mesa[]) => {
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

        const mesaRefrescada = this.buscarMesaLogicaPorId(mesas, seleccionActual.id);
        this.mesaSeleccionada.set(mesaRefrescada);
      },
      error: (err: any) => {
        console.error('Error recargando mesas:', err);
        this.cargando.set(false);
        this.error.set('Error al cargar mesas.');
      },
    });
  }

  etiquetaMesa(mesa: Mesa): string {
    const ids = this.normalizarGrupoMesaIds(mesa.grupoMesaIds);
    if (ids.length <= 1) {
      return `M${mesa.id}`;
    }

    return ids.map((id) => `M${id}`).join(' + ');
  }

  esMesaAgrupada(mesa: Mesa): boolean {
    return this.normalizarGrupoMesaIds(mesa.grupoMesaIds).length > 1;
  }

  responsableMesaTexto(mesa: Mesa): string {
    const atencion = this.obtenerAsignacionResponsableDeMesa(mesa);
    if (!atencion) {
      return 'Sin responsable asignado';
    }
    if (!atencion.enCurso) {
      return 'Sin responsable asignado';
    }
    const nombre = atencion.camareroNombre?.trim() || 'Camarero';
    return `Responsable: ${nombre}`;
  }

  responsableMesaCorto(mesa: Mesa): string {
    const atencion = this.obtenerAsignacionResponsableDeMesa(mesa);
    if (!atencion) {
      return 'Sin responsable';
    }
    if (!atencion.enCurso) {
      return 'Sin responsable';
    }
    return atencion.camareroNombre?.trim() || 'Responsable asignado';
  }

  puedeAsignarmeMesa(mesa: Mesa): boolean {
    const atencion = this.obtenerAsignacionResponsableDeMesa(mesa);
    if (!mesa.cuentaActiva?.id || !!this.accionAtencionMesaId()) {
      return false;
    }
    return !atencion || !atencion.enCurso;
  }

  puedeDesasignarMesa(mesa: Mesa): boolean {
    const atencion = this.obtenerAsignacionResponsableDeMesa(mesa);
    return !!atencion && !!atencion.enCurso && !this.accionAtencionMesaId();
  }

  private obtenerAtencionActivaDeMesa(mesa: Mesa): Notificacion | null {
    const idsGrupo = new Set(this.normalizarGrupoMesaIds(mesa.grupoMesaIds));
    const notificacion = this.notificacionesAtencion().find((n) => {
      if (n.tipo !== 'Atencion' || n.leida) {
        return false;
      }
      const mesasNotificacion = n.cuenta?.mesas ?? [];
      return mesasNotificacion.some((m) => idsGrupo.has(String(m.id)));
    });
    return notificacion ?? null;
  }

  private obtenerAsignacionResponsableDeMesa(mesa: Mesa): Notificacion | null {
    const idsGrupo = new Set(this.normalizarGrupoMesaIds(mesa.grupoMesaIds));
    const asignacion = this.asignacionesActivas().find((n) => {
      const mesasNotificacion = n.cuenta?.mesas ?? [];
      return mesasNotificacion.some((m) => idsGrupo.has(String(m.id)));
    });
    return asignacion ?? null;
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
    return Array.from(new Set(grupoMesaIds)).sort((a, b) => this.compararMesaIds(a, b));
  }

  private claveGrupo(grupoMesaIds: string[]): string {
    return this.normalizarGrupoMesaIds(grupoMesaIds).join('|');
  }

  private compararMesaIds(left: string, right: string): number {
    return Number(left) - Number(right);
  }

  private async cargarPerfilCamareroActual(): Promise<void> {
    try {
      const perfil = await this.camareroAuth.obtenerPerfilCamareroActual();
      this.camareroUidActual.set(perfil.uid);
    } catch (err) {
      console.warn('No se ha podido cargar el perfil del camarero actual.', err);
      this.camareroUidActual.set(null);
    }
  }
}
