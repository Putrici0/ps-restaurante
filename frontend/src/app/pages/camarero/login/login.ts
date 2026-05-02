import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { FirebaseError } from 'firebase/app';
import { CamareroAuthService } from '../../../services/camarero-auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginCamarero {
  correo = '';
  password = '';
  cargando = false;
  error = '';

  constructor(
    private cdr: ChangeDetectorRef,
    private router: Router,
    private camareroAuthService: CamareroAuthService
  ) {}

  private setError(mensaje: string) {
    this.error = mensaje;
    this.cdr.detectChanges();
  }

  async iniciarSesion() {
    if (this.cargando) {
      return;
    }

    this.error = '';
    const correoLimpio = this.correo.trim();
    const passwordLimpia = this.password.trim();

    if (!correoLimpio || !passwordLimpia) {
      this.setError('Debes completar correo y contrasena.');
      return;
    }

    const emailValido = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correoLimpio);
    if (!emailValido) {
      this.setError('El correo no es valido.');
      return;
    }

    this.cargando = true;
    this.cdr.detectChanges();

    try {
      await this.camareroAuthService.iniciarSesion(correoLimpio, passwordLimpia);
      await this.router.navigate(['/camarero/mesas']);
    } catch (e) {
      const errorCode = (e as { code?: string })?.code;

      if (
        errorCode === 'auth/invalid-credential' ||
        errorCode === 'auth/invalid-login-credentials' ||
        errorCode === 'auth/user-not-found' ||
        errorCode === 'auth/wrong-password' ||
        errorCode === 'auth/invalid-email'
      ) {
        this.setError('Correo o contrasena incorrectos.');
      } else if (e instanceof Error && e.message === 'NO_CAMARERO') {
        this.setError('Esta cuenta no tiene rol de camarero.');
      } else if (e instanceof Error && e.message === 'FIREBASE_TIMEOUT') {
        this.setError('Tiempo agotado. Revisa conexion y configuracion de Firebase.');
      } else if (e instanceof FirebaseError) {
        this.setError(`No se pudo iniciar sesion (${e.code}).`);
      } else {
        this.setError('No se pudo iniciar sesion.');
      }

      alert(this.error);
      console.error(e);
    } finally {
      this.cargando = false;
      this.cdr.detectChanges();
    }
  }
}
