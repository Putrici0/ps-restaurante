package repository.interfaces;

import model.Orden;
import model.OrdenEstado;
import model.Pedido;

import java.util.List;

public interface OrdenRepository extends  Repository<Orden, String> {
    List<Orden> findByPedido(Pedido pedido);
    List<Orden> findByEstado(OrdenEstado estado);
}

