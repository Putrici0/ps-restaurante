import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MesaCardComponent } from '../../components/mesa-card/mesa-card';
import { MesaDetalleComponent } from '../../components/mesa-detalle/mesa-detalle';
import { Mesa } from '../../models/mesa.model'; // Importamos la interfaz

@Component({
  selector: 'app-mesas',
  standalone: true,
  imports: [CommonModule, MesaCardComponent, MesaDetalleComponent],
  templateUrl: './mesas.html',
  styleUrl: './mesas.css',
})
export class MesasComponent {
  // Convertimos el estado en Signals
  zona = signal<'interior' | 'terraza'>('interior');
  mesaSeleccionada = signal<Mesa | null>(null);

  // Arrays de mesas
  mesasInterior = signal<Mesa[]>([
    { id: '1', capacidad: 2, estado: 'ocupada' },
    { id: '2', capacidad: 4, estado: 'libre' },
    { id: '3', capacidad: 2, estado: 'ocupada' },
    { id: '4', capacidad: 2, estado: 'ocupada' },
    { id: '5', capacidad: 4, estado: 'ocupada' },
    { id: '6', capacidad: 4, estado: 'libre' },
    { id: '7', capacidad: 2, estado: 'ocupada' },
    { id: '8', capacidad: 4, estado: 'libre' },
    { id: '9', capacidad: 4, estado: 'libre' },
    { id: '10', capacidad: 2, estado: 'reservada' },
    { id: '11', capacidad: 4, estado: 'reservada' },
    { id: '12', capacidad: 2, estado: 'ocupada' },
  ]);

  mesasTerraza = signal<Mesa[]>([
    { id: '13', capacidad: 4, estado: 'ocupada' },
    { id: '14', capacidad: 4, estado: 'libre' },
    { id: '15', capacidad: 4, estado: 'libre' },
    { id: '16', capacidad: 2, estado: 'ocupada' },
    { id: '17', capacidad: 2, estado: 'ocupada' },
    { id: '18', capacidad: 4, estado: 'reservada' },
    { id: '19', capacidad: 4, estado: 'ocupada' },
    { id: '20', capacidad: 4, estado: 'reservada' },
  ]);

  // Computed: Reacciona automáticamente si cambia la zona o las mesas
  mesasActuales = computed(() =>
    this.zona() === 'interior' ? this.mesasInterior() : this.mesasTerraza(),
  );

  seleccionar(mesa: Mesa) {
    // Si hace clic en la misma, la deselecciona (toggle)
    if (this.mesaSeleccionada()?.id === mesa.id) {
      this.mesaSeleccionada.set(null);
    } else {
      this.mesaSeleccionada.set(mesa);
    }
  }

// 1. Modificamos la función de cambiar zona para que limpie la selección
  cambiarZona(nuevaZona: 'interior' | 'terraza') {
    this.zona.set(nuevaZona);
    this.mesaSeleccionada.set(null); // Cierra el panel al cambiar de pestaña
  }

// 2. Método para cerrar al tocar el fondo
  cerrarSeleccion(event: MouseEvent) {
    const target = event.target as HTMLElement;

    // Si el clic NO es en una mesa Y NO es en el panel de detalles
    if (!target.closest('app-mesa-card') && !target.closest('.sidebar-detalle')) {
      this.mesaSeleccionada.set(null);
    }
  }
}
