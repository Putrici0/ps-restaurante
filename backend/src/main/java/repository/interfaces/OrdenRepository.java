package repository.interfaces;

import model.Orden;
import model.OrdenEstado;
import model.Pedido;

import java.util.List;

public interface OrdenRepository extends  Repository<Orden, String> {
    List<Orden> findByPedido(Pedido pedido);
    List<Orden> findByPedidosIds(List<String> pedidosIds);
    List<Orden> findByEstado(OrdenEstado estado);
    List<Orden> findByEstadoAndPagada(OrdenEstado estado, boolean pagada);
    List<Orden> findByPagada(boolean pagada);
}

