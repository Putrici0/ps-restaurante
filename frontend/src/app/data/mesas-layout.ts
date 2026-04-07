export type ZonaMesa = 'interior' | 'terraza';

export interface MesaLayout {
  id: string;
  capacidad: number;
  zona: ZonaMesa;
}

export const MESAS_LAYOUT: MesaLayout[] = [
  { id: '1', capacidad: 2, zona: 'interior' },
  { id: '2', capacidad: 4, zona: 'interior' },
  { id: '3', capacidad: 2, zona: 'interior' },
  { id: '4', capacidad: 2, zona: 'interior' },
  { id: '5', capacidad: 4, zona: 'interior' },
  { id: '6', capacidad: 4, zona: 'interior' },
  { id: '7', capacidad: 2, zona: 'interior' },
  { id: '8', capacidad: 4, zona: 'interior' },
  { id: '9', capacidad: 4, zona: 'interior' },
  { id: '10', capacidad: 2, zona: 'interior' },
  { id: '11', capacidad: 4, zona: 'interior' },
  { id: '12', capacidad: 2, zona: 'interior' },

  { id: '13', capacidad: 4, zona: 'terraza' },
  { id: '14', capacidad: 4, zona: 'terraza' },
  { id: '15', capacidad: 4, zona: 'terraza' },
  { id: '16', capacidad: 2, zona: 'terraza' },
  { id: '17', capacidad: 2, zona: 'terraza' },
  { id: '18', capacidad: 4, zona: 'terraza' },
  { id: '19', capacidad: 4, zona: 'terraza' },
  { id: '20', capacidad: 4, zona: 'terraza' },
];
