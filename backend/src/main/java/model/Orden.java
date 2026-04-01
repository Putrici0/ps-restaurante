package model;

import java.math.BigDecimal;
import java.util.Date;

public record Orden(String id, Pedido pedido, Plato plato, BigDecimal price, OrdenEstado ordenEstado, Date fecha, String detalles){}
