import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EstadoMesa } from '../../models/mesa.model';

@Component({
  selector: 'app-mesa-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mesa-card.html',
  styleUrl: './mesa-card.css',
})
export class MesaCardComponent {
  @Input() numero = '';
  @Input() capacidad = 4;
  @Input() estado: EstadoMesa = 'libre';
  @Input() seleccionada = false;
}
