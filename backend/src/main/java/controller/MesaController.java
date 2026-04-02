package controller;

import dto.MesaRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Mesa;
import service.MesaService;
import util.ApiError;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class MesaController {

    private final MesaService service;

    public MesaController(MesaService service) {
        this.service = service;
    }

    public EndpointGroup routes() {
        return () -> {
            path("mesas", () -> {

                post(ctx -> {
                    MesaRequest request = ctx.bodyAsClass(MesaRequest.class);
                    Mesa creada = service.create(request);
                    ctx.status(201).json(creada);
                });

                get(ctx -> ctx.json(service.findAll()));

                path("{id}", () -> {

                    get(ctx -> {
                        String id = ctx.pathParam("id");
                        Optional<Mesa> mesa = service.findById(id);

                        if (mesa.isPresent()) {
                            ctx.json(mesa.get());
                        } else {
                            ctx.status(404).json(new ApiError("Mesa no encontrada"));
                        }
                    });

                    put(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Mesa no encontrada"));
                            return;
                        }

                        MesaRequest request = ctx.bodyAsClass(MesaRequest.class);
                        Mesa actualizada = service.update(id, request);
                        ctx.json(actualizada);
                    });

                    delete(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Mesa no encontrada"));
                            return;
                        }

                        service.delete(id);
                        ctx.status(204);
                    });
                });
            });
        };
    }
}