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
import repository.interfaces.PlatoRepository;
import service.MesaService;
import service.OrdenService;
import service.PedidoService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrdenServiceTest {

    private OrdenRepository ordenRepository;
    private PedidoRepository pedidoRepository;
    private PlatoRepository platoRepository;
    private CuentaRepository cuentaRepository;
    private MesaRepository mesaRepository;

    private MesaService mesaService;
    private PedidoService pedidoService;
    private OrdenService ordenService;

    private Mesa mesa;
    private Cuenta cuenta;
    private Pedido pedido;
    private Plato plato1;
    private Plato plato2;
    private Orden ordenPendiente;
    private Orden ordenLista;

    @BeforeEach
    void setUp() {
        ordenRepository = mock(OrdenRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        platoRepository = mock(PlatoRepository.class);
        cuentaRepository = mock(CuentaRepository.class);
        mesaRepository = mock(MesaRepository.class);

        mesaService = new MesaService(
                mesaRepository,
                cuentaRepository,
                pedidoRepository,
                ordenRepository
        );

        pedidoService = spy(new PedidoService(
                pedidoRepository,
                cuentaRepository,
                ordenRepository,
                platoRepository,
                mesaService
        ));

        ordenService = new OrdenService(
                ordenRepository,
                pedidoRepository,
                platoRepository,
                pedidoService,
                cuentaRepository
        );

        mesa = new Mesa("mesa1", 4);

        cuenta = new Cuenta(
                "cuenta1",
                List.of(mesa),
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty(),
                ""
        );

        pedido = new Pedido(
                "pedido1",
                cuenta,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        plato1 = new Plato(
                "plato1",
                "Hamburguesa",
                Categoria.Principal,
                "Desc",
                new BigDecimal("12.00"),
                true,
                ""
        );

        plato2 = new Plato(
                "plato2",
                "Agua",
                Categoria.Bebida,
                "Desc",
                new BigDecimal("2.50"),
                true,
                ""
        );

        ordenPendiente = new Orden(
                "orden1",
                pedido,
                plato1,
                new BigDecimal("12.00"),
                OrdenEstado.Pendiente,
                Instant.now(),
                ""
        );

        ordenLista = new Orden(
                "orden2",
                pedido,
                plato2,
                new BigDecimal("2.50"),
                OrdenEstado.Listo,
                Instant.now(),
                ""
        );
    }

    @Test
    void crearOrdenDesdePedidoYPlato_creaOrdenCorrectamente() {
        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedido));
        when(platoRepository.findById("plato1")).thenReturn(Optional.of(plato1));
        when(ordenRepository.save(any())).thenAnswer(invocation -> {
            Orden o = invocation.getArgument(0);
            return new Orden("ordenNueva", o.pedido(), o.plato(), o.precio(), o.ordenEstado(), o.fecha(), o.detalles());
        });

        Orden resultado = ordenService.crearOrdenDesdePedidoYPlato("pedido1", "plato1", "sin cebolla");

        assertNotNull(resultado);
        assertEquals("ordenNueva", resultado.id());
        assertEquals("pedido1", resultado.pedido().id());
        assertEquals("plato1", resultado.plato().id());
        assertEquals(new BigDecimal("12.00"), resultado.precio());
        assertEquals(OrdenEstado.Pendiente, resultado.ordenEstado());
        assertEquals("sin cebolla", resultado.detalles());
    }

    @Test
    void crearOrdenesDesdePedido_creaVariasOrdenes() {
        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedido));
        when(platoRepository.findById("plato1")).thenReturn(Optional.of(plato1));
        when(platoRepository.findById("plato2")).thenReturn(Optional.of(plato2));
        when(ordenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(pedido).when(pedidoService).recalcularEstadoPedido("pedido1");

        List<Orden> resultado = ordenService.crearOrdenesDesdePedido(
                "pedido1",
                List.of("plato1", "plato2"),
                List.of("sin cebolla", "muy fría")
        );

        assertEquals(2, resultado.size());
        assertEquals("plato1", resultado.get(0).plato().id());
        assertEquals("plato2", resultado.get(1).plato().id());

        verify(ordenRepository, times(2)).save(any(Orden.class));
        verify(pedidoService).recalcularEstadoPedido("pedido1");
    }

    @Test
    void obtenerOrdenesDePedido_devuelveSoloLasDeEsePedido() {
        Pedido otroPedido = new Pedido(
                "pedido2",
                cuenta,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        Orden otraOrden = new Orden(
                "orden3",
                otroPedido,
                plato1,
                new BigDecimal("12.00"),
                OrdenEstado.Pendiente,
                Instant.now(),
                ""
        );

        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(ordenPendiente, ordenLista, otraOrden));

        List<Orden> resultado = ordenService.obtenerOrdenesDePedido("pedido1");

        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().allMatch(o -> o.pedido().id().equals("pedido1")));
    }

    @Test
    void obtenerOrdenesPendientes_devuelveSoloPendientes() {
        when(ordenRepository.findAll()).thenReturn(List.of(ordenPendiente, ordenLista));

        List<Orden> resultado = ordenService.obtenerOrdenesPendientes();

        assertEquals(1, resultado.size());
        assertEquals("orden1", resultado.get(0).id());
    }

    @Test
    void marcarOrdenEnPreparacion_actualizaEstado() {
        when(ordenRepository.findById("orden1")).thenReturn(Optional.of(ordenPendiente));
        when(ordenRepository.update(eq("orden1"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        doReturn(pedido).when(pedidoService).recalcularEstadoPedido("pedido1");

        Orden resultado = ordenService.marcarOrdenEnPreparacion("orden1");

        assertEquals(OrdenEstado.Preparación, resultado.ordenEstado());
        verify(pedidoService).recalcularEstadoPedido("pedido1");
    }

    @Test
    void marcarOrdenLista_actualizaEstado() {
        when(ordenRepository.findById("orden1")).thenReturn(Optional.of(ordenPendiente));
        when(ordenRepository.update(eq("orden1"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        doReturn(new Pedido("pedido1", cuenta, PedidoEstado.Listo, pedido.fechaPedido()))
                .when(pedidoService).recalcularEstadoPedido("pedido1");

        Orden resultado = ordenService.marcarOrdenLista("orden1");

        assertEquals(OrdenEstado.Listo, resultado.ordenEstado());
        verify(pedidoService).recalcularEstadoPedido("pedido1");
    }

    @Test
    void estanTodasListasLasOrdenes_devuelveTrue_siTodasEstanListas() {
        Orden ordenLista2 = new Orden(
                "orden4",
                pedido,
                plato1,
                new BigDecimal("12.00"),
                OrdenEstado.Listo,
                Instant.now(),
                ""
        );

        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(ordenLista, ordenLista2));

        boolean resultado = ordenService.estanTodasListasLasOrdenes("pedido1");

        assertTrue(resultado);
    }

    @Test
    void estanTodasListasLasOrdenes_devuelveFalse_siAlgunaNoEstaLista() {
        when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(ordenLista, ordenPendiente));

        boolean resultado = ordenService.estanTodasListasLasOrdenes("pedido1");

        assertFalse(resultado);
    }
}
