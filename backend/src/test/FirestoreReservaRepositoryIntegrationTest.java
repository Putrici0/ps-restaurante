
import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import model.Reserva;
import org.junit.jupiter.api.*;
import repository.firestore.FirestoreReservaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración Real: FirestoreReservaRepository")
class FirestoreReservaRepositoryIntegrationTest {

    private FirestoreReservaRepository repository;
    private String testReservaId;
    private final Instant fechaReserva = Instant.parse("2026-04-02T12:00:00Z");
    private final Instant fechaCreacion = Instant.parse("2026-04-02T10:00:00Z");

    @BeforeAll
    void setup() {
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestoreReservaRepository(db);
    }

    @Test
    @Order(1)
    @DisplayName("Debe guardar y recuperar una reserva")
    void shouldSaveAndRetrieveReserva() {
        Reserva reserva = new Reserva(
                null,
                "Cliente Test Reserva",
                fechaReserva,
                4,
                fechaCreacion
        );

        Reserva guardada = repository.save(reserva);
        testReservaId = guardada.id();

        assertNotNull(testReservaId);

        Optional<Reserva> recuperadaOpt = repository.findById(testReservaId);
        assertTrue(recuperadaOpt.isPresent());

        Reserva recuperada = recuperadaOpt.get();
        assertEquals("Cliente Test Reserva", recuperada.nombre());
        assertEquals(fechaReserva, recuperada.fecha());
        assertEquals(4, recuperada.capacidad());
        assertEquals(fechaCreacion, recuperada.fechaCreacion());
    }

    @Test
    @Order(2)
    @DisplayName("Debe actualizar una reserva")
    void shouldUpdateReserva() {
        Reserva actualizada = new Reserva(
                testReservaId,
                "Cliente Test Reserva Actualizado",
                fechaReserva,
                6,
                fechaCreacion
        );

        Reserva resultado = repository.update(testReservaId, actualizada);

        assertEquals(6, resultado.capacidad());

        Reserva recuperada = repository.findById(testReservaId).orElseThrow();
        assertEquals("Cliente Test Reserva Actualizado", recuperada.nombre());
        assertEquals(6, recuperada.capacidad());
    }

    @Test
    @Order(3)
    @DisplayName("Debe encontrar reservas por fecha")
    void shouldFindReservaByFecha() {
        List<Reserva> reservas = repository.findByFecha(fechaReserva);

        assertFalse(reservas.isEmpty());
        assertTrue(reservas.stream().anyMatch(r -> testReservaId.equals(r.id())));
    }

    @Test
    @Order(4)
    @DisplayName("Debe borrar una reserva")
    void shouldDeleteReserva() {
        repository.deleteById(testReservaId);

        Optional<Reserva> recuperada = repository.findById(testReservaId);
        assertTrue(recuperada.isEmpty());

        testReservaId = null;
    }

    @AfterAll
    void cleanup() {
        if (testReservaId != null) {
            repository.deleteById(testReservaId);
        }
    }
}