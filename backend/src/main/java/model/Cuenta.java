package model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record Cuenta(String id, List<Mesa> mesas, boolean estaPagada, Optional<Reserva> reserva, Instant fechaCreacion, Optional<Instant> fechaPago) {
    public Cuenta {
        // inmutabilidad
        mesas = (mesas == null) ? List.of() : List.copyOf(mesas);
    }
}
