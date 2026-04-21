package controller;

import dto.OrdenRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Orden;
import service.OrdenService;
import service.application.NotificacionApplicationService;
import service.application.OrdenApplicationService;
import util.ApiError;

import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class OrdenController {

    private final OrdenService service;
    private final OrdenApplicationService applicationService;
    private final NotificacionApplicationService notificacionApplicationService;

    public OrdenController(
            OrdenService service,
            OrdenApplicationService applicationService,
            NotificacionApplicationService notificacionApplicationService
    ) {
        this.service = service;
        this.applicationService = applicationService;
        this.notificacionApplicationService = notificacionApplicationService;
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

                path("pendientes", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesPendientes())));
                path("en-preparacion", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesEnPreparacion())));
                path("listas", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesListas())));

                path("cocina", () -> {
                    path("pendientes", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesCocinaPendientes())));
                    path("en-preparacion", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesCocinaEnPreparacion())));
                    path("listas", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesCocinaListas())));
                });

                path("barra", () -> {
                    path("pendientes", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesBarraPendientes())));
                    path("en-preparacion", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesBarraEnPreparacion())));
                    path("listas", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesBarraListas())));
                });

                path("sala", () -> {
                    path("platos", () -> get(ctx -> ctx.json(applicationService.obtenerOrdenesSalaPlatos())));
                });

                path("pedido/{pedidoId}", () -> {
                    get(ctx -> {
                        String pedidoId = ctx.pathParam("pedidoId");
                        List<Orden> ordenes = applicationService.obtenerOrdenesDePedido(pedidoId);
                        ctx.json(ordenes);
                    });

                    post(ctx -> {
                        String pedidoId = ctx.pathParam("pedidoId");
                        CrearOrdenesBody body = ctx.bodyAsClass(CrearOrdenesBody.class);
                        List<Orden> creadas = applicationService.crearOrdenesDesdePedido(
                                pedidoId,
                                body.platosIds,
                                body.detalles
                        );
                        ctx.status(201).json(creadas);
                    });
                });

                path("{id}", () -> {
                    get(ctx -> {
                        String id = ctx.pathParam("id");
                        Optional<Orden> orden = service.findById(id);

                        if (orden.isPresent()) {
                            ctx.json(orden.get());
                        } else {
                            ctx.status(404).json(new ApiError("Orden no encontrada"));
                        }
                    });

                    delete(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Orden no encontrada"));
                            return;
                        }

                        service.delete(id);
                        ctx.status(204);
                    });

                    path("pendiente", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Orden orden = applicationService.marcarOrdenPendiente(id);
                            ctx.json(orden);
                        });
                    });

                    path("en-preparacion", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Orden orden = applicationService.marcarOrdenEnPreparacion(id);
                            ctx.json(orden);
                        });
                    });

                    path("lista", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Orden orden = applicationService.marcarOrdenLista(id);

                            if (orden.pedido() != null
                                    && orden.pedido().cuenta() != null
                                    && orden.pedido().cuenta().id() != null) {
                                notificacionApplicationService.crearNotificacionPedidoListo(
                                        orden.pedido().cuenta().id()
                                );
                            }

                            ctx.json(orden);
                        });
                    });

                    path("entregada", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Orden orden = applicationService.marcarOrdenEntregada(id);
                            ctx.json(orden);
                        });
                    });
                });
            });
        };
    }

    public static class CrearOrdenesBody {
        public List<String> platosIds;
        public List<String> detalles;
    }
}