import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import model.Mesa;
import org.junit.jupiter.api.*;
import repository.firestore.FirestoreMesaRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración Real: FirestoreMesaRepository")
class FirestoreMesaRepositoryIntegrationTest {

    private FirestoreMesaRepository repository;
    private String testMesaId;

    @BeforeAll
    void setup() {
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestoreMesaRepository(db);
    }

    @Test
    @Order(1)
    @DisplayName("Debe guardar y recuperar una mesa")
    void shouldSaveAndRetrieveMesa() {
        Mesa mesa = new Mesa(null, 4);

        Mesa guardada = repository.save(mesa);
        testMesaId = guardada.id();

        assertNotNull(testMesaId);

        Optional<Mesa> recuperadaOpt = repository.findById(testMesaId);
        assertTrue(recuperadaOpt.isPresent());

        Mesa recuperada = recuperadaOpt.get();
        assertEquals(4, recuperada.capacidad());
    }

    @Test
    @Order(2)
    @DisplayName("Debe actualizar la capacidad de una mesa")
    void shouldUpdateMesa() {
        Mesa actual = repository.findById(testMesaId).orElseThrow();

        Mesa actualizada = new Mesa(actual.id(), 6);
        Mesa resultado = repository.update(testMesaId, actualizada);

        assertEquals(6, resultado.capacidad());

        Mesa recuperada = repository.findById(testMesaId).orElseThrow();
        assertEquals(6, recuperada.capacidad());
    }

    @Test
    @Order(3)
    @DisplayName("Debe encontrar mesas por capacidad")
    void shouldFindMesaByCapacidad() {
        List<Mesa> mesas = repository.findByCapacidad(6);

        assertFalse(mesas.isEmpty());
        assertTrue(mesas.stream().anyMatch(m -> testMesaId.equals(m.id())));
    }

    @Test
    @Order(4)
    @DisplayName("Debe borrar una mesa")
    void shouldDeleteMesa() {
        repository.deleteById(testMesaId);

        Optional<Mesa> recuperada = repository.findById(testMesaId);
        assertTrue(recuperada.isEmpty());

        testMesaId = null;
    }

    @AfterAll
    void cleanup() {
        if (testMesaId != null) {
            repository.deleteById(testMesaId);
        }
    }
}