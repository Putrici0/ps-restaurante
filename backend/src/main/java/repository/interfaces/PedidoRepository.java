package repository.interfaces;

import model.Cuenta;
import model.Pedido;
import model.PedidoEstado;

import java.util.List;

public interface PedidoRepository extends Repository<Pedido, String> {
    List<Pedido> findByCuenta(Cuenta cuenta);
    List<Pedido> findByEstado(PedidoEstado estado);
}
