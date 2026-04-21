import { Routes } from '@angular/router';
import { Mesas } from './pages/mesas/mesas';
import { Bebidas } from './pages/bebidas/bebidas';
import { PlatosComponent } from './pages/platos/platos';
import { HistorialComponent } from './pages/historial/historial';
import { tableAccessGuard } from './guards/table-access.guard';
import { TableLogin } from './feature/access/table-login/table-login';
import { QrGenerator } from './feature/admin/qr-generator/qr-generator';
import { MenuPage } from './feature/menu/menu-page/menu-page';
import { BillPage } from './feature/bill/bill-page/bill-page';
import { TableroPedidos } from './pages/cocina/tablero-pedidos';

export const routes: Routes = [
  { path: '', redirectTo: 'mesas', pathMatch: 'full' },
  { path: 'mesas', component: Mesas },
  { path: 'bebidas', component: Bebidas },
  { path: 'platos', component: PlatosComponent },
  { path: 'historial', component: HistorialComponent },
  { path: 'acceso/:id', component: TableLogin },
  { path: 'admin/generar-qr', component: QrGenerator },
  { path: 'menu/:id', component: MenuPage, canActivate: [tableAccessGuard] },
  { path: 'cuenta/:id', component: BillPage, canActivate: [tableAccessGuard] },
  { path: 'cocina', component: TableroPedidos },
];
