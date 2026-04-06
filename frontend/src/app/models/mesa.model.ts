export type EstadoMesa = 'libre' | 'ocupada' | 'reservada';

export interface Mesa {
  id: string;
  capacidad: number;
  estado: EstadoMesa;
}
