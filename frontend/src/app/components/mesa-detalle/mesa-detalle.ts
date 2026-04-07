import { CommonModule, DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { Mesa } from '../../models/mesa.model';

@Component({
  selector: 'app-mesa-detalle',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './mesa-detalle.html',
  styleUrl: './mesa-detalle.css',
})
export class MesaDetalleComponent implements OnChanges {
  @Input({ required: true }) mesa!: Mesa;
  @Input() accionEnCurso = false;

  @Output() ocupar = new EventEmitter<string>();
  @Output() cobrar = new EventEmitter<{ mesaId: string; cuentaId: string }>();

  mostrarCuenta = false;
  mostrarPassword = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['mesa']) {
      this.mostrarCuenta = false;
      this.mostrarPassword = false;
    }
  }

  toggleCuenta(): void {
    if (this.mesa.estado !== 'ocupada' || !this.mesa.cuentaActiva) {
      return;
    }

    this.mostrarCuenta = !this.mostrarCuenta;
  }

  togglePassword(): void {
    if (this.mesa.estado !== 'ocupada' || !this.mesa.cuentaActiva) {
      return;
    }

    this.mostrarPassword = !this.mostrarPassword;
  }

  solicitarCobro(): void {
    if (this.mesa.estado !== 'ocupada' || !this.mesa.cuentaActiva) {
      return;
    }

    this.cobrar.emit({
      mesaId: this.mesa.id,
      cuentaId: this.mesa.cuentaActiva.id,
    });
  }
}
