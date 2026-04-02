package controller;

import dto.CuentaRequest;
import io.javalin.apibuilder.EndpointGroup;
import model.Cuenta;
import service.CuentaService;
import util.ApiError;

import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class CuentaController {

    private final CuentaService service;

    public CuentaController(CuentaService service) {
        this.service = service;
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

                            ctx.status(404)
                                    .json(new ApiError("Cuenta no encontrada"));

                        }
                    });

                    delete(ctx -> {

                        String id = ctx.pathParam("id");

                        if (service.findById(id).isEmpty()) {

                            ctx.status(404)
                                    .json(new ApiError("Cuenta no encontrada"));

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