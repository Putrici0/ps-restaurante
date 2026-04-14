import model.Categoria;
import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import model.PedidoEstado;
import model.Plato;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.interfaces.CuentaRepository;
import repository.interfaces.MesaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;
import service.MesaService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MesaServiceTest {

    private MesaRepository mesaRepository;
    private CuentaRepository cuentaRepository;
    private PedidoRepository pedidoRepository;
    private OrdenRepository ordenRepository;

    private MesaService mesaService;

    private Mesa mesa;
    private Cuenta cuentaActiva;
    private Cuenta cuentaPagada;
    private Pedido pedido;
    private Orden orden;

    @BeforeEach
    void setUp() {
        mesaRepository = mock(MesaRepository.class);
        cuentaRepository = mock(CuentaRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        ordenRepository = mock(OrdenRepository.class);

        mesaService = new MesaService(
                mesaRepository,
                cuentaRepository,
                pedidoRepository,
                ordenRepository
        );

        mesa = new Mesa("mesa1", 4);

        cuentaActiva = new Cuenta(
                "cuenta1",
                List.of(mesa),
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty(),
                "1234"
        );

        cuentaPagada = new Cuenta(
                "cuenta2",
                List.of(mesa),
                true,
                Optional.empty(),
                Instant.now(),
                Optional.of(Instant.now()),
                ""
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
                true,
                ""
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
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.of(cuentaActiva));

        assertTrue(mesaService.estaOcupada("mesa1"));
    }

    @Test
    void estaOcupada_devuelveFalse_siSoloHayCuentaPagada() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.empty());

        assertFalse(mesaService.estaOcupada("mesa1"));
    }

    @Test
    void ocuparMesa_creaCuenta_siLaMesaEstaLibre() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.empty());
        when(cuentaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Cuenta resultado = mesaService.ocuparMesa("mesa1");

        assertNotNull(resultado);
        assertFalse(resultado.payed());
        assertEquals("mesa1", resultado.mesas().get(0).id());
    }

    @Test
    void ocuparMesa_falla_siYaEstaOcupada() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.of(cuentaActiva));

        assertThrows(IllegalArgumentException.class,
                () -> mesaService.ocuparMesa("mesa1"));
    }

    @Test
    void obtenerCuentaActivaDeMesa_devuelveLaNoPagada() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.of(cuentaActiva));

        Optional<Cuenta> resultado = mesaService.obtenerCuentaActivaDeMesa("mesa1");

        assertTrue(resultado.isPresent());
        assertEquals("cuenta1", resultado.get().id());
    }

    @Test
    void obtenerPedidosActivosDeMesa_devuelvePedidosDeLaCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.of(cuentaActiva));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));

        List<Pedido> pedidos = mesaService.obtenerPedidosActivosDeMesa("mesa1");

        assertEquals(1, pedidos.size());
        assertEquals("pedido1", pedidos.get(0).id());
    }

    @Test
    void obtenerOrdenesActivasDeMesa_devuelveOrdenesDeLosPedidosDeLaMesa() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.of(cuentaActiva));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(orden));

        List<Orden> ordenes = mesaService.obtenerOrdenesActivasDeMesa("mesa1");

        assertEquals(1, ordenes.size());
        assertEquals("orden1", ordenes.get(0).id());
    }

    @Test
    void liberarMesa_falla_siNoHayCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> mesaService.liberarMesa("mesa1"));
    }

    @Test
    void liberarMesa_noFalla_siHayCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findByMesa(mesa)).thenReturn(Optional.of(cuentaActiva));
        when(cuentaRepository.update(eq("cuenta1"), any())).thenAnswer(invocation -> invocation.getArgument(1));

        assertDoesNotThrow(() -> mesaService.liberarMesa("mesa1"));
    }
}
