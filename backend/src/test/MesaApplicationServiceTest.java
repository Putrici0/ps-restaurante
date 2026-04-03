import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import model.PedidoEstado;
import model.Plato;
import model.Categoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.firestore.FirestoreCuentaRepository;
import repository.firestore.FirestoreMesaRepository;
import repository.firestore.FirestoreOrdenRepository;
import repository.firestore.FirestorePedidoRepository;
import service.application.MesaApplicationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MesaApplicationServiceTest {

    @Mock
    private FirestoreMesaRepository mesaRepository;

    @Mock
    private FirestoreCuentaRepository cuentaRepository;

    @Mock
    private FirestorePedidoRepository pedidoRepository;

    @Mock
    private FirestoreOrdenRepository ordenRepository;

    @InjectMocks
    private MesaApplicationService mesaApplicationService;

    private Mesa mesa;
    private Cuenta cuentaActiva;
    private Cuenta cuentaPagada;
    private Pedido pedido;
    private Orden orden;

    @BeforeEach
    void setUp() {
        mesa = new Mesa("mesa1", 4);

        cuentaActiva = new Cuenta(
                "cuenta1",
                List.of(mesa),
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty()
        );

        cuentaPagada = new Cuenta(
                "cuenta2",
                List.of(mesa),
                true,
                Optional.empty(),
                Instant.now(),
                Optional.of(Instant.now())
        );

        pedido = new Pedido(
                "pedido1",
                cuentaActiva,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        Plato plato = new Plato(
                "plato1",
                "Hamburguesa",
                Categoria.Principal,
                "Desc",
                new BigDecimal("12.00"),
                true
        );

        orden = new Orden(
                "orden1",
                pedido,
                plato,
                new BigDecimal("12.00"),
                OrdenEstado.Pendiente,
                Instant.now(),
                ""
        );
    }

    @Test
    void estaOcupada_devuelveTrue_siHayCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaActiva));

        assertTrue(mesaApplicationService.estaOcupada("mesa1"));
    }

    @Test
    void estaOcupada_devuelveFalse_siSoloHayCuentaPagada() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaPagada));

        assertFalse(mesaApplicationService.estaOcupada("mesa1"));
    }

    @Test
    void ocuparMesa_creaCuenta_siLaMesaEstaLibre() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of());
        when(cuentaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Cuenta resultado = mesaApplicationService.ocuparMesa("mesa1");

        assertNotNull(resultado);
        assertFalse(resultado.estaPagada());
        assertEquals(1, resultado.mesas().size());
        assertEquals("mesa1", resultado.mesas().get(0).id());
    }

    @Test
    void ocuparMesa_falla_siLaMesaYaEstaOcupada() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaActiva));

        assertThrows(IllegalArgumentException.class,
                () -> mesaApplicationService.ocuparMesa("mesa1"));
    }

    @Test
    void obtenerCuentaActivaDeMesa_devuelveLaNoPagada() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaPagada, cuentaActiva));

        Optional<Cuenta> resultado = mesaApplicationService.obtenerCuentaActivaDeMesa("mesa1");

        assertTrue(resultado.isPresent());
        assertEquals("cuenta1", resultado.get().id());
    }

    @Test
    void obtenerPedidosActivosDeMesa_devuelvePedidosDeLaCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaActiva));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));

        List<Pedido> pedidos = mesaApplicationService.obtenerPedidosActivosDeMesa("mesa1");

        assertEquals(1, pedidos.size());
        assertEquals("pedido1", pedidos.get(0).id());
    }

    @Test
    void obtenerOrdenesActivasDeMesa_devuelveOrdenesDeLosPedidosDeLaMesa() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaActiva));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(orden));

        List<Orden> ordenes = mesaApplicationService.obtenerOrdenesActivasDeMesa("mesa1");

        assertEquals(1, ordenes.size());
        assertEquals("orden1", ordenes.get(0).id());
    }

    @Test
    void liberarMesa_falla_siLaCuentaSigueActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaActiva));

        assertThrows(IllegalArgumentException.class,
                () -> mesaApplicationService.liberarMesa("mesa1"));
    }

    @Test
    void liberarMesa_noFalla_siNoHayCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaPagada));

        assertDoesNotThrow(() -> mesaApplicationService.liberarMesa("mesa1"));
    }
}