import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
export type EstadoOrdenBackend =
  | 'Pendiente'
  | 'Preparación'
  | 'Listo'
  | 'Entregado';

export type CategoriaPlatoBackend =
  | 'Bebida'
  | 'Entrante'
  | 'Principal'
  | 'Postre';

export interface OrdenCocinaResponse {
  id: string;
  precio: number;
  ordenEstado: EstadoOrdenBackend;
  fecha: string;
  detalles: string;
  pedido?: {
    id: string;
    pedidoEstado: string;
    fechaPedido: string;
    cuenta?: {
      id: string;
      payed?: boolean;
      paid?: boolean;
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
  private readonly apiUrl = `http://${window.location.hostname}:7070`;

  // 🚀 CARGA ÚNICA BARRA (Trae todo en una sola petición)
  obtenerBebidasActivasBarra(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(`${this.apiUrl}/ordenes/barra/activas`);
  }

  // 🚀 CARGA ÚNICA SALA
  obtenerPlatosSala(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(`${this.apiUrl}/ordenes/sala/platos`);
  }

  // 🚀 DESHACER ENTREGA (Vuelve el estado a 'Listo')
  deshacerEntregaPlato(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(`${this.apiUrl}/ordenes/${ordenId}/listo`, {});
  }

  marcarEnPreparacion(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(`${this.apiUrl}/ordenes/${ordenId}/en-preparacion`, {});
  }

  marcarLista(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(`${this.apiUrl}/ordenes/${ordenId}/lista`, {});
  }

  marcarEntregada(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(`${this.apiUrl}/ordenes/${ordenId}/entregada`, {});
  }

  marcarPendiente(ordenId: string): Observable<OrdenCocinaResponse> {
    return this.http.post<OrdenCocinaResponse>(`${this.apiUrl}/ordenes/${ordenId}/pendiente`, {});
  }

  // 🚀 RESTAURADOS: Endpoints para la Cocina
  obtenerPendientesCocina(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(`${this.apiUrl}/ordenes/cocina/pendientes`);
  }

  obtenerEnPreparacionCocina(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(`${this.apiUrl}/ordenes/cocina/en-preparacion`);
  }

  obtenerListasCocina(): Observable<OrdenCocinaResponse[]> {
    return this.http.get<OrdenCocinaResponse[]>(`${this.apiUrl}/ordenes/cocina/listas`);
  }
}
