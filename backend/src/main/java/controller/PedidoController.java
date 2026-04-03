package controller;

import dto.PedidoRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Pedido;
import service.application.PedidoApplicationService;
import service.PedidoService;
import util.ApiError;

import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class PedidoController {

    private final PedidoService service;
    private final PedidoApplicationService applicationService;

    public PedidoController(PedidoService service, PedidoApplicationService applicationService) {
        this.service = service;
        this.applicationService = applicationService;
    }

    public EndpointGroup routes() {
        return () -> {
            path("pedidos", () -> {

                post(ctx -> {
                    PedidoRequest request = ctx.bodyAsClass(PedidoRequest.class);
                    Pedido creado = service.create(request);
                    ctx.status(201).json(creado);
                });

                get(ctx -> ctx.json(service.findAll()));

                path("desde-mesa/{mesaId}", () -> {
                    post(ctx -> {
                        String mesaId = ctx.pathParam("mesaId");
                        Pedido pedido = applicationService.crearPedidoDesdeMesa(mesaId);
                        ctx.status(201).json(pedido);
                    });
                });

                path("mesa/{mesaId}/activos", () -> {
                    get(ctx -> {
                        String mesaId = ctx.pathParam("mesaId");
                        List<Pedido> pedidos = applicationService.obtenerPedidosActivosDeMesa(mesaId);
                        ctx.json(pedidos);
                    });
                });

                path("cuenta/{cuentaId}", () -> {
                    get(ctx -> {
                        String cuentaId = ctx.pathParam("cuentaId");
                        List<Pedido> pedidos = applicationService.obtenerPedidosDeCuenta(cuentaId);
                        ctx.json(pedidos);
                    });
                });

                path("{id}", () -> {

                    get(ctx -> {
                        String id = ctx.pathParam("id");
                        Optional<Pedido> pedido = service.findById(id);

                        if (pedido.isPresent()) {
                            ctx.json(pedido.get());
                        } else {
                            ctx.status(404).json(new ApiError("Pedido no encontrado"));
                        }
                    });

                    delete(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Pedido no encontrado"));
                            return;
                        }

                        service.delete(id);
                        ctx.status(204);
                    });

                    path("recalcular-estado", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Pedido pedido = applicationService.recalcularEstadoPedido(id);
                            ctx.json(pedido);
                        });
                    });

                    path("listo", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            boolean listo = applicationService.pedidoEstaListo(id);
                            ctx.json(new EstadoPedidoResponse(id, listo));
                        });
                    });
                });
            });
        };
    }

    private record EstadoPedidoResponse(String pedidoId, boolean listo) {
    }
}