

import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import model.Cuenta;
import model.Mesa;
import model.Notificacion;
import model.TipoNotificacion;
import org.junit.jupiter.api.*;
import repository.firestore.FirestoreNotificacionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración Real: FirestoreNotificacionRepository")
class FirestoreNotificacionRepositoryIntegrationTest {

    private FirestoreNotificacionRepository repository;
    private String testNotificacionId;
    private Cuenta cuentaDePrueba;
    private final Instant fechaCuenta = Instant.parse("2026-04-02T09:15:00Z");
    private final Instant fechaNotificacion = Instant.parse("2026-04-02T14:00:00Z");

    @BeforeAll
    void setup() {
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestoreNotificacionRepository(db);

        cuentaDePrueba = new Cuenta(
                "cuenta-test-notif-" + System.currentTimeMillis(),
                List.of(new Mesa("mesa-test-notif", 2)),
                false,
                Optional.empty(),
                fechaCuenta,
                Optional.empty()
        );
    }

    @Test
    @Order(1)
    @DisplayName("Debe guardar y recuperar una notificación")
    void shouldSaveAndRetrieveNotificacion() {
        Notificacion notificacion = new Notificacion(
                null,
                cuentaDePrueba,
                TipoNotificacion.Atencion,
                false,
                fechaNotificacion
        );

        Notificacion guardada = repository.save(notificacion);
        testNotificacionId = guardada.id();

        assertNotNull(testNotificacionId);

        Optional<Notificacion> recuperadaOpt = repository.findById(testNotificacionId);
        assertTrue(recuperadaOpt.isPresent());

        Notificacion recuperada = recuperadaOpt.get();
        assertNotNull(recuperada.cuenta());
        assertEquals(cuentaDePrueba.id(), recuperada.cuenta().id());
        assertEquals(TipoNotificacion.Atencion, recuperada.tipo());
        assertFalse(recuperada.leida());
        assertEquals(fechaNotificacion, recuperada.fecha());
    }

    @Test
    @Order(2)
    @DisplayName("Debe actualizar una notificación a leída")
    void shouldUpdateNotificacion() {
        Notificacion actualizada = new Notificacion(
                testNotificacionId,
                cuentaDePrueba,
                TipoNotificacion.Atencion,
                true,
                fechaNotificacion
        );

        Notificacion resultado = repository.update(testNotificacionId, actualizada);

        assertTrue(resultado.leida());

        Notificacion recuperada = repository.findById(testNotificacionId).orElseThrow();
        assertTrue(recuperada.leida());
    }

    @Test
    @Order(3)
    @DisplayName("Debe encontrar notificaciones por cuenta")
    void shouldFindNotificacionByCuenta() {
        List<Notificacion> notificaciones = repository.findByCuenta(cuentaDePrueba);

        assertFalse(notificaciones.isEmpty());
        assertTrue(notificaciones.stream().anyMatch(n -> testNotificacionId.equals(n.id())));
    }

    @Test
    @Order(4)
    @DisplayName("Debe encontrar notificaciones por tipo")
    void shouldFindNotificacionByTipo() {
        List<Notificacion> notificaciones = repository.findByTipoNotificacion(TipoNotificacion.Atencion);

        assertFalse(notificaciones.isEmpty());
        assertTrue(notificaciones.stream().anyMatch(n -> testNotificacionId.equals(n.id())));
    }

    @Test
    @Order(5)
    @DisplayName("Debe encontrar notificaciones por estado leída")
    void shouldFindNotificacionByLeida() {
        List<Notificacion> notificaciones = repository.findByLeida(true);

        assertFalse(notificaciones.isEmpty());
        assertTrue(notificaciones.stream().anyMatch(n -> testNotificacionId.equals(n.id())));
    }

    @Test
    @Order(6)
    @DisplayName("Debe borrar una notificación")
    void shouldDeleteNotificacion() {
        repository.deleteById(testNotificacionId);

        Optional<Notificacion> recuperada = repository.findById(testNotificacionId);
        assertTrue(recuperada.isEmpty());

        testNotificacionId = null;
    }

    @AfterAll
    void cleanup() {
        if (testNotificacionId != null) {
            repository.deleteById(testNotificacionId);
        }
    }
}