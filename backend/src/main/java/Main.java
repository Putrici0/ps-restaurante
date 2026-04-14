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
import service.MesaService;
import service.NotificacionService;
import service.OrdenService;
import service.PedidoService;
import service.PlatoService;
import service.ReservaService;
import service.PagoService;
import util.ApiError;
import util.MesaSeeder;
import util.PlatoSeeder;

public class Main {

    public static void main(String[] args) {
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

        PlatoService platoService = new PlatoService(platoRepository);
        MesaService mesaService = new MesaService(mesaRepository, cuentaRepository, pedidoRepository, ordenRepository);
        ReservaService reservaService = new ReservaService(reservaRepository);
        CuentaService cuentaService = new CuentaService(cuentaRepository, mesaRepository, reservaRepository);
        PedidoService pedidoService = new PedidoService(
                pedidoRepository,
                cuentaRepository,
                ordenRepository,
                platoRepository,
                mesaService
        );
        OrdenService ordenService = new OrdenService(
                ordenRepository,
                pedidoRepository,
                platoRepository,
                pedidoService,
                cuentaRepository
        );
        NotificacionService notificacionService = new NotificacionService(notificacionRepository, cuentaRepository);

        PagoService pagoService = new PagoService(
                cuentaRepository,
                pedidoRepository,
                ordenRepository
        );

        PlatoController platoController = new PlatoController(platoService);
        MesaController mesaController = new MesaController(mesaService);
        ReservaController reservaController = new ReservaController(reservaService);
        CuentaController cuentaController = new CuentaController(cuentaService, pagoService);
        PedidoController pedidoController = new PedidoController(pedidoService);
        OrdenController ordenController = new OrdenController(
                ordenService,
                notificacionService
        );
        NotificacionController notificacionController = new NotificacionController(
                notificacionService
        );

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));

            config.routes.get("/", ctx -> ctx.result("API del restaurante funcionando"));
            config.routes.get("/health", ctx -> ctx.result("OK"));

            config.routes.apiBuilder(platoController.routes());
            config.routes.apiBuilder(mesaController.routes());
            config.routes.apiBuilder(reservaController.routes());
            config.routes.apiBuilder(cuentaController.routes());
            config.routes.apiBuilder(pedidoController.routes());
            config.routes.apiBuilder(ordenController.routes());
            config.routes.apiBuilder(notificacionController.routes());

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