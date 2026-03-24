import config.FirebaseConfig;
import model.Categoria;
import model.Plato;
import repository.PlatoRepository;

public class Main {

    public static void main(String[] args) {

        System.out.println("Iniciando test Firestore...");

        try {

            // Inicializar Firebase
            FirebaseConfig.init();

            // Crear repository
            PlatoRepository repo = new PlatoRepository();

            // Crear objeto de prueba
            Plato plato = new Plato(
                    "plato_test_main",
                    "Hamburguesa BBQ",
                    Categoria.Principal,
                    "Hamburguesa con salsa BBQ",
                    10.50,
                    true
            );

            // Guardar en Firestore
            repo.save(plato);

            System.out.println("Plato guardado correctamente");

            // Recuperar desde Firestore
            Plato resultado = repo.findById("plato_test_main");

            System.out.println("Plato recuperado desde Firestore:");

            System.out.println(resultado);

        } catch (Exception e) {

            System.out.println("Error ejecutando test");

            e.printStackTrace();

        }

    }
}