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
import { MenuCamarero } from './pages/camarero/menu/menu';
import { PedidoCamarero } from './pages/camarero/pedido/pedido';
import { BebidasCamarero } from './pages/camarero/bebidas/bebidas';
import { PlatosCamarero } from './pages/camarero/platos/platos';
import { LoginCamarero } from './pages/camarero/login/login';
import { MesasCamarero } from './pages/camarero/mesas/mesas';

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
  // Rutas de Camarero
  { path: 'camarero/login', component: LoginCamarero },
  { path: 'camarero/mesas', component: MesasCamarero },
  { path: 'camarero/menu/:id', component: MenuCamarero },
  { path: 'camarero/cuenta/:id', component: PedidoCamarero },
  { path: 'camarero/bebidas', component: BebidasCamarero },
  { path: 'camarero/platos', component: PlatosCamarero },
];
