package model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record Orden(
        String id,
        Pedido pedido,
        Plato plato,
        BigDecimal precio,
        OrdenEstado ordenEstado,
        Instant fecha,
        String detalles,
        boolean pagada,
        Optional<Instant> fechaPago,
        Optional<MetodoPago> metodoPago
) {
    public Orden {
        detalles = detalles == null ? "" : detalles;
        fechaPago = fechaPago == null ? Optional.empty() : fechaPago;
        metodoPago = metodoPago == null ? Optional.empty() : metodoPago;
    }

    // Constructor compatible con el código existente
    public Orden(
            String id,
            Pedido pedido,
            Plato plato,
            BigDecimal precio,
            OrdenEstado ordenEstado,
            Instant fecha,
            String detalles
    ) {
        this(
                id,
                pedido,
                plato,
                precio,
                ordenEstado,
                fecha,
                detalles,
                false,
                Optional.empty(),
                Optional.empty()
        );
    }
}