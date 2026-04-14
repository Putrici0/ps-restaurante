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
import service.MesaApplicationService;
import service.PedidoApplicationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PedidoApplicationServiceTest {

    private MesaRepository mesaRepository;
    private CuentaRepository cuentaRepository;
    private PedidoRepository pedidoRepository;
    private OrdenRepository ordenRepository;

    private MesaApplicationService mesaApplicationService;
    private PedidoApplicationService pedidoApplicationService;

    private Mesa mesa;
    private Cuenta cuentaActiva;
    private Cuenta cuentaPagada;
    private Pedido pedido;
    private Plato plato;
    private Orden ordenPendiente;
    private Orden ordenLista;

    @BeforeEach
    void setUp() {
        mesaRepository = mock(MesaRepository.class);
        cuentaRepository = mock(CuentaRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        ordenRepository = mock(OrdenRepository.class);

        mesaApplicationService = new MesaApplicationService(
                mesaRepository,
                cuentaRepository,
                pedidoRepository,
                ordenRepository
        );

        pedidoApplicationService = new PedidoApplicationService(
                pedidoRepository,
                cuentaRepository,
                ordenRepository,
                mesaApplicationService
        );

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

        plato = new Plato(
                "plato1",
                "Hamburguesa",
                Categoria.Principal,
                "Desc",
                new BigDecimal("12.00"),
                true
        );

        ordenPendiente = new Orden(
                "orden1",
                pedido,
                plato,
                new BigDecimal("12.00"),
                OrdenEstado.Pendiente,
                Instant.now(),
                ""
        );

        ordenLista = new Orden(
                "orden2",
                pedido,
                plato,
                new BigDecimal("12.00"),
                OrdenEstado.Listo,
                Instant.now(),
                ""
        );
    }

    @Test
    void crearPedidoDesdeMesa_creaPedido_siLaMesaTieneCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaActiva));
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuentaActiva));
        when(pedidoRepository.save(any())).thenAnswer(invocation -> {
            Pedido p = invocation.getArgument(0);
            return new Pedido("pedidoNuevo", p.cuenta(), p.pedidoEstado(), p.fechaPedido());
        });

        Pedido resultado = pedidoApplicationService.crearPedidoDesdeMesa("mesa1");

        assertNotNull(resultado);
        assertEquals("pedidoNuevo", resultado.id());
        assertEquals("cuenta1", resultado.cuenta().id());
        assertEquals(PedidoEstado.Pendiente, resultado.pedidoEstado());
    }

    @Test
    void crearPedidoDesdeMesa_falla_siLaMesaNoTieneCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaPagada));

        assertThrows(
                IllegalArgumentException.class,
                () -> pedidoApplicationService.crearPedidoDesdeMesa("mesa1")
        );
    }

    @Test
    void crearPedidoEnCuenta_falla_siLaCuentaEstaPagada() {
        when(cuentaRepository.findById("cuenta2")).thenReturn(Optional.of(cuentaPagada));

        assertThrows(
                IllegalArgumentException.class,
                () -> pedidoApplicationService.crearPedidoEnCuenta("cuenta2")
        );
    }

    @Test
    void obtenerPedidosDeCuenta_devuelveSoloLosDeEsaCuenta() {
        Pedido pedido2 = new Pedido(
                "pedido2",
                cuentaPagada,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuentaActiva));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido, pedido2));

        List<Pedido> resultado = pedidoApplicationService.obtenerPedidosDeCuenta("cuenta1");

        assertEquals(1, resultado.size());
        assertEquals("pedido1", resultado.get(0).id());
    }

    @Test
    void obtenerPedidosActivosDeMesa_devuelveLosDeLaCuentaActiva() {
        when(mesaRepository.findById("mesa1")).thenReturn(Optional.of(mesa));
        when(cuentaRepository.findAll()).thenReturn(List.of(cuentaActiva));
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuentaActiva));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));

        List<Pedido> resultado = pedidoApplicationService.obtenerPedidosActivosDeMesa("mesa1");

        assertEquals(1, resultado.size());
        assertEquals("pedido1", resultado.get(0).id());
    }

    @Test
    void recalcularEstadoPedido_poneListo_siTodasLasOrdenesEstanListas() {
        Pedido pedidoInicial = new Pedido(
                "pedido1",
                cuentaActiva,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        Orden ordenLista2 = new Orden(
                "orden3",
                pedidoInicial,
                plato,
                new BigDecimal("8.00"),
                OrdenEstado.Listo,
                Instant.now(),
                ""
        );

        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedidoInicial));
        when(ordenRepository.findAll()).thenReturn(List.of(ordenLista, ordenLista2));
        when(pedidoRepository.update(eq("pedido1"), any())).thenAnswer(invocation -> invocation.getArgument(1));

        Pedido resultado = pedidoApplicationService.recalcularEstadoPedido("pedido1");

        assertEquals(PedidoEstado.Listo, resultado.pedidoEstado());
    }

    @Test
    void recalcularEstadoPedido_ponePendiente_siAlgunaOrdenNoEstaLista() {
        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(ordenLista, ordenPendiente));
        when(pedidoRepository.update(eq("pedido1"), any())).thenAnswer(invocation -> invocation.getArgument(1));

        Pedido resultado = pedidoApplicationService.recalcularEstadoPedido("pedido1");

        assertEquals(PedidoEstado.Pendiente, resultado.pedidoEstado());
    }

    @Test
    void pedidoEstaListo_devuelveTrue_siElPedidoYaEstaListo() {
        Pedido listo = new Pedido(
                "pedido1",
                cuentaActiva,
                PedidoEstado.Listo,
                Instant.now()
        );

        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(listo));

        assertTrue(pedidoApplicationService.pedidoEstaListo("pedido1"));
    }
}