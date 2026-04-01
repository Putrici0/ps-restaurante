package model;

import java.math.BigDecimal;
import java.time.Instant;

public record Orden(String id, Pedido pedido, Plato plato, BigDecimal precio, OrdenEstado ordenEstado, Instant fecha, String detalles){}
