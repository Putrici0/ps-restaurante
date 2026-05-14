package controller;

import dto.PlatoRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Plato;
import service.PlatoService;
import util.ApiError;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class PlatoController {

    private final PlatoService service;

    public PlatoController(PlatoService service) {
        this.service = service;
    }

    public EndpointGroup routes() {
        return () -> {
            path("platos", () -> {

                post(ctx -> {
                    PlatoRequest request = ctx.bodyAsClass(PlatoRequest.class);
                    Plato creado = service.create(request);
                    ctx.status(201).json(creado);
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

                path("activos", () ->
                        get(ctx -> ctx.json(service.findActivos()))
                );

                path("{id}", () -> {

                    get(ctx -> {
                        String id = ctx.pathParam("id");
                        Optional<Plato> plato = service.findById(id);

                        if (plato.isPresent()) {
                            ctx.json(plato.get());
                        } else {
                            ctx.status(404).json(new ApiError("Plato no encontrado"));
                        }
                    });

                    put(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Plato no encontrado"));
                            return;
                        }

                        PlatoRequest request = ctx.bodyAsClass(PlatoRequest.class);
                        Plato actualizado = service.update(id, request);
                        ctx.json(actualizado);
                    });

                    delete(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Plato no encontrado"));
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
