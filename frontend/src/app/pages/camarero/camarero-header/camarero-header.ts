import { CommonModule } from '@angular/common';
import { Component, Input, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { catchError, of, take } from 'rxjs';
import { Notificacion } from '../../../models/notificacion.model';
import { CamareroAuthService } from '../../../services/camarero-auth.service';
import { NotificacionesApiService } from '../../../services/notificaciones-api.service';

type ModoAviso = 'sonido' | 'vibracion' | 'silencio';

@Component({
  selector: 'app-camarero-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './camarero-header.html',
  styleUrl: './camarero-header.css',
})
export class CamareroHeader implements OnInit, OnDestroy {
  @Input() titulo = '';
  @Input() pendientesCount = 0;

  private readonly notificacionesApi = inject(NotificacionesApiService);
  private readonly camareroAuth = inject(CamareroAuthService);
  private readonly pollingMs = 4000;
  private readonly modoAvisoStorageKey = 'camarero-notificaciones-modo-aviso';
  private readonly notificacionesYaAvisadas = new Set<string>();

  private intervaloNotificaciones?: number;
  private intervaloReloj?: number;

  menuAbierto = false;

  readonly panelNotificacionesAbierto = signal(false);
  readonly notificaciones = signal<Notificacion[]>([]);
  readonly errorNotificaciones = signal<string | null>(null);
  readonly modoAviso = signal<ModoAviso>('sonido');
  readonly ahora = signal(Date.now());
  readonly asignandoNotificacionId = signal<string | null>(null);

  readonly notificacionesRecoger = computed(() =>
    this.notificaciones()
      .filter((notificacion) => !notificacion.leida && notificacion.tipo === 'Recoger')
      .sort((a, b) => this.fechaMs(b.fecha) - this.fechaMs(a.fecha)),
  );

  readonly totalNotificaciones = computed(() => this.notificacionesRecoger().length);

  readonly textoModoAviso = computed(() => {
    switch (this.modoAviso()) {
      case 'sonido':
        return '🔊';
      case 'vibracion':
        return '📳';
      case 'silencio':
        return '🔕';
    }
  });

  readonly textoModoAvisoLargo = computed(() => {
    switch (this.modoAviso()) {
      case 'sonido':
        return 'Sonido y vibración';
      case 'vibracion':
        return 'Solo vibración';
      case 'silencio':
        return 'Silencio';
    }
  });

  ngOnInit(): void {
    this.restaurarPreferencias();
    this.cargarNotificaciones(false);

    this.intervaloNotificaciones = window.setInterval(() => {
      this.cargarNotificaciones(true);
    }, this.pollingMs);

    this.intervaloReloj = window.setInterval(() => {
      this.ahora.set(Date.now());
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.intervaloNotificaciones) {
      window.clearInterval(this.intervaloNotificaciones);
    }

    if (this.intervaloReloj) {
      window.clearInterval(this.intervaloReloj);
    }
  }

  toggleMenu(): void {
    this.menuAbierto = !this.menuAbierto;

    if (this.menuAbierto) {
      this.panelNotificacionesAbierto.set(false);
    }
  }

  togglePanelNotificaciones(): void {
    this.panelNotificacionesAbierto.update((abierto) => !abierto);
  }

  cerrarPanelNotificaciones(): void {
    this.panelNotificacionesAbierto.set(false);
  }

  cambiarModoAviso(): void {
    const modoActual = this.modoAviso();

    const nuevoModo: ModoAviso =
      modoActual === 'sonido'
        ? 'vibracion'
        : modoActual === 'vibracion'
          ? 'silencio'
          : 'sonido';

    this.modoAviso.set(nuevoModo);
    localStorage.setItem(this.modoAvisoStorageKey, nuevoModo);

    if (nuevoModo === 'sonido') {
      this.reproducirSonido();
      this.vibrar();
    }

    if (nuevoModo === 'vibracion') {
      this.vibrar();
    }
  }

  async marcarEnCurso(notificacion: Notificacion): Promise<void> {
    if (this.asignandoNotificacionId()) {
      return;
    }

    this.asignandoNotificacionId.set(notificacion.id);
    this.errorNotificaciones.set(null);

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
            this.asignandoNotificacionId.set(null);
          },
          error: (err) => {
            console.error(err);
            this.errorNotificaciones.set('No se ha podido marcar la notificación en curso.');
            this.asignandoNotificacionId.set(null);
          },
        });
    } catch (err) {
      console.error(err);
      this.errorNotificaciones.set('No se ha podido identificar al camarero.');
      this.asignandoNotificacionId.set(null);
    }
  }

  marcarTodasEnCurso(): void {
    const pendientes = this.notificacionesRecoger().filter((notificacion) => !notificacion.enCurso);

    pendientes.forEach((notificacion) => {
      this.marcarEnCurso(notificacion);
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

  descripcionNotificacion(notificacion: Notificacion): string {
    const item = this.itemTexto(notificacion);

    return item
      ? `Listo para recoger: ${item}.`
      : 'Pedido listo para recoger';
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
    return `Hace ${horas} h`;
  }

  private cargarNotificaciones(avisarNuevas: boolean): void {
    this.notificacionesApi
      .obtenerPendientes()
      .pipe(
        catchError((err) => {
          console.error(err);
          this.errorNotificaciones.set('No se han podido cargar las notificaciones.');
          return of([] as Notificacion[]);
        }),
        take(1),
      )
      .subscribe((notificaciones) => {
        const recoger = notificaciones.filter(
          (notificacion) => !notificacion.leida && notificacion.tipo === 'Recoger',
        );

        this.notificaciones.set(recoger);
        this.errorNotificaciones.set(null);

        if (avisarNuevas) {
          this.procesarNuevas(recoger);
        } else {
          recoger.forEach((notificacion) =>
            this.notificacionesYaAvisadas.add(notificacion.id),
          );
        }
      });
  }

  private procesarNuevas(notificaciones: Notificacion[]): void {
    const nuevas = notificaciones.filter(
      (notificacion) => !this.notificacionesYaAvisadas.has(notificacion.id),
    );

    notificaciones.forEach((notificacion) => {
      this.notificacionesYaAvisadas.add(notificacion.id);
    });

    if (nuevas.length === 0) {
      return;
    }

    if (this.modoAviso() === 'silencio') {
      return;
    }

    this.panelNotificacionesAbierto.set(true);

    if (this.modoAviso() === 'sonido') {
      this.reproducirSonido();
      this.vibrar();
      return;
    }

    if (this.modoAviso() === 'vibracion') {
      this.vibrar();
    }
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
      console.warn('No se ha podido reproducir el sonido.', err);
    }
  }

  private vibrar(): void {
    if ('vibrate' in navigator) {
      navigator.vibrate([160, 80, 160]);
    }
  }

  private restaurarPreferencias(): void {
    const modoGuardado = localStorage.getItem(this.modoAvisoStorageKey);

    if (
      modoGuardado === 'sonido' ||
      modoGuardado === 'vibracion' ||
      modoGuardado === 'silencio'
    ) {
      this.modoAviso.set(modoGuardado);
    } else {
      this.modoAviso.set('sonido');
      localStorage.setItem(this.modoAvisoStorageKey, 'sonido');
    }
  }

  private fechaMs(fechaIso: string | null | undefined): number {
    if (!fechaIso) {
      return 0;
    }

    const fecha = new Date(fechaIso).getTime();
    return Number.isNaN(fecha) ? 0 : fecha;
  }
}
