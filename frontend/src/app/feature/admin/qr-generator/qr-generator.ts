import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { QRCodeComponent } from 'angularx-qrcode';

@Component({
  selector: 'app-qr-generator',
  standalone: true,
  imports: [FormsModule, QRCodeComponent],
  templateUrl: './qr-generator.html',
  styleUrls: ['./qr-generator.css'],
})
export class QrGenerator {

  tableNumber = signal(1);

  // ⚠️ CAMBIA SOLO ESTA IP POR LA TUYA
  private readonly fallbackIp = '192.168.X.XX';

  get baseUrl(): string {
    const { protocol, hostname, port } = window.location;

    // Si estás en localhost, sustituimos por la IP
    const host = hostname === 'localhost' ? this.fallbackIp : hostname;

    return `${protocol}//${host}${port ? ':' + port : ''}`;
  }

  get qrUrl(): string {
    return `${this.baseUrl}/acceso/${this.tableNumber()}`;
  }
}
