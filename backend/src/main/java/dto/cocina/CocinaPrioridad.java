package dto.cocina;

public record CocinaPrioridad(int total,
    int categoria,
    int espera,
    int mesa,
    int flujo,
    boolean urgente)
{}
