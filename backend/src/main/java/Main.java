import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import controller.MesaController;
import controller.PlatoController;
import controller.ReservaController;
import io.javalin.Javalin;
import repository.firestore.FirestoreMesaRepository;
import repository.firestore.FirestorePlatoRepository;
import repository.firestore.FirestoreReservaRepository;
import service.MesaService;
import service.PlatoService;
import service.ReservaService;
import util.ApiError;

public class Main {

    public static void main(String[] args) {
        Firestore db = FirebaseConfig.getFirestore();

        FirestorePlatoRepository platoRepository = new FirestorePlatoRepository(db);
        FirestoreMesaRepository mesaRepository = new FirestoreMesaRepository(db);
        FirestoreReservaRepository reservaRepository = new FirestoreReservaRepository(db);

        PlatoService platoService = new PlatoService(platoRepository);
        MesaService mesaService = new MesaService(mesaRepository);
        ReservaService reservaService = new ReservaService(reservaRepository);

        PlatoController platoController = new PlatoController(platoService);
        MesaController mesaController = new MesaController(mesaService);
        ReservaController reservaController = new ReservaController(reservaService);

        Javalin app = Javalin.create(config -> {

            config.routes.get("/", ctx -> ctx.result("API del restaurante funcionando"));
            config.routes.get("/health", ctx -> ctx.result("OK"));

            config.routes.apiBuilder(platoController.routes());
            config.routes.apiBuilder(mesaController.routes());
            config.routes.apiBuilder(reservaController.routes());

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