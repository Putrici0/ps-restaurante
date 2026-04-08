import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type EstadoOrdenBackend = 'Pendiente' | 'Preparación' | 'Listo';
export type CategoriaPlatoBackend = 'Bebida' | 'Entrante' | 'Principal' | 'Postre';

export interface OrdenCocinaResponse {
  id: string;
  precio: number;
  ordenEstado: EstadoOrdenBackend;
  fecha: string;
  detalles: string;
  pedido: {
    id: string;
    pedidoEstado: string;
    fechaPedido: string;
    cuenta?: {
      id: string;
      payed?: boolean;
      mesas?: Array<{
        id: string | number;
        capacidad?: number;
      }>;
    };
  };
  plato: {
    id: string;
    nombre: string;
    categoria: CategoriaPlatoBackend;
    descripcion: string;
    imagen: string;
  };
}

@Injectable({
  providedIn: 'root',
})
export class OrdenesApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:7070';

  obtenerPendientesCocina(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(
      `${this.apiUrl}/ordenes/cocina/pendientes`,
    );
  }

  obtenerEnPreparacionCocina(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(
      `${this.apiUrl}/ordenes/cocina/en-preparacion`,
    );
  }

  obtenerListasCocina(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(
      `${this.apiUrl}/ordenes/cocina/listas`,
    );
  }

  obtenerPendientesBarra(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(
      `${this.apiUrl}/ordenes/barra/pendientes`,
    );
  }

  obtenerEnPreparacionBarra(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(
      `${this.apiUrl}/ordenes/barra/en-preparacion`,
    );
  }

  obtenerListasBarra(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(
      `${this.apiUrl}/ordenes/barra/listas`,
    );
  }

  marcarPendiente(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(
      `${this.apiUrl}/ordenes/${ordenId}/pendiente`,
      {},
    );
  }

  marcarEnPreparacion(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(
      `${this.apiUrl}/ordenes/${ordenId}/en-preparacion`,
      {},
    );
  }

  marcarLista(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(
      `${this.apiUrl}/ordenes/${ordenId}/lista`,
      {},
    );
  }
}
