package controller;

import dto.CuentaPagadaResumenResponse;
import dto.CuentaRequest;
import dto.PagoCuentaRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Cuenta;
import model.MetodoPago;
import model.Orden;
import model.Pedido;
import service.CuentaService;
import service.application.HistorialCuentasApplicationService;
import service.application.PagoApplicationService;
import util.ApiError;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class CuentaController {
    private final CuentaService service;
    private final PagoApplicationService pagoApplicationService;
    private final HistorialCuentasApplicationService historialService;

    public CuentaController(
            CuentaService service,
            PagoApplicationService pagoApplicationService,
            HistorialCuentasApplicationService historialService
    ) {
        this.service = service;
        this.pagoApplicationService = pagoApplicationService;
        this.historialService = historialService;
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

                path("pagadas", () -> {
                    get(ctx -> {
                        String fechaRaw = ctx.queryParam("fecha");
                        LocalDate fecha = null;

                        if (fechaRaw != null && !fechaRaw.isBlank()) {
                            try {
                                fecha = LocalDate.parse(fechaRaw);
                            } catch (DateTimeParseException e) {
                                ctx.status(400).json(new ApiError("La fecha debe tener formato YYYY-MM-DD"));
                                return;
                            }
                        }

                        List<CuentaPagadaResumenResponse> resultado = historialService.obtenerCuentasPagadas(fecha);
                        ctx.json(resultado);
                    });
                });

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

                        path("{ordenId}", () -> {
                            delete(ctx -> {
                                String cuentaId = ctx.pathParam("id");
                                String ordenId = ctx.pathParam("ordenId");

                                try {
                                    pagoApplicationService.eliminarOrdenDeCuenta(cuentaId, ordenId);
                                    ctx.status(204);
                                } catch (IllegalArgumentException e) {
                                    ctx.status(400).json(new ApiError(e.getMessage()));
                                }
                            });
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

                            try {
                                PagoCuentaRequest request = ctx.bodyAsClass(PagoCuentaRequest.class);

                                if (request == null || request.metodoPago == null || request.metodoPago.isBlank()) {
                                    ctx.status(400).json(new ApiError("El método de pago es obligatorio"));
                                    return;
                                }

                                MetodoPago metodoPago = MetodoPago.valueOf(request.metodoPago.toUpperCase());
                                Cuenta cuenta = pagoApplicationService.pagarCuentaCompleta(id, metodoPago);
                                ctx.json(cuenta);
                            } catch (IllegalArgumentException e) {
                                ctx.status(400).json(new ApiError(e.getMessage()));
                            }
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

    private record ImporteResponse(String cuentaId, BigDecimal importe) {}
    private record EstadoCuentaResponse(String cuentaId, boolean saldada) {}
}