import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, ActivatedRoute } from '@angular/router';
import {Subscription, catchError, of, take} from 'rxjs';
import { CuentaApiService } from '../../services/cuenta-api.service';
import { NotificacionesApiService } from '../../services/notificaciones-api.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './header.html',
  styleUrls: ['./header.css'],
})
export class Header implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly cuentaApiService = inject(CuentaApiService);
  private readonly notificacionesApi = inject(NotificacionesApiService);

  private pollingTimer?: number;
  private routeSub?: Subscription;

  readonly tableId = signal<string>('1');
  readonly menuAbierto = signal(false);
  readonly cuentaId = signal<string | null>(null);
  readonly solicitarAtencionCargando = signal(false);
  readonly toast = signal<{ mensaje: string; tipo: 'success' | 'error' } | null>(null);
  readonly atencionActiva = signal<{
    id: string;
    enCurso: boolean;
    camareroNombre?: string | null;
    leida: boolean;
  } | null>(null);

  readonly estadoAtencionTexto = signal('');

  ngOnInit(): void {
    this.routeSub = this.route.params.subscribe(params => {
      const id = params['id'];
      if (id) {
        this.tableId.set(id);
        this.actualizarEstadoAtencion();
      }
    });

    this.iniciarPolling();
  }

  ngOnDestroy(): void {
    if (this.pollingTimer) {
      window.clearInterval(this.pollingTimer);
    }
    if (this.routeSub) {
      this.routeSub.unsubscribe();
    }
  }

  toggleMenu(): void {
    this.menuAbierto.update(v => !v);
    document.body.style.overflow = this.menuAbierto() ? 'hidden' : '';
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
    document.body.style.overflow = '';
  }

  solicitarAtencion(): void {
    const cuentaId = this.cuentaId();
    if (!cuentaId || this.solicitarAtencionCargando()) return;

    this.solicitarAtencionCargando.set(true);
    this.notificacionesApi.solicitarAtencion(cuentaId).pipe(take(1)).subscribe({
      next: (notificacion) => {
        this.atencionActiva.set({
          id: notificacion.id,
          enCurso: !!notificacion.enCurso,
          camareroNombre: notificacion.camareroNombre,
          leida: notificacion.leida,
        });
        this.solicitarAtencionCargando.set(false);
        this.mostrarToast('Solicitud enviada correctamente', 'success');
      },
      error: () => {
        this.solicitarAtencionCargando.set(false);
        this.mostrarToast('Error al enviar la solicitud. Inténtalo de nuevo.', 'error');
      },
    });
  }

  private mostrarToast(mensaje: string, tipo: 'success' | 'error'): void {
    this.toast.set({mensaje, tipo});
    window.setTimeout(() => {
      this.toast.set(null);
    }, 5000);
  }

  private iniciarPolling(): void {
    this.actualizarEstadoAtencion();
    this.pollingTimer = window.setInterval(() => {
      this.actualizarEstadoAtencion();
    }, 4000);
  }

  private actualizarEstadoAtencion(): void {
    const mesaId = this.tableId();
    if (!mesaId) return;

    this.cuentaApiService.obtenerCuentaActivaDeMesa(mesaId).pipe(take(1)).subscribe({
      next: (cuenta) => {
        if (cuenta?.id) {
          const cuentaIdPrevia = this.cuentaId();
          this.cuentaId.set(cuenta.id);

          if (!cuentaIdPrevia || cuentaIdPrevia !== cuenta.id) {
            this.atencionActiva.set(null);
          }

          this.notificacionesApi.obtenerAtencionActiva(cuenta.id).pipe(take(1)).subscribe({
            next: (notificacion) => {
              const previa = this.atencionActiva();

              if (notificacion) {
                this.atencionActiva.set({
                  id: notificacion.id,
                  enCurso: !!notificacion.enCurso,
                  camareroNombre: notificacion.camareroNombre,
                  leida: notificacion.leida,
                });
              } else {
                this.atencionActiva.set(null);
                if (previa && !previa.leida) {
                  this.mostrarToast('El camarero ha completado tu solicitud.', 'success');
                }
              }
            },
            error: () => {},
          });
        } else {
          this.cuentaId.set(null);
          this.atencionActiva.set(null);
        }
      },
      error: () => {},
    });
  }
}
