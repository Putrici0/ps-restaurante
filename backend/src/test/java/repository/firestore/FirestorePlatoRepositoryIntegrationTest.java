package repository.firestore;

import config.FirebaseConfig;
import com.google.cloud.firestore.Firestore;
import model.Categoria;
import model.Plato;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración: FirestorePlatoRepository")
class FirestorePlatoRepositoryIntegrationTest {

    private FirestorePlatoRepository repository;
    private String testPlatoId;

    @BeforeAll
    void setup() {
        // Conexión real a Firestore a través de tu clase de configuración
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestorePlatoRepository(db);
        System.out.println("Firestore inicializado correctamente para los tests");
    }

    @Test
    @Order(1)
    @DisplayName("Debe guardar un plato real y verificar la precisión de los céntimos")
    void shouldSaveAndRetrieveRealPlato() {
        // Arrange
        Plato nuevoPlato = new Plato(
            null, 
            "Test Integración " + System.currentTimeMillis(), 
            Categoria.Principal, 
            "Plato de prueba para integración", 
            new BigDecimal("25.99"), 
            true
        );

        // Act
        Plato guardado = repository.save(nuevoPlato);
        testPlatoId = guardado.id();

        // Assert
        assertNotNull(testPlatoId, "El ID de Firestore no debería ser nulo");
        
        Optional<Plato> recuperadoOpt = repository.findById(testPlatoId);
        assertTrue(recuperadoOpt.isPresent(), "El plato debería existir en la base de datos");
        
        Plato recuperado = recuperadoOpt.get();
        assertEquals(new BigDecimal("25.99"), recuperado.precio(), "¡Éxito! El precio recuperado coincide al céntimo");
        assertTrue(recuperado.estaActivo());
    }

    @Test
    @Order(2)
    @DisplayName("Debe poder borrar un plato de la base de datos real")
    void shouldDeletePlato() {
        // Act
        repository.deleteById(testPlatoId);

        // Assert
        Optional<Plato> recuperado = repository.findById(testPlatoId);
        assertFalse(recuperado.isPresent(), "El plato debería haber sido eliminado de Firestore");
    }

    @AfterAll
    void tearDown() {
        System.out.println("Limpieza de tests finalizada");
    }
}
