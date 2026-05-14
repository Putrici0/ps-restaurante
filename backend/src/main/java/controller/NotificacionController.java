package controller;

import dto.NotificacionRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Notificacion;
import model.TipoNotificacion;
import service.NotificacionService;
import service.application.NotificacionApplicationService;
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

                get(ctx -> {
                    if (!paginationRequested(ctx.queryParam("limit"), ctx.queryParam("cursor"))) {
                        ctx.json(service.findAll());
                        return;
                    }

                    int limit = parseLimit(ctx.queryParam("limit"));
                    String cursor = normalizeCursor(ctx.queryParam("cursor"));
                    ctx.json(service.findPage(limit, cursor));
                });

                path("pendientes", () -> {
                    get(ctx -> {
                        List<Notificacion> pendientes = applicationService.obtenerNotificacionesPendientes();
                        ctx.json(pendientes);
                    });
                });

                path("activas", () -> {
                    get(ctx -> {
                        List<Notificacion> activas = applicationService.obtenerTodasLasAtencionesActivas();
                        ctx.json(activas);
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

                    get(ctx -> {
                        String cuentaId = ctx.pathParam("cuentaId");
                        Optional<Notificacion> notificacion =
                                applicationService.obtenerNotificacionAtencionActiva(cuentaId);

                        if (notificacion.isPresent()) {
                            ctx.json(notificacion.get());
                        } else {
                            ctx.status(204);
                        }
                    });
                });

                path("pedido-listo/{cuentaId}", () -> {
                    post(ctx -> {
                        String cuentaId = ctx.pathParam("cuentaId");

                        Notificacion notificacion =
                                applicationService.crearNotificacionPedidoListo(
                                        cuentaId,
                                        null,
                                        null,
                                        null
                                );

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

                    path("en-curso", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            MarcarEnCursoBody body = ctx.bodyAsClass(MarcarEnCursoBody.class);

                            Notificacion notificacion = applicationService.marcarNotificacionEnCurso(
                                    id,
                                    body.camareroUid,
                                    body.camareroNombre
                            );

                            ctx.json(notificacion);
                        });
                    });

                    path("leida", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Notificacion notificacion = applicationService.marcarNotificacionLeida(id);
                            ctx.json(notificacion);
                        });
                    });

                    path("desasignar", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Notificacion notificacion = applicationService.desasignarYReenviarNotificacion(id);
                            ctx.json(notificacion);
                        });
                    });

                    path("completada", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Notificacion notificacion = applicationService.marcarNotificacionCompletada(id);
                            ctx.json(notificacion);
                        });
                    });
                });
            });
        };
    }

    public static class MarcarEnCursoBody {
        public String camareroUid;
        public String camareroNombre;
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
