import model.Cuenta;
import model.Mesa;
import model.Notificacion;
import model.TipoNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.interfaces.CuentaRepository;
import repository.interfaces.NotificacionRepository;
import service.NotificacionApplicationService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificacionApplicationServiceTest {

    private NotificacionRepository notificacionRepository;
    private CuentaRepository cuentaRepository;

    private NotificacionApplicationService notificacionApplicationService;

    private Cuenta cuenta;
    private Notificacion notificacionAtencion;
    private Notificacion notificacionRecoger;

    @BeforeEach
    void setUp() {
        notificacionRepository = mock(NotificacionRepository.class);
        cuentaRepository = mock(CuentaRepository.class);

        notificacionApplicationService = new NotificacionApplicationService(
                notificacionRepository,
                cuentaRepository
        );

        Mesa mesa = new Mesa("mesa1", 4);

        cuenta = new Cuenta(
                "cuenta1",
                List.of(mesa),
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty()
        );

        notificacionAtencion = new Notificacion(
                "not1",
                cuenta,
                TipoNotificacion.Atencion,
                false,
                Instant.now()
        );

        notificacionRecoger = new Notificacion(
                "not2",
                cuenta,
                TipoNotificacion.Recoger,
                true,
                Instant.now()
        );
    }

    @Test
    void crearNotificacionAtencion_creaCorrectamente() {
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuenta));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notificacion notificacion = notificacionApplicationService.crearNotificacionAtencion("cuenta1");

        assertNotNull(notificacion);
        assertEquals(TipoNotificacion.Atencion, notificacion.tipo());
        assertFalse(notificacion.leida());
    }

    @Test
    void crearNotificacionPedidoListo_creaCorrectamente() {
        when(cuentaRepository.findById("cuenta1")).thenReturn(Optional.of(cuenta));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notificacion notificacion = notificacionApplicationService.crearNotificacionPedidoListo("cuenta1");

        assertNotNull(notificacion);
        assertEquals(TipoNotificacion.Recoger, notificacion.tipo());
        assertFalse(notificacion.leida());
    }

    @Test
    void obtenerNotificacionesPendientes_devuelveSoloNoLeidas() {
        when(notificacionRepository.findAll()).thenReturn(List.of(notificacionAtencion, notificacionRecoger));

        List<Notificacion> resultado = notificacionApplicationService.obtenerNotificacionesPendientes();

        assertEquals(1, resultado.size());
        assertEquals("not1", resultado.get(0).id());
    }

    @Test
    void obtenerNotificacionesDeCuenta_filtraPorCuenta() {
        when(notificacionRepository.findAll()).thenReturn(List.of(notificacionAtencion, notificacionRecoger));

        List<Notificacion> resultado = notificacionApplicationService.obtenerNotificacionesDeCuenta("cuenta1");

        assertEquals(2, resultado.size());
    }

    @Test
    void marcarNotificacionLeida_actualizaSiNoLoEsta() {
        when(notificacionRepository.findById("not1")).thenReturn(Optional.of(notificacionAtencion));
        when(notificacionRepository.update(eq("not1"), any())).thenAnswer(inv -> inv.getArgument(1));

        Notificacion resultado = notificacionApplicationService.marcarNotificacionLeida("not1");

        assertTrue(resultado.leida());
    }
}