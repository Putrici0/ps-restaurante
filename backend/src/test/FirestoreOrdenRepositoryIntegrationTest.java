
import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import model.*;
import org.junit.jupiter.api.*;
import repository.firestore.FirestoreOrdenRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración Real: FirestoreOrdenRepository")
class FirestoreOrdenRepositoryIntegrationTest {

    private FirestoreOrdenRepository repository;
    private String testOrdenId;
    private Pedido pedidoDePrueba;
    private Plato platoDePrueba;
    private final Instant fechaPedido = Instant.parse("2026-04-02T13:30:00Z");
    private final Instant fechaOrden = Instant.parse("2026-04-02T13:35:00Z");

    @BeforeAll
    void setup() {
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestoreOrdenRepository(db);

        Cuenta cuenta = new Cuenta(
                "cuenta-test-orden",
                List.of(),
                false,
                Optional.empty(),
                Instant.parse("2026-04-02T09:00:00Z"),
                Optional.empty()
        );

        pedidoDePrueba = new Pedido(
                "pedido-test-" + System.currentTimeMillis(),
                cuenta,
                PedidoEstado.Pendiente,
                fechaPedido
        );

        platoDePrueba = new Plato(
                "plato-test-" + System.currentTimeMillis(),
                "Pizza Test",
                Categoria.Principal,
                "Pizza de prueba",
                new BigDecimal("12.50"),
                true
        );
    }

    @Test
    @Order(1)
    @DisplayName("Debe guardar y recuperar una orden")
    void shouldSaveAndRetrieveOrden() {
        Orden orden = new Orden(
                null,
                pedidoDePrueba,
                platoDePrueba,
                new BigDecimal("12.50"),
                OrdenEstado.Pendiente,
                fechaOrden,
                "Sin cebolla"
        );

        Orden guardada = repository.save(orden);
        testOrdenId = guardada.id();

        assertNotNull(testOrdenId);

        Optional<Orden> recuperadaOpt = repository.findById(testOrdenId);
        assertTrue(recuperadaOpt.isPresent());

        Orden recuperada = recuperadaOpt.get();
        assertNotNull(recuperada.pedido());
        assertEquals(pedidoDePrueba.id(), recuperada.pedido().id());
        assertNotNull(recuperada.plato());
        assertEquals(platoDePrueba.id(), recuperada.plato().id());
        assertEquals("Pizza Test", recuperada.plato().nombre());
        assertEquals(new BigDecimal("12.50"), recuperada.precio());
        assertEquals(OrdenEstado.Pendiente, recuperada.ordenEstado());
        assertEquals("Sin cebolla", recuperada.detalles());
    }

    @Test
    @Order(2)
    @DisplayName("Debe actualizar el estado de una orden")
    void shouldUpdateOrdenEstado() {
        Orden actualizada = new Orden(
                testOrdenId,
                pedidoDePrueba,
                platoDePrueba,
                new BigDecimal("12.50"),
                OrdenEstado.Listo,
                fechaOrden,
                "Sin cebolla"
        );

        Orden resultado = repository.update(testOrdenId, actualizada);

        assertEquals(OrdenEstado.Listo, resultado.ordenEstado());

        Orden recuperada = repository.findById(testOrdenId).orElseThrow();
        assertEquals(OrdenEstado.Listo, recuperada.ordenEstado());
    }

    @Test
    @Order(3)
    @DisplayName("Debe encontrar órdenes por pedido")
    void shouldFindOrdenByPedido() {
        List<Orden> ordenes = repository.findByPedido(pedidoDePrueba);

        assertFalse(ordenes.isEmpty());
        assertTrue(ordenes.stream().anyMatch(o -> testOrdenId.equals(o.id())));
    }

    @Test
    @Order(4)
    @DisplayName("Debe encontrar órdenes por estado")
    void shouldFindOrdenByEstado() {
        List<Orden> ordenes = repository.findByEstado(OrdenEstado.Listo);

        assertFalse(ordenes.isEmpty());
        assertTrue(ordenes.stream().anyMatch(o -> testOrdenId.equals(o.id())));
    }

    @Test
    @Order(5)
    @DisplayName("Debe borrar una orden")
    void shouldDeleteOrden() {
        repository.deleteById(testOrdenId);

        Optional<Orden> recuperada = repository.findById(testOrdenId);
        assertTrue(recuperada.isEmpty());

        testOrdenId = null;
    }

    @AfterAll
    void cleanup() {
        if (testOrdenId != null) {
            repository.deleteById(testOrdenId);
        }
    }
}