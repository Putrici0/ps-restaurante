package controller;

import dto.CuentaRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Cuenta;
import model.Orden;
import model.Pedido;
import service.CuentaService;
import service.application.PagoApplicationService;
import util.ApiError;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class CuentaController {

    private final CuentaService service;
    private final PagoApplicationService pagoApplicationService;

    public CuentaController(CuentaService service, PagoApplicationService pagoApplicationService) {
        this.service = service;
        this.pagoApplicationService = pagoApplicationService;
    }

    public EndpointGroup routes() {
        return () -> {
            path("cuentas", () -> {

                post(ctx -> {
                    CuentaRequest request = ctx.bodyAsClass(CuentaRequest.class);
                    Cuenta creada = service.create(request);
                    ctx.status(201).json(creada);
                });

                get(ctx -> ctx.json(service.findAll()));

                path("{id}", () -> {

                    get(ctx -> {
                        String id = ctx.pathParam("id");
                        Optional<Cuenta> cuenta = service.findById(id);

                        if (cuenta.isPresent()) {
                            ctx.json(cuenta.get());
                        } else {
                            ctx.status(404).json(new ApiError("Cuenta no encontrada"));
                        }
                    });

                    delete(ctx -> {
                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {
                            ctx.status(404).json(new ApiError("Cuenta no encontrada"));
                            return;
                        }

                        service.delete(id);
                        ctx.status(204);
                    });

                    path("pedidos", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            List<Pedido> pedidos = pagoApplicationService.obtenerPedidosDeCuenta(id);
                            ctx.json(pedidos);
                        });
                    });

                    path("ordenes", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            List<Orden> ordenes = pagoApplicationService.obtenerOrdenesDeCuenta(id);
                            ctx.json(ordenes);
                        });
                    });

                    path("total", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            BigDecimal total = pagoApplicationService.calcularTotalCuenta(id);
                            ctx.json(new ImporteResponse(id, total));
                        });
                    });

                    path("pendiente", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            BigDecimal pendiente = pagoApplicationService.calcularPendienteCuenta(id);
                            ctx.json(new ImporteResponse(id, pendiente));
                        });
                    });

                    path("saldada", () -> {
                        get(ctx -> {
                            String id = ctx.pathParam("id");
                            boolean saldada = pagoApplicationService.cuentaEstaSaldada(id);
                            ctx.json(new EstadoCuentaResponse(id, saldada));
                        });
                    });

                    path("pagar-total", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Cuenta cuenta = pagoApplicationService.pagarCuentaCompleta(id);
                            ctx.json(cuenta);
                        });
                    });

                    path("cerrar-si-procede", () -> {
                        post(ctx -> {
                            String id = ctx.pathParam("id");
                            Cuenta cuenta = pagoApplicationService.cerrarCuentaSiProcede(id);
                            ctx.json(cuenta);
                        });
                    });
                });
            });
        };
    }

    private record ImporteResponse(String cuentaId, BigDecimal importe) {
    }

    private record EstadoCuentaResponse(String cuentaId, boolean saldada) {
    }
}