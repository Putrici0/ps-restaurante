package model;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public record Cuenta(String id, List<Mesa> mesas, boolean payed, Optional<Reserva> reserva, Date fecha_creacion) { }

