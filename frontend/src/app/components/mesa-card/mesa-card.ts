import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-mesa-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mesa-card.html',
  styleUrl: './mesa-card.css',
})
export class MesaCardComponent {
  @Input() numero: string = '';
  @Input() capacidad: number = 4;
  @Input() estado: 'libre' | 'ocupada' | 'reservada' = 'libre';
  @Input() seleccionada: boolean = false;
}
