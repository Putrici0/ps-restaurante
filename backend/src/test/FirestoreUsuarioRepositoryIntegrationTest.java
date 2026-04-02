

import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import model.Rol;
import model.Usuario;
import org.junit.jupiter.api.*;
import repository.firestore.FirestoreUsuarioRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de Integración Real: FirestoreUsuarioRepository")
class FirestoreUsuarioRepositoryIntegrationTest {

    private FirestoreUsuarioRepository repository;
    private String testUsuarioId;
    private final String username = "usuario-test-" + System.currentTimeMillis();
    private final Instant fechaCreacion = Instant.parse("2026-04-02T08:00:00Z");

    @BeforeAll
    void setup() {
        Firestore db = FirebaseConfig.getFirestore();
        repository = new FirestoreUsuarioRepository(db);
    }

    @Test
    @Order(1)
    @DisplayName("Debe guardar y recuperar un usuario")
    void shouldSaveAndRetrieveUsuario() {
        Usuario usuario = new Usuario(
                null,
                username,
                "hash-seguro-test",
                Rol.Camarero,
                fechaCreacion
        );

        Usuario guardado = repository.save(usuario);
        testUsuarioId = guardado.id();

        assertNotNull(testUsuarioId);

        Optional<Usuario> recuperadoOpt = repository.findById(testUsuarioId);
        assertTrue(recuperadoOpt.isPresent());

        Usuario recuperado = recuperadoOpt.get();
        assertEquals(username, recuperado.username());
        assertEquals("hash-seguro-test", recuperado.passwordHash());
        assertEquals(Rol.Camarero, recuperado.rol());
        assertEquals(fechaCreacion, recuperado.fechaCreacion());
    }

    @Test
    @Order(2)
    @DisplayName("Debe actualizar el rol de un usuario")
    void shouldUpdateUsuario() {
        Usuario actualizado = new Usuario(
                testUsuarioId,
                username,
                "hash-seguro-test",
                Rol.Gerente,
                fechaCreacion
        );

        Usuario resultado = repository.update(testUsuarioId, actualizado);

        assertEquals(Rol.Gerente, resultado.rol());

        Usuario recuperado = repository.findById(testUsuarioId).orElseThrow();
        assertEquals(Rol.Gerente, recuperado.rol());
    }

    @Test
    @Order(3)
    @DisplayName("Debe encontrar usuarios por nombre")
    void shouldFindUsuarioByNombre() {
        List<Usuario> usuarios = repository.findByNombre(username);

        assertFalse(usuarios.isEmpty());
        assertTrue(usuarios.stream().anyMatch(u -> testUsuarioId.equals(u.id())));
    }

    @Test
    @Order(4)
    @DisplayName("Debe encontrar usuarios por rol")
    void shouldFindUsuarioByRol() {
        List<Usuario> usuarios = repository.findByRol(Rol.Gerente);

        assertFalse(usuarios.isEmpty());
        assertTrue(usuarios.stream().anyMatch(u -> testUsuarioId.equals(u.id())));
    }

    @Test
    @Order(5)
    @DisplayName("Debe borrar un usuario")
    void shouldDeleteUsuario() {
        repository.deleteById(testUsuarioId);

        Optional<Usuario> recuperado = repository.findById(testUsuarioId);
        assertTrue(recuperado.isEmpty());

        testUsuarioId = null;
    }

    @AfterAll
    void cleanup() {
        if (testUsuarioId != null) {
            repository.deleteById(testUsuarioId);
        }
    }
}