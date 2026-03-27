package repository.interfaces;

import model.Reserva;

import java.util.Date;
import java.util.List;

public interface ReservaRepository extends Repository<Reserva, String> {
    List<Reserva> findByDate(Date date);
}
