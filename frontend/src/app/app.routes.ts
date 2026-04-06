import { Routes } from '@angular/router';
import {TableLogin} from './feature/access/table-login/table-login';
import {QrGenerator} from './feature/admin/qr-generator/qr-generator';

export const routes: Routes = [
  // Captura el numero de la mesa dado por el QR
  { path: 'acceso/:id', component: TableLogin },
  { path: 'admin/generar-qr', component: QrGenerator },
  // Entrada por si alguien entra sin QR
  { path: '', redirectTo: 'error-qr', pathMatch: 'full' },
];
