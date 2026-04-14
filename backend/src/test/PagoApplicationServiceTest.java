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
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;
import service.PagoApplicationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PagoApplicationServiceTest {

    private CuentaRepository cuentaRepository;
    private PedidoRepository pedidoRepository;
    private OrdenRepository ordenRepository;

    private PagoApplicationService pagoApplicationService;

    private Mesa mesa;
    private Cuenta cuenta;
    private Pedido pedido;
    private Orden orden1;
    private Orden orden2;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        ordenRepository = mock(OrdenRepository.class);

        pagoApplicationService = new PagoApplicationService(
                cuentaRepository,
                pedidoRepository,
                ordenRepository
        );

        mesa = new Mesa("mesa1", 4);

        cuenta = new Cuenta(
                "cuenta1",
                List.of(mesa),
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty()
        );

        pedido = new Pedido(
                "pedido1",
                cuenta,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        Plato plato = new Plato(
                "plato1",
                "Hamburguesa",
                Categoria.Principal,
                "Desc",
                new BigDecimal("10.00"),
                true
        );

        orden1 = new Orden(
                "orden1",
                pedido,
                plato,
                new BigDecimal("10.00"),
                OrdenEstado.Pendiente,
                Instant.now(),
                ""
        );

        orden2 = new Orden(
                "orden2",
                pedido,
                plato,
                new BigDecimal("5.00"),
                OrdenEstado.Pendiente,
                Instant.now(),
                ""
        );
    }

    @Test
    void calcularTotalCuenta_sumaLasOrdenesDeLaCuenta() {
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuenta));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(orden1, orden2));

        BigDecimal total = pagoApplicationService.calcularTotalCuenta("cuenta1");

        assertEquals(new BigDecimal("15.00"), total);
    }

    @Test
    void calcularPendienteCuenta_devuelveCero_siLaCuentaYaEstaPagada() {
        Cuenta pagada = new Cuenta(
                "cuenta1",
                List.of(mesa),
                true,
                Optional.empty(),
                Instant.now(),
                Optional.of(Instant.now())
        );

        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(pagada));

        BigDecimal pendiente = pagoApplicationService.calcularPendienteCuenta("cuenta1");

        assertEquals(BigDecimal.ZERO, pendiente);
    }

    @Test
    void calcularPendienteCuenta_devuelveTotal_siLaCuentaNoEstaPagada() {
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuenta));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(orden1, orden2));

        BigDecimal pendiente = pagoApplicationService.calcularPendienteCuenta("cuenta1");

        assertEquals(new BigDecimal("15.00"), pendiente);
    }

    @Test
    void pagarCuentaCompleta_actualizaLaCuenta() {
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuenta));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(orden1, orden2));
        when(cuentaRepository.update(eq("cuenta1"), any())).thenAnswer(invocation -> invocation.getArgument(1));

        Cuenta resultado = pagoApplicationService.pagarCuentaCompleta("cuenta1");

        assertTrue(resultado.estaPagada());
        assertTrue(resultado.fechaPago().isPresent());
        verify(cuentaRepository).update(eq("cuenta1"), any(Cuenta.class));
    }

    @Test
    void pagarCuentaCompleta_falla_siYaEstaPagada() {
        Cuenta pagada = new Cuenta(
                "cuenta1",
                List.of(mesa),
                true,
                Optional.empty(),
                Instant.now(),
                Optional.of(Instant.now())
        );

        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(pagada));

        assertThrows(
                IllegalArgumentException.class,
                () -> pagoApplicationService.pagarCuentaCompleta("cuenta1")
        );
    }

    @Test
    void cuentaEstaSaldada_devuelveTrue_siLaCuentaEstaPagada() {
        Cuenta pagada = new Cuenta(
                "cuenta1",
                List.of(mesa),
                true,
                Optional.empty(),
                Instant.now(),
                Optional.of(Instant.now())
        );

        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(pagada));

        assertTrue(pagoApplicationService.cuentaEstaSaldada("cuenta1"));
    }

    @Test
    void cerrarCuentaSiProcede_falla_siQuedaPendiente() {
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuenta));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(ordenRepository.findAll()).thenReturn(List.of(orden1, orden2));

        assertThrows(
                IllegalArgumentException.class,
                () -> pagoApplicationService.cerrarCuentaSiProcede("cuenta1")
        );
    }
}