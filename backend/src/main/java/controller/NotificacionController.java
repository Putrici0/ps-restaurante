package controller;

import dto.NotificacionRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Notificacion;
import model.TipoNotificacion;
import service.application.NotificacionApplicationService;
import service.NotificacionService;
import util.ApiError;

import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class NotificacionController {

    private final NotificacionService service;
    private final NotificacionApplicationService applicationService;

    public NotificacionController(
            NotificacionService service,
            NotificacionApplicationService applicationService
    ) {
        this.service = service;
        this.applicationService = applicationService;
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

                path("pendientes", () -> {
                    get(ctx -> {
                        List<Notificacion> pendientes = applicationService.obtenerNotificacionesPendientes();
                        ctx.json(pendientes);
                    });
                });

                path("cuenta/{cuentaId}", () -> {
                    get(ctx -> {
                        String cuentaId = ctx.pathParam("cuentaId");
                        List<Notificacion> notificaciones =
                                applicationService.obtenerNotificacionesDeCuenta(cuentaId);
                        ctx.json(notificaciones);
                    });
                });

                path("tipo/{tipo}", () -> {
                    get(ctx -> {
                        String tipo = ctx.pathParam("tipo");

                        TipoNotificacion tipoNotificacion;
                        try {
                            tipoNotificacion = TipoNotificacion.valueOf(tipo);
                        } catch (IllegalArgumentException e) {
                            ctx.status(400).json(new ApiError("Tipo de notificación inválido"));
                            return;
                        }

                        List<Notificacion> notificaciones =
                                applicationService.obtenerNotificacionesPorTipo(tipoNotificacion);
                        ctx.json(notificaciones);
                    });
                });

                path("atencion/{cuentaId}", () -> {
                    post(ctx -> {
                        String cuentaId = ctx.pathParam("cuentaId");
                        Notificacion notificacion =
                                applicationService.crearNotificacionAtencion(cuentaId);
                        ctx.status(201).json(notificacion);
                    });
                });

                path("pedido-listo/{cuentaId}", () -> {
                    post(ctx -> {
                        String cuentaId = ctx.pathParam("cuentaId");
                        Notificacion notificacion =
                                applicationService.crearNotificacionPedidoListo(cuentaId);
                        ctx.status(201).json(notificacion);
                    });
                });

                path("{id}", () -> {

                    get(ctx -> {
                        String id = ctx.pathParam("id");
                        Optional<Notificacion> notificacion = service.findById(id);

                        if (notificacion.isPresent()) {
                            ctx.json(notificacion.get());
                        } else {
                            ctx.status(404).json(new ApiError("Notificacion no encontrada"));
                        }
                    });

                    delete(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Notificacion no encontrada"));
                            return;
                        }

                        service.delete(id);
                        ctx.status(204);
                    });

                    path("leida", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Notificacion notificacion = applicationService.marcarNotificacionLeida(id);
                            ctx.json(notificacion);
                        });
                    });
                });
            });
        };
    }
}