import { Routes } from '@angular/router';

import { MesasComponent } from './pages/mesas/mesas';
import { BebidasComponent } from './pages/bebidas/bebidas';

import { TableLogin } from './feature/access/table-login/table-login';
import { QrGenerator } from './feature/admin/qr-generator/qr-generator';
import { MenuPage } from './feature/menu/menu-page/menu-page';
import { BillPage } from './feature/bill/bill-page/bill-page';

export const routes: Routes = [
  { path: '', redirectTo: 'mesas', pathMatch: 'full' },

  { path: 'mesas', component: MesasComponent },
  { path: 'bebidas', component: BebidasComponent },

  { path: 'acceso/:id', component: TableLogin },
  { path: 'admin/generar-qr', component: QrGenerator },
  { path: 'menu/:id', component: MenuPage },
  { path: 'cuenta/:id', component: BillPage },
];
