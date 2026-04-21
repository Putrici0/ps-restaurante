import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, map, Observable } from 'rxjs';
import { CuentaApi, ImporteCuentaApi, Mesa, MesaApi } from '../models/mesa.model';
import { MESAS_LAYOUT } from '../data/mesas-layout';

@Injectable({
  providedIn: 'root',
})
export class MesasApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `http://${window.location.hostname}:7070`;

  obtenerMesas(): Observable<MesaApi[]> {
    return this.http.get<MesaApi[]>(`${this.apiUrl}/mesas`);
  }

  obtenerCuentas(): Observable<CuentaApi[]> {
    return this.http.get<CuentaApi[]>(`${this.apiUrl}/cuentas`);
  }

  obtenerTotalCuenta(cuentaId: string): Observable<ImporteCuentaApi> {
    return this.http.get<ImporteCuentaApi>(`${this.apiUrl}/cuentas/${cuentaId}/total`);
  }

  pagarCuentaCompleta(cuentaId: string, metodoPago: 'EFECTIVO' | 'TARJETA') {
    return this.http.post(`${this.apiUrl}/cuentas/${cuentaId}/pagar-total`, {
      metodoPago
    });
  }

  ocuparMesa(id: string): Observable<CuentaApi> {
    return this.http.post<CuentaApi>(`${this.apiUrl}/mesas/${id}/ocupar`, {});
  }

  liberarMesa(id: string): Observable<CuentaApi> {
    return this.http.post<CuentaApi>(`${this.apiUrl}/mesas/${id}/liberar`, {});
  }

  validarAccesoMesa(
    mesaId: string,
    password: string
  ): Observable<{ mesaId: string; cuentaId: string; accesoValido: boolean }> {
    return this.http.post<{ mesaId: string; cuentaId: string; accesoValido: boolean }>(
      `${this.apiUrl}/mesas/${mesaId}/validar-acceso`,
      { password }
    );
  }

  cargarMesasParaVista(): Observable<Mesa[]> {
    return forkJoin({
      mesas: this.obtenerMesas(),
      cuentas: this.obtenerCuentas(),
    }).pipe(
      map(({ mesas, cuentas }) => {
        const layoutMap = new Map(MESAS_LAYOUT.map((mesa) => [mesa.id, mesa]));

        return mesas
          .map((mesaDb) => {
            const layout = layoutMap.get(mesaDb.id);

            const cuentaActiva =
              cuentas.find(
                (cuenta) => !cuenta.payed && cuenta.mesas?.some((mesa) => mesa.id === mesaDb.id)
              ) ?? null;

            return {
              id: mesaDb.id,
              capacidad: mesaDb.capacidad,
              zona: layout?.zona ?? 'interior',
              estado: cuentaActiva ? 'ocupada' : 'libre',
              cuentaActivaId: cuentaActiva?.id ?? null,
              cuentaActiva,
            } as Mesa;
          })
          .sort((a, b) => Number(a.id) - Number(b.id));
      })
    );
  }
}
