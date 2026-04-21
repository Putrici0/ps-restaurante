package dto;

import model.MetodoPago;

import java.math.BigDecimal;
import java.time.Instant;

public record CuentaPagadaResumenResponse(
        String cuentaId,
        Instant fechaHora,
        String mesa,
        BigDecimal importeTotal,
        MetodoPago metodoPago
) {
}
