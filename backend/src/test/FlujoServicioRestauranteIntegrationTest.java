

import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import model.*;
import org.junit.jupiter.api.*;
import repository.firestore.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Flujo completo de servicio en restaurante")
class FlujoServicioRestauranteIntegrationTest {

    private FirestoreMesaRepository mesaRepository;
    private FirestoreCuentaRepository cuentaRepository;
    private FirestorePlatoRepository platoRepository;
    private FirestorePedidoRepository pedidoRepository;
    private FirestoreOrdenRepository ordenRepository;
    private FirestoreNotificacionRepository notificacionRepository;
    private FirestoreUsuarioRepository usuarioRepository;

    private String mesaId;
    private String cuentaId;
    private String platoId;
    private String pedidoId;
    private String ordenId;
    private String notificacionId;
    private String camareroId;
    private String cocineroId;

    private final Instant ahora = Instant.parse("2026-04-02T20:00:00Z");

    @BeforeAll
    void setup() {
        Firestore db = FirebaseConfig.getFirestore();

        mesaRepository = new FirestoreMesaRepository(db);
        cuentaRepository = new FirestoreCuentaRepository(db);
        platoRepository = new FirestorePlatoRepository(db);
        pedidoRepository = new FirestorePedidoRepository(db);
        ordenRepository = new FirestoreOrdenRepository(db);
        notificacionRepository = new FirestoreNotificacionRepository(db);
        usuarioRepository = new FirestoreUsuarioRepository(db);

        System.out.println("\n==============================================");
        System.out.println("  INICIALIZANDO TEST DE FLUJO DEL RESTAURANTE");
        System.out.println("==============================================\n");
    }

