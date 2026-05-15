export interface Reserva {
  id?: string;
  fecha: string;
  hora: string;
  nombre: string;
  telefono: string;
  comensales: number;
  zona?: 'Interior' | 'Terraza' | 'Cualquiera';
  mesasIds?: string[];
  nota?: string;
  estado: 'Confirmado' | 'Cancelado' | 'Pendiente';
  creadoEn?: string;
}
