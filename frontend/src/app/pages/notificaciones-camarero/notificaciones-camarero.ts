import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { catchError, of, take } from 'rxjs';
import { Notificacion } from '../../models/notificacion.model';
import { CamareroAuthService } from '../../services/camarero-auth.service';
import { NotificacionesApiService } from '../../services/notificaciones-api.service';

@Component({
  selector: 'app-notificaciones-camarero',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notificaciones-camarero.html',
  styleUrl: './notificaciones-camarero.css',
})
export class NotificacionesCamarero implements OnInit, OnDestroy {
  private readonly notificacionesApi = inject(NotificacionesApiService);
  private readonly camareroAuth = inject(CamareroAuthService);
  private intervaloRefresco?: number;
  private intervaloReloj?: number;
  private readonly pollingMs = 4000;
  private readonly silencioStorageKey = 'waiterNotificationsMutedUntil';
  private readonly avisosActivadosStorageKey = 'waiterNotificationsEnabled';
  private readonly notificacionesConAviso = new Set<string>();

  readonly cargando = signal(true);
  readonly actualizando = signal(false);
  readonly asignandoNotificacionId = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly notificaciones = signal<Notificacion[]>([]);
  readonly toast = signal<Notificacion | null>(null);
  readonly ahora = signal(Date.now());
  readonly avisosActivados = signal(false);
  readonly silenciadoHasta = signal(0);
  readonly camareroUidActual = signal<string | null>(null);

  readonly notificacionesRecoger = computed(() =>
    this.notificaciones()
      .filter((notificacion) => notificacion.tipo === 'Recoger')
      .sort((a, b) => {
        const prioridadA = this.esAsignadaAlCamareroActual(a) ? 1 : 0;
        const prioridadB = this.esAsignadaAlCamareroActual(b) ? 1 : 0;

        if (prioridadA !== prioridadB) {
          return prioridadB - prioridadA;
        }

        return this.fechaMs(b.fecha) - this.fechaMs(a.fecha);
      }),
  );

  readonly totalPendientes = computed(() => this.notificacionesRecoger().length);

  readonly estaSilenciado = computed(() => this.silenciadoHasta() > this.ahora());

  readonly textoSilencio = computed(() => {
    if (!this.estaSilenciado()) {
      return 'Avisos activos';
    }

    const restanteMs = this.silenciadoHasta() - this.ahora();
    const minutos = Math.floor(restanteMs / 60000);
    const segundos = Math.floor((restanteMs % 60000) / 1000);

    return `Silenciado ${minutos}:${segundos.toString().padStart(2, '0')}`;
  });