    @Test
    @Order(1)
    @DisplayName("Debe simular el flujo completo desde llegada del cliente hasta entrega")
    void shouldSimulateFullRestaurantFlow() {

        System.out.println("========== INICIO FLUJO RESTAURANTE ==========\n");

        // 1) Crear usuarios
        System.out.println("1. Creando usuarios del sistema...");

        Usuario camarero = usuarioRepository.save(new Usuario(
                null,
                "camarero-" + System.currentTimeMillis(),
                "hash-camarero",
                Rol.Camarero,
                ahora
        ));
        camareroId = camarero.id();

        Usuario cocinero = usuarioRepository.save(new Usuario(
                null,
                "cocinero-" + System.currentTimeMillis(),
                "hash-cocinero",
                Rol.Cocinero,
                ahora
        ));
        cocineroId = cocinero.id();

        System.out.println("   ✅ Camarero creado con ID: " + camareroId);
        System.out.println("   ✅ Cocinero creado con ID: " + cocineroId);

        assertNotNull(camareroId);
        assertNotNull(cocineroId);

        // 2) Crear mesa
        System.out.println("\n2. Creando una mesa disponible...");

        Mesa mesa = mesaRepository.save(new Mesa(null, 4));
        mesaId = mesa.id();

        System.out.println("   ✅ Mesa creada con ID: " + mesaId + " | Capacidad: 4");

        assertNotNull(mesaId);

        // 3) Ocupar mesa creando una cuenta
        System.out.println("\n3. Entra un cliente y el camarero le asigna la mesa...");
        System.out.println("   ℹ️ El modelo actual no tiene campo 'ocupada', así que se simula");
        System.out.println("      ocupando la mesa mediante la creación de una cuenta.");

        Cuenta cuenta = cuentaRepository.save(new Cuenta(
                null,
                List.of(mesa),
                false,
                Optional.empty(),
                ahora,
                Optional.empty()
        ));
        cuentaId = cuenta.id();

        System.out.println("   ✅ Cuenta creada con ID: " + cuentaId);
        System.out.println("   ✅ Mesa " + mesaId + " asociada a la cuenta");
        System.out.println("   🔐 Contraseña del cliente simulada: 1234");
        System.out.println("      (solo impresa, porque el modelo actual no la guarda)");

        assertNotNull(cuentaId);

        Optional<Cuenta> cuentaRecuperada = cuentaRepository.findById(cuentaId);
        assertTrue(cuentaRecuperada.isPresent());
        assertEquals(1, cuentaRecuperada.get().mesas().size());
        assertEquals(mesaId, cuentaRecuperada.get().mesas().getFirst().id());
        assertFalse(cuentaRecuperada.get().estaPagada());

        System.out.println("   ✅ Verificación correcta: cuenta recuperada desde Firestore");

        // 4) Crear plato
        System.out.println("\n4. Preparando un plato disponible en carta...");

        Plato plato = platoRepository.save(new Plato(
                null,
                "Pizza barbacoa",
                Categoria.Principal,
                "Pizza familiar",
                new BigDecimal("12.50"),
                true
        ));
        platoId = plato.id();

        System.out.println("   ✅ Plato creado con ID: " + platoId);
        System.out.println("   ✅ Nombre: " + plato.nombre());
        System.out.println("   ✅ Precio: " + plato.precio() + "€");

        assertNotNull(platoId);

        // 5) Cliente hace un pedido
        System.out.println("\n5. El cliente hace un pedido desde su mesa...");

        Pedido pedido = pedidoRepository.save(new Pedido(
                null,
                cuenta,
                PedidoEstado.Pendiente,
                ahora.plusSeconds(60)
        ));
        pedidoId = pedido.id();

        System.out.println("   ✅ Pedido creado con ID: " + pedidoId);
        System.out.println("   ✅ Estado inicial del pedido: " + PedidoEstado.Pendiente);

        assertNotNull(pedidoId);

        Optional<Pedido> pedidoRecuperado = pedidoRepository.findById(pedidoId);
        assertTrue(pedidoRecuperado.isPresent());
        assertEquals(PedidoEstado.Pendiente, pedidoRecuperado.get().pedidoEstado());
        assertEquals(cuentaId, pedidoRecuperado.get().cuenta().id());

        System.out.println("   ✅ Verificación correcta: pedido recuperado desde Firestore");

        // 6) Crear orden en cocina
        System.out.println("\n6. Cocina recibe la orden del pedido...");

        Orden orden = ordenRepository.save(new Orden(
                null,
                pedido,
                plato,
                plato.precio(),
                OrdenEstado.Pendiente,
                ahora.plusSeconds(90),
                "Sin cebolla"
        ));
        ordenId = orden.id();

        System.out.println("   ✅ Orden creada con ID: " + ordenId);
        System.out.println("   ✅ Estado inicial de la orden: " + OrdenEstado.Pendiente);
        System.out.println("   ✅ Detalles: Sin cebolla");

        assertNotNull(ordenId);

        Optional<Orden> ordenRecuperada = ordenRepository.findById(ordenId);
        assertTrue(ordenRecuperada.isPresent());
        assertEquals(OrdenEstado.Pendiente, ordenRecuperada.get().ordenEstado());

        System.out.println("   ✅ Verificación correcta: orden recuperada desde Firestore");

        // 7) Cocina empieza a preparar
        System.out.println("\n7. Cocina empieza a preparar el plato...");

        Orden ordenEnPreparacion = ordenRepository.update(ordenId, new Orden(
                ordenId,
                pedido,
                plato,
                plato.precio(),
                OrdenEstado.Preparación,
                ahora.plusSeconds(120),
                "Sin cebolla"
        ));

        System.out.println("   🔄 Orden actualizada a estado: " + ordenEnPreparacion.ordenEstado());

        assertEquals(OrdenEstado.Preparación, ordenEnPreparacion.ordenEstado());

        Orden ordenPreparacionRecuperada = ordenRepository.findById(ordenId).orElseThrow();
        assertEquals(OrdenEstado.Preparación, ordenPreparacionRecuperada.ordenEstado());

        System.out.println("   ✅ Verificación correcta: orden en preparación");

        // 8) Cocina termina el plato
        System.out.println("\n8. Cocina termina el plato y lo marca como listo...");

        Orden ordenLista = ordenRepository.update(ordenId, new Orden(
                ordenId,
                pedido,
                plato,
                plato.precio(),
                OrdenEstado.Listo,
                ahora.plusSeconds(180),
                "Sin cebolla"
        ));

        System.out.println("   🎉 Orden actualizada a estado: " + ordenLista.ordenEstado());

        assertEquals(OrdenEstado.Listo, ordenLista.ordenEstado());

        Orden ordenListaRecuperada = ordenRepository.findById(ordenId).orElseThrow();
        assertEquals(OrdenEstado.Listo, ordenListaRecuperada.ordenEstado());

        System.out.println("   ✅ Verificación correcta: orden lista");

        // 9) Pedido completo pasa a listo
        System.out.println("\n9. El pedido completo se marca como listo...");

        Pedido pedidoListo = pedidoRepository.update(pedidoId, new Pedido(
                pedidoId,
                cuenta,
                PedidoEstado.Listo,
                ahora.plusSeconds(180)
        ));

        System.out.println("   📦 Pedido actualizado a estado: " + pedidoListo.pedidoEstado());

        assertEquals(PedidoEstado.Listo, pedidoListo.pedidoEstado());

        Pedido pedidoListoRecuperado = pedidoRepository.findById(pedidoId).orElseThrow();
        assertEquals(PedidoEstado.Listo, pedidoListoRecuperado.pedidoEstado());

        System.out.println("   ✅ Verificación correcta: pedido listo");

        // 10) Crear notificación para camarero
        System.out.println("\n10. Se notifica al camarero que debe recoger el plato...");

        Notificacion notificacion = notificacionRepository.save(new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Recoger,
                false,
                ahora.plusSeconds(181)
        ));
        notificacionId = notificacion.id();

