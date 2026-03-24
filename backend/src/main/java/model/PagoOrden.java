package model;

import java.util.List;

public record PagoOrden(String id, double total, List<Orden> orden) {
}
