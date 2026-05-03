package dto.cocina;

import java.util.List;

public record CocinaPrioridad(
        int total,
        int categoria,
        int espera,
        int mesa,
        int flujo,
        int tiempoEstimado,
        int etaMinutos,
        int sincronizacion,
        int urgencia,
        List<String> motivos
) {
}
