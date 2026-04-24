import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-pedido-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pedido-card.html',
  styleUrl: './pedido-card.css',
})
export class PedidoCard {
  @Input() numeroMesa = '';
  @Input() estadoVisual = '';
  @Input() etiquetaEstado = '';
  @Input() tiempo = '';
  @Input() esOpaco = false;

  @Input() textoBotonPrincipal = '';
  @Input() mostrarBotonPrincipal = true;
  @Input() disablePrincipal = false;
  @Input() claseBotonPrincipal: 'verde' | 'marron' | 'rojo' | 'gris' = 'verde';
  @Input() iconoPrincipal: 'check' | 'cross' | 'none' = 'none';

  @Input() textoBotonSecundario = '';
  @Input() mostrarBotonSecundario = false;
  @Input() disableSecundario = false;
  @Input() claseBotonSecundario: 'verde' | 'marron' | 'rojo' | 'gris' = 'rojo';
  @Input() iconoSecundario: 'check' | 'cross' | 'none' = 'none';

  @Output() principal = new EventEmitter<void>();
  @Output() secundario = new EventEmitter<void>();

  /**
   * Getter que soluciona el error TS2339.
   * Asigna el sufijo de color para las clases CSS (border-xxx y pill-xxx)
   */
  get colorEstado(): string {
    switch (this.estadoVisual) {
      case 'Pendiente':
        return 'rojo';
      case 'Preparación':
        return 'amarillo';
      case 'Listo':
        return 'verde';
      case 'Entregado':
        return 'gris';
      default:
        return 'gris';
    }
  }
}
