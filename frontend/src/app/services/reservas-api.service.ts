import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Reserva } from '../models/reserva.model';
import { firebaseDb } from '../firebase.config';
import { 
  collection as firestoreCollection, 
  addDoc as firestoreAddDoc, 
  onSnapshot, 
  query as firestoreQuery, 
  where as firestoreWhere, 
  orderBy as firestoreOrderBy,
  doc as firestoreDoc,
  updateDoc as firestoreUpdateDoc,
  deleteDoc as firestoreDeleteDoc
} from 'firebase/firestore';

@Injectable({
  providedIn: 'root',
})
export class ReservasApiService {
  private readonly collectionName = 'reservas';

  obtenerReservas(): Observable<Reserva[]> {
    const ref = firestoreCollection(firebaseDb, this.collectionName);
    const q = firestoreQuery(ref, firestoreOrderBy('fecha', 'asc'));
    
    return new Observable<Reserva[]>((subscriber) => {
      const unsubscribe = onSnapshot(q, (snapshot) => {
        const reservas = snapshot.docs.map((doc) => ({
          id: doc.id,
          ...doc.data(),
        })) as Reserva[];
        subscriber.next(reservas);
      }, (error) => {
        console.error('Error en onSnapshot:', error);
        subscriber.error(error);
      });
      return () => unsubscribe();
    });
  }

  obtenerReservasPorFecha(fecha: string): Observable<Reserva[]> {
    const ref = firestoreCollection(firebaseDb, this.collectionName);
    const q = firestoreQuery(
      ref, 
      firestoreWhere('fecha', '==', fecha),
      firestoreOrderBy('hora', 'asc')
    );
    
    return new Observable<Reserva[]>((subscriber) => {
      const unsubscribe = onSnapshot(q, (snapshot) => {
        const reservas = snapshot.docs.map((doc) => ({
          id: doc.id,
          ...doc.data(),
        })) as Reserva[];

        // Auto-cancelar si han pasado 15 min de la hora y sigue en Confirmado/Pendiente pero no ocupada
        // Nota: El story dice "no hayan sido confirmadas de su recepción". 
        // En este sistema, asumimos que "Confirmado" es el estado inicial y que 
        // si no se ocupa la mesa, se cancela.
        const ahora = new Date();
        const minsAhora = ahora.getHours() * 60 + ahora.getMinutes();
        const hoy = ahora.toISOString().split('T')[0];

        if (fecha === hoy) {
          reservas.forEach(r => {
            if (r.estado === 'Confirmado') {
              const [h, m] = r.hora.split(':').map(Number);
              const minsReserva = h * 60 + m;
              if (minsAhora - minsReserva > 15) {
                this.actualizarEstado(r.id!, 'Cancelado');
              }
            }
          });
        }

        subscriber.next(reservas);
      }, (error) => {
        subscriber.error(error);
      });
      return () => unsubscribe();
    });
  }

  crearReserva(reserva: Omit<Reserva, 'id'>): Promise<string> {
    const ref = firestoreCollection(firebaseDb, this.collectionName);
    return firestoreAddDoc(ref, {
      ...reserva,
      creadoEn: new Date().toISOString(),
    }).then((docRef) => docRef.id);
  }

  actualizarEstado(id: string, estado: 'Confirmado' | 'Cancelado' | 'Pendiente'): Promise<void> {
    const ref = firestoreDoc(firebaseDb, this.collectionName, id);
    return firestoreUpdateDoc(ref, { estado });
  }

  actualizarReserva(id: string, reserva: Partial<Reserva>): Promise<void> {
    const ref = firestoreDoc(firebaseDb, this.collectionName, id);
    return firestoreUpdateDoc(ref, { ...reserva });
  }

  borrarReserva(id: string): Promise<void> {
    const ref = firestoreDoc(firebaseDb, this.collectionName, id);
    return firestoreDeleteDoc(ref);
  }
}
