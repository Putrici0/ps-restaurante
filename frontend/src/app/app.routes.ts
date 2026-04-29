import { Routes } from '@angular/router';
import { Mesas } from './pages/barra/mesas/mesas';
import { Bebidas } from './pages/barra/bebidas/bebidas';
import { PlatosComponent } from './pages/barra/platos/platos';
import { HistorialComponent } from './pages/barra/historial/historial';
import { tableAccessGuard } from './guards/table-access.guard';
import { TableLogin } from './pages/cliente/table-login/table-login';
import { QrGenerator } from './pages/cliente/qr-generator/qr-generator';
import { MenuPage } from './pages/cliente/menu-page/menu-page';
import { BillPage } from './pages/cliente/bill-page/bill-page';
import { TableroPedidos } from './pages/cocina/tablero-pedidos';
import { BebidasCamarero } from './pages/camarero/bebidas/bebidas';
import { PlatosCamarero } from './pages/camarero/platos/platos';
import { CamareroHeader } from './pages/camarero/camarero-header/camarero-header';

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
  {
    path: 'camarero',
    component: CamareroHeader,
    children: [
      { path: 'platos', component: PlatosCamarero },
      { path: 'bebidas', component: BebidasCamarero },
    ],
  },
];
