package controller;

import dto.NotificacionRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Notificacion;
import service.NotificacionService;
import util.ApiError;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class NotificacionController {

    private final NotificacionService service;

    public NotificacionController(NotificacionService service) {
        this.service = service;
    }

    public EndpointGroup routes() {

        return () -> {

            path("notificaciones", () -> {

                post(ctx -> {

                    NotificacionRequest request = ctx.bodyAsClass(NotificacionRequest.class);

                    Notificacion creada = service.create(request);

                    ctx.status(201).json(creada);

                });

                get(ctx -> ctx.json(service.findAll()));

                path("{id}", () -> {

                    get(ctx -> {

                        String id = ctx.pathParam("id");

                        Optional<Notificacion> notificacion = service.findById(id);

                        if (notificacion.isPresent()) {

                            ctx.json(notificacion.get());

                        } else {

                            ctx.status(404)
                                    .json(new ApiError("Notificacion no encontrada"));

                        }

                    });

                    delete(ctx -> {

                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {

                            ctx.status(404)
                                    .json(new ApiError("Notificacion no encontrada"));

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