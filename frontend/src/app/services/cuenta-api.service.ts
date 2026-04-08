import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

export interface CuentaActivaResponse {
  id: string;
  payed: boolean;
  fechaCreacion: string;
  fechaPago?: string | null;
  password: string;
  mesas: Array<{
    id: string;
    capacidad?: number;
  }>;
}

export interface OrdenCuentaResponse {
  id: string;
  precio: number;
  ordenEstado: 'Pendiente' | 'Preparación' | 'Preparacion' | 'Listo' | 'Listo para servir' | 'Entregado';
  fecha: string;
  detalles: string;
  pedido: {
    id: string;
  };
  plato: {
    id: string;
    nombre: string;
    categoria: string;
    descripcion: string;
    imagen: string;
    precio?: number;
  };
}

export interface ImporteCuentaResponse {
  cuentaId: string;
  importe: number;
}

export interface EstadoCuentaResponse {
  cuentaId: string;
  saldada: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class CuentaApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:7070';

  obtenerCuentaActivaDeMesa(mesaId: string): Observable<CuentaActivaResponse | null> {
    return this.http
      .get<CuentaActivaResponse>(`${this.apiUrl}/mesas/${mesaId}/cuenta-activa`)
      .pipe(
        catchError((error) => {
          if (error?.status === 404) {
            return of(null);
          }
          return throwError(() => error);
        }),
      );
  }

  obtenerOrdenesDeCuenta(cuentaId: string): Observable<OrdenCuentaResponse[]> {
    return this.http.get<OrdenCuentaResponse[]>(`${this.apiUrl}/cuentas/${cuentaId}/ordenes`);
  }

  obtenerPendienteCuenta(cuentaId: string): Observable<ImporteCuentaResponse> {
    return this.http.get<ImporteCuentaResponse>(`${this.apiUrl}/cuentas/${cuentaId}/pendiente`);
  }

  obtenerEstadoSaldada(cuentaId: string): Observable<EstadoCuentaResponse> {
    return this.http.get<EstadoCuentaResponse>(`${this.apiUrl}/cuentas/${cuentaId}/saldada`);
  }
}
