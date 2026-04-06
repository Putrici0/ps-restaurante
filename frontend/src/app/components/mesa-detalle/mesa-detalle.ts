import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-mesa-detalle',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mesa-detalle.html',
  styleUrl: './mesa-detalle.css',
})
export class MesaDetalleComponent {
  @Input() mesa: any = null;
}
