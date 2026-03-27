package model;

import java.math.BigDecimal;
import java.util.List;

public record PagoOrden(String id, BigDecimal total, List<Orden> orden) {
}
