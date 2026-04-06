import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Mesa } from '../../models/mesa.model'; // Importado

@Component({
  selector: 'app-mesa-detalle',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mesa-detalle.html',
  styleUrl: './mesa-detalle.css',
})
export class MesaDetalleComponent {
  // Ahora Angular sabe exactamente qué es esto
  @Input({ required: true }) mesa!: Mesa;
}
