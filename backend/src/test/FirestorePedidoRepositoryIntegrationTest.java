
import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import model.Cuenta;
import model.Mesa;
import model.Pedido;
import model.PedidoEstado;
import org.junit.jupiter.api.*;
import repository.firestore.FirestorePedidoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración Real: FirestorePedidoRepository")
class FirestorePedidoRepositoryIntegrationTest {

    private FirestorePedidoRepository repository;
    private String testPedidoId;
    private Cuenta cuentaDePrueba;
    private final Instant fechaCreacionCuenta = Instant.parse("2026-04-02T09:00:00Z");
    private final Instant fechaPedido = Instant.parse("2026-04-02T13:00:00Z");

    @BeforeAll
    void setup() {
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestorePedidoRepository(db);

        cuentaDePrueba = new Cuenta(
                "cuenta-test-" + System.currentTimeMillis(),
                List.of(new Mesa("mesa-test-pedido", 4)),
                false,
                Optional.empty(),
                fechaCreacionCuenta,
                Optional.empty()
        );
    }

    @Test
    @Order(1)
    @DisplayName("Debe guardar y recuperar un pedido")
    void shouldSaveAndRetrievePedido() {
        Pedido pedido = new Pedido(
                null,
                cuentaDePrueba,
                PedidoEstado.Pendiente,
                fechaPedido
        );

        Pedido guardado = repository.save(pedido);
        testPedidoId = guardado.id();

        assertNotNull(testPedidoId);

        Optional<Pedido> recuperadoOpt = repository.findById(testPedidoId);
        assertTrue(recuperadoOpt.isPresent());

        Pedido recuperado = recuperadoOpt.get();
        assertNotNull(recuperado.cuenta());
        assertEquals(cuentaDePrueba.id(), recuperado.cuenta().id());
        assertEquals(PedidoEstado.Pendiente, recuperado.pedidoEstado());
        assertEquals(fechaPedido, recuperado.fechaPedido());
    }

    @Test
    @Order(2)
    @DisplayName("Debe actualizar el estado de un pedido")
    void shouldUpdatePedidoEstado() {
        Pedido actualizado = new Pedido(
                testPedidoId,
                cuentaDePrueba,
                PedidoEstado.Listo,
                fechaPedido
        );

        Pedido resultado = repository.update(testPedidoId, actualizado);

        assertEquals(PedidoEstado.Listo, resultado.pedidoEstado());

        Pedido recuperado = repository.findById(testPedidoId).orElseThrow();
        assertEquals(PedidoEstado.Listo, recuperado.pedidoEstado());
    }

    @Test
    @Order(3)
    @DisplayName("Debe encontrar pedidos por cuenta")
    void shouldFindPedidoByCuenta() {
        List<Pedido> pedidos = repository.findByCuenta(cuentaDePrueba);

        assertFalse(pedidos.isEmpty());
        assertTrue(pedidos.stream().anyMatch(p -> testPedidoId.equals(p.id())));
    }

    @Test
    @Order(4)
    @DisplayName("Debe encontrar pedidos por estado")
    void shouldFindPedidoByEstado() {
        List<Pedido> pedidos = repository.findByEstado(PedidoEstado.Listo);

        assertFalse(pedidos.isEmpty());
        assertTrue(pedidos.stream().anyMatch(p -> testPedidoId.equals(p.id())));
    }

    @Test
    @Order(5)
    @DisplayName("Debe borrar un pedido")
    void shouldDeletePedido() {
        repository.deleteById(testPedidoId);

        Optional<Pedido> recuperado = repository.findById(testPedidoId);
        assertTrue(recuperado.isEmpty());

        testPedidoId = null;
    }

    @AfterAll
    void cleanup() {
        if (testPedidoId != null) {
            repository.deleteById(testPedidoId);
        }
    }
}
