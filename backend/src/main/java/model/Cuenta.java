package model;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record Cuenta(String id, List<Mesa> mesas, boolean payed, Optional<Reserva> reserva, LocalDate fecha_creacion) { }

