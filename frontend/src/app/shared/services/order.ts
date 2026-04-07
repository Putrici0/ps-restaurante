import { Injectable, signal } from '@angular/core';

// Esta es la estructura que usa tu página de la Cuenta
export interface SubItem {
  id: string;
  seleccionado: boolean;
}

export interface ItemCuenta {
  nombre: string;
  cantidad: number;
  precioUnitario: number;
  subItems: SubItem[];
}

@Injectable({
  providedIn: 'root',
})
export class OrderService {
  // Cuenta real del cliente
  pedidosConfirmados = signal<ItemCuenta[]>([]);

  // Función para enviar los platos del Menú a la Cuenta
  agregarPedido(nuevosPlatos: any[]) {
    this.pedidosConfirmados.update((listaActual) => {
      const nuevaLista = [...listaActual];

      nuevosPlatos.forEach((platoNuevo) => {
        // Buscamos si el plato ya estaba en la cuenta para sumar la cantidad
        const index = nuevaLista.findIndex((p) => p.nombre === platoNuevo.nombre);

        if (index !== -1) {
          nuevaLista[index].cantidad += platoNuevo.cantidad;
          // Añadimos nuevos checkboxes para la cuenta dividida
          nuevaLista[index].subItems.push(...this.crearSubItems(platoNuevo.cantidad));
        } else {
          // Si es un plato nuevo, lo añadimos a la lista
          nuevaLista.push({
            nombre: platoNuevo.nombre,
            cantidad: platoNuevo.cantidad,
            precioUnitario: platoNuevo.precio,
            subItems: this.crearSubItems(platoNuevo.cantidad),
          });
        }
      });
      return nuevaLista;
    });
  }

  // Crea los checkboxes para la cuenta dividida
  private crearSubItems(cantidad: number): SubItem[] {
    return Array.from({ length: cantidad }, () => ({
      id: Math.random().toString(36).substring(2, 9),
      seleccionado: false,
    }));
  }
}
