package model;

import java.util.Date;

public record Orden(String id, Pedido pedido, Plato plato, double price, OrdenEstado ordenEstado, Date fecha, String detalles){}
