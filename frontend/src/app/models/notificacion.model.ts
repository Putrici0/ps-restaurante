export type TipoNotificacion = 'Recoger' | 'Atencion';

export interface NotificacionMesa {
  id: string | number;
  capacidad?: number;
}

export interface NotificacionCuenta {
  id: string;
  mesas?: NotificacionMesa[];
  payed?: boolean;
  estaPagada?: boolean;
}

export interface Notificacion {
  id: string;
  cuenta?: NotificacionCuenta | null;
  tipo: TipoNotificacion;
  leida: boolean;
  fecha: string;
}
