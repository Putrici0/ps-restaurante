package controller;

import dto.MesaRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.Pedido;
import service.MesaApplicationService;
import service.MesaService;
import util.ApiError;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class MesaController {
    private final MesaService service;
    private final MesaApplicationService applicationService;

    public MesaController(MesaService service, MesaApplicationService applicationService) {
        this.service = service;
        this.applicationService = applicationService;
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

                    path("ocupada", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            boolean ocupada = applicationService.estaOcupada(id);
                            ctx.json(new EstadoMesaResponse(id, ocupada));
                        });
                    });

                    path("ocupar", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Cuenta cuenta = applicationService.ocuparMesa(id);
                            ctx.status(201).json(cuenta);
                        });
                    });

                    path("cuenta-activa", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            Optional<Cuenta> cuenta = applicationService.obtenerCuentaActivaDeMesa(id);

                            if (cuenta.isPresent()) {
                                ctx.json(cuenta.get());
                            } else {
                                ctx.status(404).json(new ApiError("La mesa no tiene cuenta activa"));
                            }
                        });
                    });

                    path("validar-acceso", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");

                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> body = ctx.bodyAsClass(Map.class);

                                String password = body.get("password") != null
                                        ? body.get("password").toString()
                                        : "";

                                Cuenta cuenta = applicationService.validarAccesoMesa(id, password);

                                ctx.json(Map.of(
                                        "mesaId", id,
                                        "cuentaId", cuenta.id(),
                                        "accesoValido", true
                                ));
                            } catch (IllegalArgumentException e) {
                                ctx.status(400).json(new ApiError(e.getMessage()));
                            }
                        });
                    });

                    path("pedidos-activos", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            List<Pedido> pedidos = applicationService.obtenerPedidosActivosDeMesa(id);
                            ctx.json(pedidos);
                        });
                    });

                    path("ordenes-activas", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            List<Orden> ordenes = applicationService.obtenerOrdenesActivasDeMesa(id);
                            ctx.json(ordenes);
                        });
                    });

                    path("liberar", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Cuenta cuenta = applicationService.liberarMesa(id);
                            ctx.json(cuenta);
                        });
                    });
                });
            });
        };
    }

    private record EstadoMesaResponse(String mesaId, boolean ocupada) {
    }
}