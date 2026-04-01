package model;

import java.time.Instant;

public record Reserva(String id, String nombre, Instant fecha, int capacidad, Instant fechaCreacion) { }
