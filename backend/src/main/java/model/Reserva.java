package model;

import java.time.LocalDate;

public record Reserva(String id, String nombre, LocalDate fecha, int capacidad, LocalDate fecha_creacion) { }
