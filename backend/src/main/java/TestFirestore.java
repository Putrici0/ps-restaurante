import model.Categoria;
import model.Plato;
import config.FirebaseConfig;
import repository.PlatoRepository;

public class TestFirestore {

    public static void main(String[] args) {

        FirebaseConfig.init();

        try {

            PlatoRepository repo = new PlatoRepository();

            Plato plato = new Plato(
                    "plato_test_1",
                    "Pizza BBQ",
                    Categoria.Principal,
                    "Pizza con salsa barbacoa",
                    9.95,
                    true
            );

            repo.save(plato);

            System.out.println("Guardado OK");

            Plato resultado = repo.findById("plato_test_1");

            System.out.println("Leído desde Firestore:");

            System.out.println(resultado);

        } catch (Exception e) {

            e.printStackTrace();

        }

    }

}