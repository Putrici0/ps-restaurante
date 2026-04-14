package controller;

import dto.OrdenRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Orden;
import service.OrdenService;
import service.NotificacionService;
import util.ApiError;

import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class OrdenController {

    private final OrdenService service;
    private final NotificacionService notificacionService;

    public OrdenController(
            OrdenService service,
            NotificacionService notificacionService
    ) {
        this.service = service;
        this.notificacionService = notificacionService;
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

                path("pendientes", () -> get(ctx -> ctx.json(service.obtenerOrdenesPendientes())));
                path("en-preparacion", () -> get(ctx -> ctx.json(service.obtenerOrdenesEnPreparacion())));
                path("listas", () -> get(ctx -> ctx.json(service.obtenerOrdenesListas())));

                path("cocina", () -> {
                    path("pendientes", () -> get(ctx -> ctx.json(service.obtenerOrdenesCocinaPendientes())));
                    path("en-preparacion", () -> get(ctx -> ctx.json(service.obtenerOrdenesCocinaEnPreparacion())));
                    path("listas", () -> get(ctx -> ctx.json(service.obtenerOrdenesCocinaListas())));
                });

                path("barra", () -> {
                    path("pendientes", () -> get(ctx -> ctx.json(service.obtenerOrdenesBarraPendientes())));
                    path("en-preparacion", () -> get(ctx -> ctx.json(service.obtenerOrdenesBarraEnPreparacion())));
                    path("listas", () -> get(ctx -> ctx.json(service.obtenerOrdenesBarraListas())));
                });

                path("sala", () -> {
                    path("platos", () -> get(ctx -> ctx.json(service.obtenerOrdenesSalaPlatos())));
                });

                path("pedido/{pedidoId}", () -> {
                    get(ctx -> {
                        String pedidoId = ctx.pathParam("pedidoId");
                        List<Orden> ordenes = service.obtenerOrdenesDePedido(pedidoId);
                        ctx.json(ordenes);
                    });

                    post(ctx -> {
                        String pedidoId = ctx.pathParam("pedidoId");
                        CrearOrdenesBody body = ctx.bodyAsClass(CrearOrdenesBody.class);
                        List<Orden> creadas = service.crearOrdenesDesdePedido(
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
                            Orden orden = service.marcarOrdenPendiente(id);
                            ctx.json(orden);
                        });
                    });

                    path("en-preparacion", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Orden orden = service.marcarOrdenEnPreparacion(id);
                            ctx.json(orden);
                        });
                    });

                    path("lista", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Orden orden = service.marcarOrdenLista(id);

                            if (orden.pedido() != null
                                    && orden.pedido().cuenta() != null
                                    && orden.pedido().cuenta().id() != null) {
                                notificacionService.crearNotificacionPedidoListo(
                                        orden.pedido().cuenta().id()
                                );
                            }

                            ctx.json(orden);
                        });
                    });

                    path("entregada", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Orden orden = service.marcarOrdenEntregada(id);
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