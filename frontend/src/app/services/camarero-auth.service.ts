import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { User, createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { doc, getDoc, setDoc } from 'firebase/firestore';
import { firebaseAuth, firebaseDb } from '../firebase.config';

interface RegistroCamareroInput {
  nombre: string;
  apellido: string;
  correo: string;
  password: string;
}

const FIREBASE_TIMEOUT_MS = 12000;

@Injectable({ providedIn: 'root' })
export class CamareroAuthService {
  constructor(private router: Router) {}

  async iniciarSesion(correo: string, password: string): Promise<void> {
    const credenciales = await this.withTimeout(
      signInWithEmailAndPassword(firebaseAuth, correo, password)
    );

    const autorizado = await this.withTimeout(this.esCamarero(credenciales.user.uid));
    if (!autorizado) {
      await signOut(firebaseAuth);
      throw new Error('NO_CAMARERO');
    }
  }

  async registrarCamarero(data: RegistroCamareroInput): Promise<void> {
    const credenciales = await this.withTimeout(
      createUserWithEmailAndPassword(firebaseAuth, data.correo, data.password)
    );

    await this.withTimeout(
      setDoc(doc(firebaseDb, 'usuarios', credenciales.user.uid), {
        uid: credenciales.user.uid,
        rol: 'camarero',
        nombre: data.nombre,
        apellido: data.apellido,
        correo: data.correo,
        creadoEn: new Date().toISOString(),
      })
    );
  }

  obtenerUsuarioActual(): User | null {
    return firebaseAuth.currentUser;
  }

  esperarEstadoAuth(): Promise<User | null> {
    return new Promise((resolve) => {
      const unsubscribe = onAuthStateChanged(firebaseAuth, (user) => {
        unsubscribe();
        resolve(user);
      });
    });
  }

  async cerrarSesion(): Promise<void> {
    await signOut(firebaseAuth);
    await this.router.navigate(['/camarero/login']);
  }

  async esCamarero(uid: string): Promise<boolean> {
    const snapshot = await this.withTimeout(getDoc(doc(firebaseDb, 'usuarios', uid)));
    if (!snapshot.exists()) {
      return false;
    }

    const data = snapshot.data() as { rol?: string };
    return data.rol === 'camarero';
  }

  private async withTimeout<T>(promise: Promise<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        reject(new Error('FIREBASE_TIMEOUT'));
      }, FIREBASE_TIMEOUT_MS);

      promise
        .then((value) => {
          clearTimeout(timeoutId);
          resolve(value);
        })
        .catch((error) => {
          clearTimeout(timeoutId);
          reject(error);
        });
    });
  }
}
