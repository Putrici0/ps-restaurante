package controller;

import dto.PedidoRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Pedido;
import service.PedidoService;
import util.ApiError;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
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

                path("{id}", () -> {

                    get(ctx -> {

                        String id = ctx.pathParam("id");

                        Optional<Pedido> pedido = service.findById(id);

                        if (pedido.isPresent()) {

                            ctx.json(pedido.get());

                        } else {

                            ctx.status(404)
                                    .json(new ApiError("Pedido no encontrado"));

                        }

                    });

                    delete(ctx -> {

                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {

                            ctx.status(404)
                                    .json(new ApiError("Pedido no encontrado"));

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