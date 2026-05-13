import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlatoApi } from '../models/plato.model';

@Injectable({
  providedIn: 'root',
})
export class PlatosApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `http://${window.location.hostname}:7070`;

  obtenerPlatos(): Observable<PlatoApi[]> {
    return this.http.get<PlatoApi[]>(`${this.apiUrl}/platos/activos`);
  }
}
