import { Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-camarero-header',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './camarero-header.html',
  styleUrl: './camarero-header.css',
})
export class CamareroHeader {
  @Input() titulo = '';
  @Input() pendientesCount = 0;

  menuAbierto = false;

  toggleMenu() {
    this.menuAbierto = !this.menuAbierto;
  }
}
