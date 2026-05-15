package controller;

import dto.EnviarTiqueRequest;
import io.javalin.apibuilder.EndpointGroup;
import service.TiqueEmailService;

import java.util.Base64;
import java.util.Map;

import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.path;

public class TiqueController {

    private final TiqueEmailService tiqueEmailService;

    public TiqueController(TiqueEmailService tiqueEmailService) {
        this.tiqueEmailService = tiqueEmailService;
    }

    public EndpointGroup routes() {

        return () -> {

            path("tiques", () -> {

                post("/enviar", ctx -> {

                    EnviarTiqueRequest request = ctx.bodyAsClass(EnviarTiqueRequest.class);

                    String base64 = request.pdfBase64;

                    if (base64.contains(",")) {
                        base64 = base64.split(",")[1];
                    }

                    byte[] pdfBytes = Base64.getDecoder().decode(base64);

                    tiqueEmailService.enviarTique(
                            request.correo,
                            request.nombreArchivo,
                            pdfBytes
                    );

                    ctx.status(200).json(Map.of("ok", true));
                });
            });
        };
    }
}