  ngOnInit(): void {
    this.restaurarPreferencias();
    this.cargarPerfilCamareroActual();
    this.cargarNotificaciones(true, false);

    this.intervaloRefresco = window.setInterval(() => {
      this.cargarNotificaciones(false, true);
    }, this.pollingMs);

    this.intervaloReloj = window.setInterval(() => {
      this.ahora.set(Date.now());
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.intervaloRefresco) {
      window.clearInterval(this.intervaloRefresco);
    }

    if (this.intervaloReloj) {
      window.clearInterval(this.intervaloReloj);
    }
  }

  activarAvisos(): void {
    this.avisosActivados.set(true);
    localStorage.setItem(this.avisosActivadosStorageKey, 'true');
    this.reproducirSonido();
    this.vibrarSuave();
  }

  silenciarCincoMinutos(): void {
    const hasta = Date.now() + 5 * 60 * 1000;
    this.silenciadoHasta.set(hasta);
    localStorage.setItem(this.silencioStorageKey, String(hasta));
  }

  reactivarAvisos(): void {
    this.silenciadoHasta.set(0);
    localStorage.removeItem(this.silencioStorageKey);
  }

  recargar(): void {
    this.cargarNotificaciones(true, false);
  }

  async marcarEnCurso(notificacion: Notificacion): Promise<void> {
    if (this.actualizando() || this.asignandoNotificacionId()) {
      return;
    }

    this.actualizando.set(true);
    this.asignandoNotificacionId.set(notificacion.id);
    this.error.set(null);

    try {
      const perfil = await this.camareroAuth.obtenerPerfilCamareroActual();

      this.notificacionesApi
        .marcarEnCurso(notificacion.id, perfil.uid, perfil.nombreCompleto)
        .pipe(take(1))
        .subscribe({
          next: (actualizada) => {
            this.notificaciones.update((actuales) =>
              actuales.map((item) => item.id === actualizada.id ? actualizada : item),
            );

            if (this.toast()?.id === actualizada.id) {
              this.toast.set(actualizada);
            }

            this.asignandoNotificacionId.set(null);
            this.actualizando.set(false);
          },
          error: (err) => {
            console.error(err);
            this.error.set('No se ha podido marcar la notificación en curso.');
            this.asignandoNotificacionId.set(null);
            this.actualizando.set(false);
          },
        });
    } catch (err) {
      console.error(err);
      this.error.set('No se ha podido identificar al camarero.');
      this.asignandoNotificacionId.set(null);
      this.actualizando.set(false);
    }
  }

  desasignarYReenviar(notificacion: Notificacion): void {
    if (this.actualizando() || this.asignandoNotificacionId()) {
      return;
    }

    this.actualizando.set(true);
    this.asignandoNotificacionId.set(notificacion.id);
    this.error.set(null);

    this.notificacionesApi
      .desasignarYReenviar(notificacion.id)
      .pipe(take(1))
      .subscribe({
        next: (actualizada) => {
          this.notificacionesConAviso.delete(actualizada.id);
          this.notificaciones.update((actuales) =>
            actuales.map((item) => item.id === actualizada.id ? actualizada : item),
          );

          if (this.toast()?.id === actualizada.id) {
            this.toast.set(actualizada);
          }

          this.asignandoNotificacionId.set(null);
          this.actualizando.set(false);
        },
        error: (err) => {
          console.error(err);
          this.error.set('No se ha podido desasignar la notificación.');
          this.asignandoNotificacionId.set(null);
          this.actualizando.set(false);
        },
      });
  }

  mesaTexto(notificacion: Notificacion): string {
    const mesas = notificacion.cuenta?.mesas ?? [];

    if (mesas.length === 1) {
      return `Mesa ${mesas[0].id}`;
    }

    if (mesas.length > 1) {
      return `Mesas ${mesas.map((mesa) => mesa.id).join(', ')}`;
    }

    if (notificacion.cuenta?.id) {
      return `Cuenta ${notificacion.cuenta.id}`;
    }

    return 'Mesa sin identificar';
  }

  tituloNotificacion(notificacion: Notificacion): string {
    if (notificacion.tipo === 'Recoger') {
      return notificacion.enCurso ? 'Pedido en curso' : 'Pedido listo para recoger';
    }

    return 'Solicitud de atención';
  }

  descripcionNotificacion(notificacion: Notificacion): string {
    if (notificacion.tipo === 'Recoger') {
      const item = this.itemTexto(notificacion);
      return item
        ? `${this.mesaTexto(notificacion)} tiene listo: ${item}.`
        : `${this.mesaTexto(notificacion)} tiene pedidos listos.`;
    }

    return `${this.mesaTexto(notificacion)} solicita atención.`;
  }

  textoAsignacion(notificacion: Notificacion): string {
    if (!notificacion.enCurso) {
      return 'Pendiente de asignar';
    }

    return `En curso por ${notificacion.camareroNombre || 'camarero'}`;
  }

  itemTexto(notificacion: Notificacion): string {
    const nombre = notificacion.nombreItem?.trim();

    if (!nombre) {
      return '';
    }

    const categoria = notificacion.categoriaItem?.trim();

    if (!categoria) {
      return nombre;
    }

    const tipo = categoria.toLowerCase() === 'bebida' ? 'Bebida' : 'Plato';

    return `${tipo}: ${nombre}`;
  }

  tiempoTranscurrido(fechaIso: string): string {
    const diffMs = Math.max(0, this.ahora() - this.fechaMs(fechaIso));
    const diffMin = Math.floor(diffMs / 60000);

    if (diffMin < 1) {
      return 'Ahora';
    }

    if (diffMin < 60) {
      return `Hace ${diffMin} min`;
    }

    const horas = Math.floor(diffMin / 60);
    const minutos = diffMin % 60;

    if (minutos === 0) {
      return `Hace ${horas} h`;
    }

    return `Hace ${horas} h ${minutos} min`;
  }

  fechaLarga(fechaIso: string): string {
    const fecha = new Date(fechaIso);

    if (Number.isNaN(fecha.getTime())) {
      return fechaIso;
    }

    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'short',
      timeStyle: 'medium',
    }).format(fecha);
  }

  private cargarNotificaciones(mostrarLoading: boolean, avisarNuevas: boolean): void {
    if (mostrarLoading) {
      this.cargando.set(true);
    }

    this.notificacionesApi
      .obtenerPendientes()
      .pipe(
        catchError((err) => {
          console.error(err);
          this.error.set('No se han podido cargar las notificaciones.');
          return of([] as Notificacion[]);
        }),
        take(1),
      )
      .subscribe({
        next: (notificaciones) => {
          const pendientesRecoger = notificaciones.filter(
            (notificacion) => !notificacion.leida && notificacion.tipo === 'Recoger',
          );

          this.notificaciones.set(pendientesRecoger);
          this.cargando.set(false);

          if (avisarNuevas) {
            this.procesarAvisosNuevos(pendientesRecoger);
          } else {
            pendientesRecoger.forEach((notificacion) =>
              this.notificacionesConAviso.add(notificacion.id),
            );
          }
        },
      });
  }

  private procesarAvisosNuevos(notificaciones: Notificacion[]): void {
    const nuevas = notificaciones.filter(
      (notificacion) => !this.notificacionesConAviso.has(notificacion.id),
    );

    notificaciones.forEach((notificacion) => {
      this.notificacionesConAviso.add(notificacion.id);
    });

    if (nuevas.length === 0) {
      return;
    }

    const ultima = nuevas[0];

    this.toast.set(ultima);

    window.setTimeout(() => {
      if (this.toast()?.id === ultima.id) {
        this.toast.set(null);
      }
    }, 5000);

    if (!this.avisosActivados() || this.estaSilenciado()) {
      return;
    }

    this.reproducirSonido();
    this.vibrarSuave();
  }

  private reproducirSonido(): void {
    try {
      const AudioContextClass =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;

      const audioContext = new AudioContextClass();
      const oscillator = audioContext.createOscillator();
      const gain = audioContext.createGain();

      oscillator.type = 'sine';
      oscillator.frequency.setValueAtTime(880, audioContext.currentTime);

      gain.gain.setValueAtTime(0.001, audioContext.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.18, audioContext.currentTime + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + 0.35);

      oscillator.connect(gain);
      gain.connect(audioContext.destination);

      oscillator.start();
      oscillator.stop(audioContext.currentTime + 0.35);
    } catch (err) {
      console.warn('No se ha podido reproducir el sonido de aviso.', err);
    }
  }

  private vibrarSuave(): void {
    if ('vibrate' in navigator) {
      navigator.vibrate([160, 80, 160]);
    }
  }

  private restaurarPreferencias(): void {
    const avisosActivados = localStorage.getItem(this.avisosActivadosStorageKey) === 'true';
    this.avisosActivados.set(avisosActivados);

    const silenciadoHasta = Number(localStorage.getItem(this.silencioStorageKey) ?? 0);

    if (!Number.isNaN(silenciadoHasta) && silenciadoHasta > Date.now()) {
      this.silenciadoHasta.set(silenciadoHasta);
    } else {
      this.silenciadoHasta.set(0);
      localStorage.removeItem(this.silencioStorageKey);
    }
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

  private esAsignadaAlCamareroActual(notificacion: Notificacion): boolean {
    const uidActual = this.camareroUidActual();
    return !!uidActual && !!notificacion.enCurso && notificacion.camareroUid === uidActual;
  }

  private fechaMs(fechaIso: string | null | undefined): number {
    if (!fechaIso) {
      return 0;
    }

    const fecha = new Date(fechaIso).getTime();
    return Number.isNaN(fecha) ? 0 : fecha;
  }
}
