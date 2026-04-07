package model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record Cuenta(
        String id,
        List<Mesa> mesas,
        boolean payed,
        Optional<Reserva> reserva,
        Instant fechaCreacion,
        Optional<Instant> fechaPago,
        String password
) {
    public Cuenta {
        mesas = (mesas == null) ? List.of() : List.copyOf(mesas);
        password = (password == null) ? "" : password;
    }
}