package dto.cocina;

import model.OrdenEstado;
import model.Pedido;
import model.Plato;

import java.math.BigDecimal;
import java.time.Instant;

public record CocinaOrden(
        String id,
        Pedido pedido,
        Plato plato,
        BigDecimal precio,
        OrdenEstado ordenEstado,
        Instant fecha,
        String detalles,
        boolean pagada,
        CocinaPrioridad prioridad
) {
}