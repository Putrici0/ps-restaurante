import { Component } from '@angular/core';
import { Router } from '@angular/router'; // 1. IMPORTAR ROUTER

@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginCamarero {

  // 2. INYECTAR ROUTER
  constructor(private router: Router) {}

  // 3. FUNCIÓN PARA NAVEGAR
  iniciarSesion() {
    // Aquí normalmente validarías usuario y contraseña.
    // Si es correcto, lo mandas a la vista de mesas:
    this.router.navigate(['/camarero/mesas']);
  }
}
