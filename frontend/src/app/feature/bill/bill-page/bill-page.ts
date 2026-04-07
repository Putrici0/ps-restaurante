import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Header } from '../../../shared/components/header/header';
import { OrderService } from '../../../shared/services/order';

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

  private orderService = inject(OrderService)
  // Obtenemos los platos que el cliente ha pedido
  pedidos = this.orderService.pedidosConfirmados;

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