        System.out.println("   🔔 Notificación creada con ID: " + notificacionId);
        System.out.println("   🔔 Tipo: " + TipoNotificacion.Recoger);
        System.out.println("   🔔 Leída: false");

        assertNotNull(notificacionId);

        Optional<Notificacion> notificacionRecuperada = notificacionRepository.findById(notificacionId);
        assertTrue(notificacionRecuperada.isPresent());
        assertEquals(TipoNotificacion.Recoger, notificacionRecuperada.get().tipo());
        assertFalse(notificacionRecuperada.get().leida());

        System.out.println("   ✅ Verificación correcta: notificación pendiente de atender");

        // 11) Camarero la atiende
        System.out.println("\n11. El camarero recoge el plato y lo lleva a la mesa...");

        Notificacion notificacionLeida = notificacionRepository.update(notificacionId, new Notificacion(
                notificacionId,
                cuenta,
                TipoNotificacion.Recoger,
                true,
                ahora.plusSeconds(240)
        ));

        System.out.println("   ✅ Notificación marcada como leída");
        System.out.println("   🍽️ Pedido entregado al cliente (simulado)");

        assertTrue(notificacionLeida.leida());

        Notificacion notificacionFinal = notificacionRepository.findById(notificacionId).orElseThrow();
        assertTrue(notificacionFinal.leida());

        System.out.println("   ✅ Verificación correcta: camarero atendió la notificación");

        // 12) Comprobaciones extra
        System.out.println("\n12. Ejecutando comprobaciones adicionales...");

        assertTrue(cuentaRepository.findByMesa(mesa).isPresent());
        System.out.println("   ✅ La cuenta se puede encontrar por mesa");

        assertFalse(pedidoRepository.findByCuenta(cuenta).isEmpty());
        System.out.println("   ✅ El pedido se puede encontrar por cuenta");

        assertFalse(ordenRepository.findByPedido(pedido).isEmpty());
        System.out.println("   ✅ La orden se puede encontrar por pedido");

        assertFalse(ordenRepository.findByEstado(OrdenEstado.Listo).isEmpty());
        System.out.println("   ✅ La orden se puede encontrar por estado LISTO");

        assertFalse(notificacionRepository.findByCuenta(cuenta).isEmpty());
        System.out.println("   ✅ La notificación se puede encontrar por cuenta");

        assertFalse(notificacionRepository.findByTipoNotificacion(TipoNotificacion.Recoger).isEmpty());
        System.out.println("   ✅ La notificación se puede encontrar por tipo RECOGER");

        assertFalse(notificacionRepository.findByLeida(true).isEmpty());
        System.out.println("   ✅ La notificación se puede encontrar como leída");

        System.out.println("\n========== FIN DEL FLUJO RESTAURANTE ==========\n");
    }

    @AfterAll
    void cleanup() {
        System.out.println("========== INICIANDO LIMPIEZA ==========\n");

        if (notificacionId != null) {
            notificacionRepository.deleteById(notificacionId);
            System.out.println("🗑️ Notificación eliminada: " + notificacionId);
        }

        if (ordenId != null) {
            ordenRepository.deleteById(ordenId);
            System.out.println("🗑️ Orden eliminada: " + ordenId);
        }

        if (pedidoId != null) {
            pedidoRepository.deleteById(pedidoId);
            System.out.println("🗑️ Pedido eliminado: " + pedidoId);
        }

        if (platoId != null) {
            platoRepository.deleteById(platoId);
            System.out.println("🗑️ Plato eliminado: " + platoId);
        }

        if (cuentaId != null) {
            cuentaRepository.deleteById(cuentaId);
            System.out.println("🗑️ Cuenta eliminada: " + cuentaId);
        }

        if (mesaId != null) {
            mesaRepository.deleteById(mesaId);
            System.out.println("🗑️ Mesa eliminada: " + mesaId);
        }

        if (camareroId != null) {
            usuarioRepository.deleteById(camareroId);
            System.out.println("🗑️ Camarero eliminado: " + camareroId);
        }

        if (cocineroId != null) {
            usuarioRepository.deleteById(cocineroId);
            System.out.println("🗑️ Cocinero eliminado: " + cocineroId);
        }

        System.out.println("\n========== LIMPIEZA FINALIZADA ==========");
    }
}