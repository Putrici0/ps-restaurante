package model;

import java.util.Date;

public record Reserva(String id, String nombre, Date fecha, int capacidad, Date fecha_creacion) { }
