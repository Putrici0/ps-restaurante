import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import config.FirestoreClientProvider;
import controller.CuentaController;
import controller.MesaController;
import controller.NotificacionController;
import controller.OrdenController;
import controller.PedidoController;
import controller.PlatoController;
import controller.ReservaController;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import repository.firestore.FirestoreCuentaRepository;
import repository.firestore.FirestoreMesaRepository;
import repository.firestore.FirestoreNotificacionRepository;
import repository.firestore.FirestoreOrdenRepository;
import repository.firestore.FirestorePedidoRepository;
import repository.firestore.FirestorePlatoRepository;
import repository.firestore.FirestoreReservaRepository;
import service.CuentaService;
import service.MesaApplicationService;
import service.MesaService;
import service.NotificacionService;
import service.OrdenService;
import service.PedidoService;
import service.PlatoService;
import service.ReservaService;
import service.application.HistorialCuentasApplicationService;
import service.application.NotificacionApplicationService;
import service.application.OrdenApplicationService;
import service.application.PagoApplicationService;
import service.application.PedidoApplicationService;
import util.ApiError;
import util.MesaSeeder;
import util.PlatoSeeder;
import controller.TiqueController;
import io.github.cdimascio.dotenv.Dotenv;
import service.TiqueEmailService;

import java.io.File;

public class Main {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        if (dotenv.get("EMAIL_FROM") == null) {
            dotenv = Dotenv.configure().directory("..").ignoreIfMissing().load();
        }
        if (dotenv.get("EMAIL_FROM") != null) {
            dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
            System.out.println("✅ Variables de entorno cargadas desde .env (Email: " + dotenv.get("EMAIL_FROM") + ")");
        } else {
            System.out.println("⚠️ No se encontró el archivo .env o faltan variables.");
        }

        FirebaseConfig.init();
        Firestore db = FirestoreClientProvider.getFirestore();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        FirestorePlatoRepository platoRepository = new FirestorePlatoRepository(db);
        FirestoreMesaRepository mesaRepository = new FirestoreMesaRepository(db);
        FirestoreReservaRepository reservaRepository = new FirestoreReservaRepository(db);
        FirestoreCuentaRepository cuentaRepository = new FirestoreCuentaRepository(db);
        FirestorePedidoRepository pedidoRepository = new FirestorePedidoRepository(db);
        FirestoreOrdenRepository ordenRepository = new FirestoreOrdenRepository(db);
        FirestoreNotificacionRepository notificacionRepository = new FirestoreNotificacionRepository(db);

        MesaSeeder.seed(mesaRepository);
        PlatoSeeder.seed(platoRepository);

        PlatoService platoService = new PlatoService(platoRepository);
        MesaService mesaService = new MesaService(mesaRepository);
        ReservaService reservaService = new ReservaService(reservaRepository);
        CuentaService cuentaService = new CuentaService(cuentaRepository, mesaRepository, reservaRepository);
        PedidoService pedidoService = new PedidoService(pedidoRepository, cuentaRepository);
        OrdenService ordenService = new OrdenService(ordenRepository, pedidoRepository, platoRepository);
        NotificacionService notificacionService = new NotificacionService(notificacionRepository, cuentaRepository);
        TiqueEmailService tiqueEmailService = new TiqueEmailService();

        MesaApplicationService mesaApplicationService = new MesaApplicationService(
                mesaRepository,
                cuentaRepository,
                pedidoRepository,
                ordenRepository
        );

        PedidoApplicationService pedidoApplicationService = new PedidoApplicationService(
                pedidoRepository,
                cuentaRepository,
                ordenRepository,
                platoRepository,
                mesaApplicationService
        );

        OrdenApplicationService ordenApplicationService = new OrdenApplicationService(
                ordenRepository,
                pedidoRepository,
                platoRepository,
                pedidoApplicationService,
                cuentaRepository
        );

        PagoApplicationService pagoApplicationService = new PagoApplicationService(
                cuentaRepository,
                pedidoRepository,
                ordenRepository
        );

        NotificacionApplicationService notificacionApplicationService = new NotificacionApplicationService(
                notificacionRepository,
                cuentaRepository,
                ordenApplicationService
        );

        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                notificacionApplicationService::limpiarNotificacionesEstancadas,
                1, 1, java.util.concurrent.TimeUnit.MINUTES
        );

        HistorialCuentasApplicationService historialCuentasApplicationService =
                new HistorialCuentasApplicationService(cuentaRepository, pedidoRepository, ordenRepository);

        util.SyntheticDataGenerator generator = new util.SyntheticDataGenerator(platoRepository.findAll());
        String statsPath = "backend/src/main/resources/historico_sintetico.json";
        File statsFile = new File(statsPath);
        if (!statsFile.exists()) {
            System.out.println("No se detectó histórico de estadísticas. Generando datos iniciales...");
            generator.generarYExportar(30, statsPath);
        }

        PlatoController platoController = new PlatoController(platoService);
        MesaController mesaController = new MesaController(mesaService, mesaApplicationService);
        ReservaController reservaController = new ReservaController(reservaService);
        CuentaController cuentaController = new CuentaController(
                cuentaService,
                pagoApplicationService,
                historialCuentasApplicationService
        );
        PedidoController pedidoController = new PedidoController(pedidoService, pedidoApplicationService);
        OrdenController ordenController = new OrdenController(
                ordenService,
                ordenApplicationService,
                notificacionApplicationService
        );
        NotificacionController notificacionController = new NotificacionController(
                notificacionService,
                notificacionApplicationService
        );

        TiqueController tiqueController = new TiqueController(tiqueEmailService);

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));

            config.routes.get("/", ctx -> ctx.result("API del restaurante funcionando"));
            config.routes.get("/health", ctx -> ctx.result("OK"));

            config.routes.get("/debug/generate-stats-data", ctx -> {
                int days = ctx.queryParamAsClass("days", Integer.class).getOrDefault(30);
                String path = "backend/src/main/resources/historico_sintetico.json";
                generator.generarYExportar(days, path);
                ctx.result("Generados datos de los últimos " + days + " días en " + path);
            });

            config.routes.get("/debug/get-stats-data", ctx -> {
                File file = new File("backend/src/main/resources/historico_sintetico.json");
                if (!file.exists()) {
                    ctx.status(404).result("El archivo no existe. Generalo primero con /debug/generate-stats-data");
                    return;
                }
                ctx.contentType("application/json").result(new java.io.FileInputStream(file));
            });

            config.routes.apiBuilder(platoController.routes());
            config.routes.apiBuilder(mesaController.routes());
            config.routes.apiBuilder(reservaController.routes());
            config.routes.apiBuilder(cuentaController.routes());
            config.routes.apiBuilder(pedidoController.routes());
            config.routes.apiBuilder(ordenController.routes());
            config.routes.apiBuilder(notificacionController.routes());
            config.routes.apiBuilder(tiqueController.routes());

            config.routes.exception(IllegalArgumentException.class, (e, ctx) -> {
                ctx.status(400);
                ctx.json(new ApiError(e.getMessage()));
            });

            config.routes.exception(Exception.class, (e, ctx) -> {
                e.printStackTrace();
                ctx.status(500);
                ctx.json(new ApiError("Error interno del servidor"));
            });
        });

        app.start(7070);
    }
}
