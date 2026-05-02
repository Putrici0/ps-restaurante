import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgZone } from '@angular/core';
import { FirebaseError } from 'firebase/app';
import { CamareroAuthService } from '../../../services/camarero-auth.service';

@Component({
  selector: 'app-registro-camarero',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './registro.html',
  styleUrl: './registro.css',
})
export class RegistroCamarero {
  nombre = '';
  apellido = '';
  correo = '';
  password = '';
  cargando = false;
  error = '';
  exito = '';

  constructor(
    private ngZone: NgZone,
    private camareroAuthService: CamareroAuthService
  ) {}

  async registrar() {
    if (this.cargando) {
      return;
    }

    this.ngZone.run(() => {
      this.error = '';
      this.exito = '';
      this.cargando = true;
    });

    try {
      await this.camareroAuthService.registrarCamarero({
        nombre: this.nombre,
        apellido: this.apellido,
        correo: this.correo,
        password: this.password,
      });
      this.ngZone.run(() => {
        this.exito = 'Cuenta creada correctamente.';
        alert('Cuenta creada correctamente.');
        this.nombre = '';
        this.apellido = '';
        this.correo = '';
        this.password = '';
      });
    } catch (e) {
      if (e instanceof FirebaseError) {
        if (e.code === 'auth/email-already-in-use') {
          this.ngZone.run(() => {
            this.error = 'Ese correo ya esta en uso.';
          });
        } else if (e.code === 'auth/weak-password') {
          this.ngZone.run(() => {
            this.error = 'La contrasena debe tener al menos 6 caracteres.';
          });
        } else if (e.code === 'auth/invalid-email') {
          this.ngZone.run(() => {
            this.error = 'El correo no es valido.';
          });
        } else {
          this.ngZone.run(() => {
            this.error = 'No se pudo registrar el camarero.';
          });
        }
      } else if (e instanceof Error && e.message === 'FIREBASE_TIMEOUT') {
        this.ngZone.run(() => {
          this.error = 'Tiempo agotado. Revisa conexion y configuracion de Firebase.';
        });
      } else {
        this.ngZone.run(() => {
          this.error = 'No se pudo registrar el camarero.';
        });
      }
      console.error(e);
    } finally {
      this.ngZone.run(() => {
        this.cargando = false;
      });
    }
  }
}
