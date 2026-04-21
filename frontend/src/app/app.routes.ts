import { Routes } from '@angular/router';
import { Mesas } from './pages/barra-camarero/mesas/mesas';
import { Bebidas } from './pages/barra-camarero/bebidas/bebidas';
import { PlatosComponent } from './pages/barra-camarero/platos/platos';
import { HistorialComponent } from './pages/barra-camarero/historial/historial';
import { tableAccessGuard } from './guards/table-access.guard';
import { TableLogin } from './pages/cliente/table-login/table-login';
import { QrGenerator } from './pages/cliente/qr-generator/qr-generator';
import { MenuPage } from './pages/cliente/menu-page/menu-page';
import { BillPage } from './pages/cliente/bill-page/bill-page';
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
