package dto.cocina;

import java.time.Instant;
import java.util.List;

public record CocinaTablero(
        List<CocinaOrden> pendientes,
        List<CocinaOrden> enPreparacion,
        List<CocinaOrden> listas,
        Instant fechaActualizacion
) {
}