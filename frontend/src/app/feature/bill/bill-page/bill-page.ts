import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Header } from '../../../shared/components/header/header';

interface SubItem {
  id: string;
  seleccionado: boolean;
}

interface ItemCuenta {
  nombre: string;
  cantidad: number;
  precioUnitario: number;
  subItems: SubItem[];
}

@Component({
  selector: 'app-bill-page',
  standalone: true,
  imports: [CommonModule, Header],
  templateUrl: './bill-page.html',
  styleUrls: ['./bill-page.css'],
})
export class BillPage {
  // Estado de la vista
  viewMode = signal<'normal' | 'dividida'>('normal');
  showDetailPopup = signal<boolean>(false);

  // Datos simulados, lo que el cliente ha pedido
  pedidos = signal<ItemCuenta[]>([
    { nombre: 'Sorem ipsum dolor sit amet.', cantidad: 2, precioUnitario: 4.85, subItems: this.crearSubItems(4) },
    { nombre: 'Sorem ipsum dolor sit amet.', cantidad: 2, precioUnitario: 1.65, subItems: this.crearSubItems(2) },
    { nombre: 'Sorem ipsum dolor sit amet.', cantidad: 1, precioUnitario: 5.0, subItems: this.crearSubItems(1) },
    { nombre: 'Sorem ipsum dolor sit amet.', cantidad: 1, precioUnitario: 13.22, subItems: this.crearSubItems(1) },
    { nombre: 'Sorem ipsum dolor sit amet.', cantidad: 3, precioUnitario: 5.1, subItems: this.crearSubItems(3) },
    { nombre: 'Sorem ipsum dolor sit amet.', cantidad: 2, precioUnitario: 3.85, subItems: this.crearSubItems(2) },
    { nombre: 'Sorem ipsum dolor sit amet.', cantidad: 5, precioUnitario: 3.27, subItems: this.crearSubItems(5)  },
  ]);

  // función aux para generar los checkboxes según la cantidad
  private crearSubItems(cantidad: number): SubItem[] {
    return Array.from({ length: cantidad }, () => ({
      id: Math.random().toString(36).substr(2, 9),
      seleccionado: false,
    }));
  }

  // Vista normal (no diviida)
  totalNormal = computed(() => {
    return this.pedidos().reduce((acc, item) => acc + item.cantidad * item.precioUnitario, 0);
  });

  // Vista dividida
  toggleSubItem(itemIndex: number, subItemId: string) {
    this.pedidos.update((lista) => {
      const nuevaLista = [...lista];
      const subItem = nuevaLista[itemIndex].subItems.find((s) => s.id === subItemId);
      if (subItem) subItem.seleccionado = !subItem.seleccionado;
      return nuevaLista;
    });
  }

  // Lista resumen (sólo lo que el usuario ha separado de la cuenta original)
  pedidosSeleccionados = computed(() => {
    return this.pedidos()
      .map((item) => {
        const seleccionados = item.subItems.filter((s) => s.seleccionado).length;
        return {
          nombre: item.nombre,
          cantidad: seleccionados,
          precioUnitario: item.precioUnitario,
        };
      })
      .filter((item) => item.cantidad > 0); // Oculta los platos que no tienen nada seleccionado
  });

  // Total a pagar de la cuenta dividida
  totalDividida = computed(() => {
    return this.pedidosSeleccionados().reduce(
      (acc, item) => acc + item.cantidad * item.precioUnitario,
      0,
    );
  });
}
