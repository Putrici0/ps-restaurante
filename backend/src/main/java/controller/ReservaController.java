package controller;

import dto.ReservaRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Reserva;
import service.ReservaService;
import util.ApiError;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class ReservaController {

    private final ReservaService service;

    public ReservaController(ReservaService service) {
        this.service = service;
    }

    public EndpointGroup routes() {
        return () -> {
            path("reservas", () -> {

                post(ctx -> {
                    ReservaRequest request = ctx.bodyAsClass(ReservaRequest.class);
                    Reserva creada = service.create(request);
                    ctx.status(201).json(creada);
                });

                get(ctx -> {
                    if (!paginationRequested(ctx.queryParam("limit"), ctx.queryParam("cursor"))) {
                        ctx.json(service.findAll());
                        return;
                    }

                    int limit = parseLimit(ctx.queryParam("limit"));
                    String cursor = normalizeCursor(ctx.queryParam("cursor"));
                    ctx.json(service.findPage(limit, cursor));
                });

                path("{id}", () -> {

                    get(ctx -> {
                        String id = ctx.pathParam("id");
                        Optional<Reserva> reserva = service.findById(id);

                        if (reserva.isPresent()) {
                            ctx.json(reserva.get());
                        } else {
                            ctx.status(404).json(new ApiError("Reserva no encontrada"));
                        }
                    });

                    put(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Reserva no encontrada"));
                            return;
                        }

                        ReservaRequest request = ctx.bodyAsClass(ReservaRequest.class);
                        Reserva actualizada = service.update(id, request);
                        ctx.json(actualizada);
                    });

                    delete(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Reserva no encontrada"));
                            return;
                        }

                        service.delete(id);
                        ctx.status(204);
                    });
                });
            });
        };
    }

    private boolean paginationRequested(String limitParam, String cursorParam) {
        return (limitParam != null && !limitParam.isBlank())
                || (cursorParam != null && !cursorParam.isBlank());
    }

    private int parseLimit(String limitParam) {
        if (limitParam == null || limitParam.isBlank()) {
            return 50;
        }

        int parsed;
        try {
            parsed = Integer.parseInt(limitParam.trim());
        } catch (NumberFormatException e) {
            return 50;
        }
        return Math.max(1, Math.min(parsed, 100));
    }

    private String normalizeCursor(String cursorParam) {
        return (cursorParam == null || cursorParam.isBlank()) ? null : cursorParam.trim();
    }
}
