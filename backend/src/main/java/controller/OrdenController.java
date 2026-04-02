package controller;

import dto.OrdenRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Orden;
import service.OrdenService;
import util.ApiError;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class OrdenController {

    private final OrdenService service;

    public OrdenController(OrdenService service) {
        this.service = service;
    }

    public EndpointGroup routes() {

        return () -> {

            path("ordenes", () -> {

                post(ctx -> {

                    OrdenRequest request = ctx.bodyAsClass(OrdenRequest.class);

                    Orden creada = service.create(request);

                    ctx.status(201).json(creada);

                });

                get(ctx -> ctx.json(service.findAll()));

                path("{id}", () -> {

                    get(ctx -> {

                        String id = ctx.pathParam("id");

                        Optional<Orden> orden = service.findById(id);

                        if (orden.isPresent()) {

                            ctx.json(orden.get());

                        } else {

                            ctx.status(404)
                                    .json(new ApiError("Orden no encontrada"));

                        }

                    });

                    delete(ctx -> {

                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {

                            ctx.status(404)
                                    .json(new ApiError("Orden no encontrada"));

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