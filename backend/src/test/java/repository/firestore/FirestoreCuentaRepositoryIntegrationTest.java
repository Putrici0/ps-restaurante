package repository.firestore;

import config.FirebaseConfig;
import com.google.cloud.firestore.Firestore;
import model.Cuenta;
import model.Mesa;
import model.Reserva;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEST DE INTEGRACIÓN REAL (SENIOR)
 * Se conecta a la nube de Google Cloud Firestore para validar el repositorio de Cuentas.
 * Valida relaciones complejas (Listas y Optionals) y manejo de errores reales.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración Real: FirestoreCuentaRepository")
class FirestoreCuentaRepositoryIntegrationTest {

    private FirestoreCuentaRepository repository;
    private String testCuentaId;
    private Mesa mesaDePrueba;
    private Reserva reservaDePrueba;

    @BeforeAll
    void setup() {
        // CONEXIÓN REAL
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestoreCuentaRepository(db);
        
        // Datos base para los tests
        mesaDePrueba = new Mesa("mesa-test-" + System.currentTimeMillis(), 4);
        reservaDePrueba = new Reserva("res-test-" + System.currentTimeMillis(), "Cliente VIP Test", Instant.now(), 4, Instant.now());
        System.out.println("Firestore inicializado correctamente para los tests de Cuentas");
    }

    @Test
    @Order(1)
    @DisplayName("Debe persistir una Cuenta con List<Mesa> y Optional<Reserva> en la nube real")
    void shouldPersistComplexCuenta() {
        // Arrange
        Cuenta nuevaCuenta = new Cuenta(
            null, 
            List.of(mesaDePrueba), 
            false, 
            Optional.of(reservaDePrueba), 
            Instant.now(),
            Optional.empty() // Inicialmente sin fecha de pago
        );

        // Act: GUARDAR EN GOOGLE CLOUD
        Cuenta guardada = repository.save(nuevaCuenta);
        testCuentaId = guardada.id();

        // Assert
        assertNotNull(testCuentaId, "Google debería haber asignado un ID técnico");
        
        // Act: LEER DE LA NUBE
        Optional<Cuenta> recuperadaOpt = repository.findById(testCuentaId);
        assertTrue(recuperadaOpt.isPresent());
        
        Cuenta recuperada = recuperadaOpt.get();
        assertEquals(1, recuperada.mesas().size(), "La lista de mesas debe recuperarse íntegra");
        assertTrue(recuperada.reserva().isPresent(), "El Optional<Reserva> debe mapearse de vuelta correctamente");
        assertFalse(recuperada.fechaPago().isPresent(), "Una cuenta nueva no debe tener fecha de pago");
    }

    @Test
    @Order(2)
    @DisplayName("Debe actualizar una cuenta con fecha de pago (Simulación de Pago)")
    void shouldUpdateWithPaymentDate() {
        // Arrange
        Cuenta abierta = repository.findById(testCuentaId).orElseThrow();
        Instant fechaPago = Instant.now();
        
        Cuenta pagada = new Cuenta(
            abierta.id(),
            abierta.mesas(),
            true,
            abierta.reserva(),
            abierta.fechaCreacion(),
            Optional.of(fechaPago)
        );

        // Act
        repository.update(testCuentaId, pagada);

        // Assert
        Cuenta recuperada = repository.findById(testCuentaId).orElseThrow();
        assertTrue(recuperada.estaPagada());
        assertTrue(recuperada.fechaPago().isPresent());
        // Verificamos que la fecha recuperada coincide (Firestore tiene precisión de milisegundos)
        assertTrue(Math.abs(recuperada.fechaPago().get().toEpochMilli() - fechaPago.toEpochMilli()) < 1000);
    }

    @Test
    @Order(3)
    @DisplayName("Debe encontrar una cuenta buscando por una mesa específica (Array-Contains)")
    void shouldFindByMesaInCloud() {
        // Act: BUSCAR EN LA NUBE USANDO EL ATRIBUTO ANIDADO
        Optional<Cuenta> cuentaEncontrada = repository.findByMesa(mesaDePrueba);

        // Assert
        assertTrue(cuentaEncontrada.isPresent(), "Firestore debe encontrar la cuenta que contiene la mesa de prueba");
        assertEquals(testCuentaId, cuentaEncontrada.get().id());
    }

    @AfterAll
    void cleanup() {
        // BORRAR DE LA NUBE
        if (testCuentaId != null) {
            repository.deleteById(testCuentaId);
            System.out.println("Cuenta de prueba eliminada de Firestore: " + testCuentaId);
        }
    }
}
