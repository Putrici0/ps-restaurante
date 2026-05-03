import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, timeout } from 'rxjs';
import { Notificacion } from '../models/notificacion.model';

@Injectable({
  providedIn: 'root',
})
export class NotificacionesApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `http://${window.location.hostname}:7070`;
  private readonly requestTimeoutMs = 8000;

  obtenerPendientes(): Observable<Notificacion[]> {
    return this.http
      .get<Notificacion[]>(`${this.apiUrl}/notificaciones/pendientes`)
      .pipe(timeout(this.requestTimeoutMs));
  }

  marcarEnCurso(
    id: string,
    camareroUid: string | null,
    camareroNombre: string,
  ): Observable<Notificacion> {
    return this.http
      .post<Notificacion>(`${this.apiUrl}/notificaciones/${id}/en-curso`, {
        camareroUid,
        camareroNombre,
      })
      .pipe(timeout(this.requestTimeoutMs));
  }

  marcarComoLeida(id: string): Observable<Notificacion> {
    return this.http
      .post<Notificacion>(`${this.apiUrl}/notificaciones/${id}/leida`, {})
      .pipe(timeout(this.requestTimeoutMs));
  }

  desasignarYReenviar(id: string): Observable<Notificacion> {
    return this.http
      .post<Notificacion>(`${this.apiUrl}/notificaciones/${id}/desasignar`, {})
      .pipe(timeout(this.requestTimeoutMs));
  }
}
