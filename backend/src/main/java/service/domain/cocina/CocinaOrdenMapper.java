package service.domain.cocina;

import dto.cocina.CocinaOrden;
import dto.cocina.CocinaPrioridad;
import model.Orden;

public class CocinaOrdenMapper {

    public CocinaOrden toCocinaOrden(Orden orden, CocinaPrioridad prioridad) {
        return new CocinaOrden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                orden.ordenEstado(),
                orden.fecha(),
                orden.detalles(),
                orden.urgente(),
                orden.pagada(),
                prioridad
        );
    }
}
