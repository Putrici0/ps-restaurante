import { Routes } from '@angular/router';
import { MesasComponent } from './pages/mesas/mesas';
import { BebidasComponent } from './pages/bebidas/bebidas';

export const routes: Routes = [
  { path: '', redirectTo: 'mesas', pathMatch: 'full' },
  { path: 'mesas', component: MesasComponent },
  { path: 'bebidas', component: BebidasComponent },
];
