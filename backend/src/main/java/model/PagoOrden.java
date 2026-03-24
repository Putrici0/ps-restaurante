package model;

import java.util.List;

public record PagoOrden(int id, double total, List<Orden> orden) {
}
