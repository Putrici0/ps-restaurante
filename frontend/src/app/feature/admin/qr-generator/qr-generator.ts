import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { QRCodeComponent } from 'angularx-qrcode'; // Importamos la librería

@Component({
  selector: 'app-qr-generator',
  standalone: true,
  imports: [FormsModule, QRCodeComponent],
  templateUrl: './qr-generator.html',
  styleUrls: ['./qr-generator.css'],
})
export class QrGenerator {
  // Empezamos por defecto en la mesa 1
  tableNumber = signal<number>(1);

  // Esta función genera la URL exacta que el móvil va a leer
  get qrUrl(): string {
    // Nota: Cuando subas el proyecto a internet, cambiarás 'http://localhost:4200' por 'https://turestaurante.com'
    return `http://localhost:4200/acceso/${this.tableNumber()}`;
  }
}
