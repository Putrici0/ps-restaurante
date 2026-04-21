package controller;

import dto.CuentaPagadaResumenResponse;
import io.javalin.apibuilder.EndpointGroup;
import service.application.HistorialCuentasApplicationService;
import util.ApiError;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class HistorialController {
    private final HistorialCuentasApplicationService historialService;

    public HistorialController(HistorialCuentasApplicationService historialService) {
        this.historialService = historialService;
    }

    public EndpointGroup routes() {
        return () -> path("cuentas", () -> path("pagadas", () -> get(ctx -> {
            String fechaRaw = ctx.queryParam("fecha");
            LocalDate fecha = null;

            if (fechaRaw != null && !fechaRaw.isBlank()) {
                try {
                    fecha = LocalDate.parse(fechaRaw);
                } catch (DateTimeParseException e) {
                    ctx.status(400).json(new ApiError("La fecha debe tener formato YYYY-MM-DD"));
                    return;
                }
            }

            List<CuentaPagadaResumenResponse> resultado = historialService.obtenerCuentasPagadas(fecha);
            ctx.json(resultado);
        })));
    }
}