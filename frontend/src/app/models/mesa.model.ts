export type EstadoMesa = 'libre' | 'ocupada';
export type ZonaMesa = 'interior' | 'terraza';

export interface MesaApi {
  id: string;
  capacidad: number;
}

export interface CuentaApi {
  id: string;
  mesas: Array<{
    id: string;
    capacidad: number;
  }>;
  payed: boolean;
  fechaCreacion: string;
  fechaPago?: string | null;
  password: string;
}

export interface ImporteCuentaApi {
  cuentaId: string;
  importe: number;
}

export interface PlatoOrdenCuentaApi {
  id: string;
  nombre: string;
  categoria?: string;
}

export interface OrdenCuentaApi {
  id: string;
  precio: number;
  ordenEstado: string;
  detalles: string;
  pagada?: boolean;
  plato?: PlatoOrdenCuentaApi | null;
}

export interface Mesa {
  id: string;
  capacidad: number;
  zona: ZonaMesa;
  estado: EstadoMesa;
  cuentaActivaId: string | null;
  cuentaActiva: CuentaApi | null;
}
