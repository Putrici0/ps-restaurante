package model;

import java.time.LocalDate;

public record Orden(String id, Pedido pedido, Plato plato, double price, OrdenEstado ordenEstado, LocalDate fecha, String Detalles){}
