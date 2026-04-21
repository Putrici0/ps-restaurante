package dto.cocina;

public record CocinaOrden(        String ordenId,
                                  String pedidoId,
                                  String cuentaId,
                                  Integer mesa,
                                  String platoNombre,
                                  String categoria,
                                  String estado,
                                  String fecha,
                                  String detalles,
                                  CocinaPrioridad prioridad) {
}
