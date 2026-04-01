package repository.interfaces;

import model.Reserva;

import java.time.Instant;
import java.util.List;

public interface ReservaRepository extends Repository<Reserva, String> {
    List<Reserva> findByFecha(Instant fecha);
}
