import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MesaCardComponent } from '../../components/mesa-card/mesa-card';
import { MesaDetalleComponent } from '../../components/mesa-detalle/mesa-detalle';

type EstadoMesa = 'libre' | 'ocupada' | 'reservada';

interface Mesa {
  id: string;
  capacidad: number;
  estado: EstadoMesa;
}

@Component({
  selector: 'app-mesas',
  standalone: true,
  imports: [CommonModule, MesaCardComponent, MesaDetalleComponent],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class MesasComponent {
  zona: 'interior' | 'terraza' = 'interior';
  mesaSeleccionada: Mesa | null = null;

  mesasInterior: Mesa[] = [
    { id: 'M1', capacidad: 4, estado: 'ocupada' },
    { id: 'M2', capacidad: 4, estado: 'libre' },
    { id: 'M3', capacidad: 4, estado: 'ocupada' },
    { id: 'M4', capacidad: 4, estado: 'ocupada' },
    { id: 'M5', capacidad: 4, estado: 'ocupada' },
    { id: 'M6', capacidad: 4, estado: 'libre' },
    { id: 'M7', capacidad: 4, estado: 'ocupada' },
    { id: 'M8', capacidad: 4, estado: 'libre' },
    { id: 'M9', capacidad: 4, estado: 'libre' },
    { id: 'M10', capacidad: 4, estado: 'reservada' },
    { id: 'M11', capacidad: 4, estado: 'reservada' },
    { id: 'M12', capacidad: 4, estado: 'ocupada' },
  ];

  mesasTerraza: Mesa[] = [
    { id: 'M13', capacidad: 4, estado: 'ocupada' },
    { id: 'M14', capacidad: 4, estado: 'libre' },
    { id: 'M15', capacidad: 4, estado: 'libre' },
    { id: 'M16', capacidad: 4, estado: 'ocupada' },
    { id: 'M17', capacidad: 4, estado: 'ocupada' },
    { id: 'M18', capacidad: 4, estado: 'reservada' },
    { id: 'M19', capacidad: 4, estado: 'reservada' },
    { id: 'M20', capacidad: 4, estado: 'ocupada' },
  ];

  get mesasActuales(): Mesa[] {
    return this.zona === 'interior' ? this.mesasInterior : this.mesasTerraza;
  }

  seleccionar(mesa: Mesa) {
    this.mesaSeleccionada = mesa;
  }
}
