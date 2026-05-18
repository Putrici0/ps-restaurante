package service.application;

import model.Cuenta;
import model.Notificacion;
import model.Orden;
import model.OrdenEstado;
import model.TipoNotificacion;
import repository.interfaces.CuentaRepository;
import repository.interfaces.NotificacionRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class NotificacionApplicationService {
    private static final String MARCADOR_ASIGNACION_MESA = "__ASIGNACION_MESA__";
    private final NotificacionRepository notificacionRepository;
    private final CuentaRepository cuentaRepository;
    private final OrdenApplicationService ordenApplicationService;

    public NotificacionApplicationService(
            NotificacionRepository notificacionRepository,
            CuentaRepository cuentaRepository,
            OrdenApplicationService ordenApplicationService
    ) {
        this.notificacionRepository = notificacionRepository;
        this.cuentaRepository = cuentaRepository;
        this.ordenApplicationService = ordenApplicationService;
    }

    public Notificacion crearNotificacionAtencion(String cuentaId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        Optional<Notificacion> activa = notificacionRepository.findActiveAtencionByCuentaId(cuentaId);
        if (activa.isPresent()) {
            return activa.get();
        }

        AsignacionMesa asignacion = resolverAsignacionMesa(cuentaId).orElse(null);
        boolean enCurso = asignacion != null && asignacion.activa;

        Notificacion notificacion = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Atencion,
                false,
                Instant.now(),
                null,
                null,
                null,
                enCurso,
                asignacion != null ? asignacion.camareroUid : null,
                asignacion != null ? asignacion.camareroNombre : null,
                enCurso ? Instant.now() : null
        );

        return notificacionRepository.saveWithDedup(notificacion);
    }

    public Optional<Notificacion> obtenerNotificacionAtencionActiva(String cuentaId) {
        return notificacionRepository.findActiveAtencionByCuentaId(cuentaId);
    }

    public Notificacion crearNotificacionPedidoListo(
            String cuentaId,
            String ordenId,
            String nombreItem,
            String categoriaItem
    ) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        AsignacionMesa asignacion = resolverAsignacionMesa(cuentaId).orElse(null);
        boolean enCurso = asignacion != null && asignacion.activa;

        Notificacion notificacion = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Recoger,
                false,
                Instant.now(),
                ordenId,
                nombreItem,
                categoriaItem,
                enCurso,
                asignacion != null ? asignacion.camareroUid : null,
                asignacion != null ? asignacion.camareroNombre : null,
                enCurso ? Instant.now() : null
        );

        return notificacionRepository.save(notificacion);
    }

    public List<Notificacion> obtenerNotificacionesPendientes() {
        return notificacionRepository.findByLeida(false);
    }

    public List<Notificacion> obtenerNotificacionesDeCuenta(String cuentaId) {
        return notificacionRepository.findByCuentaId(cuentaId);
    }

    public List<Notificacion> obtenerNotificacionesPorTipo(TipoNotificacion tipo) {
        return notificacionRepository.findByTipoNotificacion(tipo);
    }

    public List<Notificacion> obtenerTodasLasAtencionesActivas() {
        return notificacionRepository.findByTipoAndLeida(TipoNotificacion.Atencion, false);
    }

    public List<Notificacion> obtenerAsignacionesMesaActivas() {
        return notificacionRepository.findByTipoNotificacion(TipoNotificacion.Atencion).stream()
                .filter(this::esMarcadorAsignacionMesa)
                .filter(n -> n.cuenta() != null && n.cuenta().id() != null && !n.cuenta().id().isBlank())
                .collect(Collectors.groupingBy(n -> n.cuenta().id()))
                .values().stream()
                .map(this::masRecientePorAsignacion)
                .filter(n -> n.enCurso() && n.camareroUid() != null && !n.camareroUid().isBlank())
                .toList();
    }

    public void limpiarNotificacionesEstancadas() {
        Instant haceCincoMinutos = Instant.now().minusSeconds(5 * 60);

        notificacionRepository.findEnCursoNoLeidas().stream()
                .filter(n -> n.fechaEnCurso() != null)
                .filter(n -> n.fechaEnCurso().isBefore(haceCincoMinutos))
                .forEach(n -> desasignarYReenviarNotificacion(n.id()));
    }

    public Notificacion marcarNotificacionEnCurso(
            String notificacionId,
            String camareroUid,
            String camareroNombre
    ) {
        String nombreLimpio = camareroNombre == null || camareroNombre.isBlank()
                ? "Camarero"
                : camareroNombre.trim();

        return notificacionRepository.marcarEnCursoSiDisponible(
                        notificacionId,
                        camareroUid,
                        nombreLimpio,
                        notificacion -> new Notificacion(
                                notificacion.id(),
                                notificacion.cuenta(),
                                notificacion.tipo(),
                                false,
                                notificacion.fecha(),
                                notificacion.ordenId(),
                                notificacion.nombreItem(),
                                notificacion.categoriaItem(),
                                true,
                                camareroUid,
                                nombreLimpio,
                                Instant.now()
                        )
                )
                .orElseThrow(() -> new IllegalStateException("Esta notificación ya la ha cogido otro compañero"));
    }

    public Notificacion marcarNotificacionLeida(String notificacionId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("La notificación no existe"));

        if (notificacion.leida()) {
            return notificacion;
        }

        Notificacion actualizada = new Notificacion(
                notificacion.id(),
                notificacion.cuenta(),
                notificacion.tipo(),
                true,
                notificacion.fecha(),
                notificacion.ordenId(),
                notificacion.nombreItem(),
                notificacion.categoriaItem(),
                notificacion.enCurso(),
                notificacion.camareroUid(),
                notificacion.camareroNombre(),
                notificacion.fechaEnCurso()
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
    }

    public Notificacion marcarNotificacionCompletada(String notificacionId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("La notificación no existe"));

        if (notificacion.leida()) {
            return notificacion;
        }

        if (notificacion.tipo() == TipoNotificacion.Recoger) {
            String ordenId = notificacion.ordenId();
            if (ordenId == null || ordenId.isBlank()) {
                ordenId = resolverOrdenIdRecoger(notificacion);
            }

            if (ordenId != null && !ordenId.isBlank()) {
                ordenApplicationService.marcarOrdenEntregada(ordenId);
            }
        }

        Notificacion actualizada = new Notificacion(
                notificacion.id(),
                notificacion.cuenta(),
                notificacion.tipo(),
                true,
                notificacion.fecha(),
                notificacion.ordenId(),
                notificacion.nombreItem(),
                notificacion.categoriaItem(),
                notificacion.enCurso(),
                notificacion.camareroUid(),
                notificacion.camareroNombre(),
                notificacion.fechaEnCurso()
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
    }

    private String resolverOrdenIdRecoger(Notificacion notificacion) {
        if (notificacion == null || notificacion.cuenta() == null || notificacion.cuenta().id() == null) {
            return null;
        }

        String cuentaId = notificacion.cuenta().id();
        String nombreItem = notificacion.nombreItem() != null ? notificacion.nombreItem().trim() : "";

        List<Orden> candidatas = ordenApplicationService.obtenerPlatosActivosSala().stream()
                .filter(orden -> orden.ordenEstado() == OrdenEstado.Listo)
                .filter(orden -> orden.pedido() != null
                        && orden.pedido().cuenta() != null
                        && cuentaId.equals(orden.pedido().cuenta().id()))
                .toList();

        if (candidatas.isEmpty()) {
            return null;
        }

        if (!nombreItem.isBlank()) {
            Optional<Orden> porNombre = candidatas.stream()
                    .filter(orden -> orden.plato() != null
                            && orden.plato().nombre() != null
                            && nombreItem.equalsIgnoreCase(orden.plato().nombre().trim()))
                    .findFirst();
            if (porNombre.isPresent()) {
                return porNombre.get().id();
            }
        }

        return candidatas.get(0).id();
    }

    public Notificacion desasignarYReenviarNotificacion(String notificacionId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("La notificación no existe"));

        if (notificacion.leida()) {
            return notificacion;
        }

        Notificacion actualizada = new Notificacion(
                notificacion.id(),
                notificacion.cuenta(),
                notificacion.tipo(),
                false,
                Instant.now(),
                notificacion.ordenId(),
                notificacion.nombreItem(),
                notificacion.categoriaItem(),
                false,
                null,
                null,
                null
        );

        return notificacionRepository.update(notificacion.id(), actualizada);
    }

    public Notificacion asignarResponsableMesa(
            String cuentaId,
            String camareroUid,
            String camareroNombre
    ) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        String nombreLimpio = camareroNombre == null || camareroNombre.isBlank()
                ? "Camarero"
                : camareroNombre.trim();

        // Registro silencioso de asignación (no aparece en pendientes)
        Notificacion marcador = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Atencion,
                true,
                Instant.now(),
                null,
                null,
                MARCADOR_ASIGNACION_MESA,
                true,
                camareroUid,
                nombreLimpio,
                Instant.now()
        );
        Notificacion guardada = notificacionRepository.save(marcador);

        // Las notificaciones abiertas de esta cuenta pasan a ese responsable
        notificacionRepository.findByCuentaId(cuentaId).stream()
                .filter(n -> !n.leida())
                .forEach(n -> {
                    Notificacion actualizada = new Notificacion(
                            n.id(),
                            n.cuenta(),
                            n.tipo(),
                            false,
                            n.fecha(),
                            n.ordenId(),
                            n.nombreItem(),
                            n.categoriaItem(),
                            true,
                            camareroUid,
                            nombreLimpio,
                            Instant.now()
                    );
                    notificacionRepository.update(n.id(), actualizada);
                });

        return guardada;
    }

    public Notificacion liberarResponsableMesa(String cuentaId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        // Registro silencioso de desasignación (no aparece en pendientes)
        Notificacion marcador = new Notificacion(
                null,
                cuenta,
                TipoNotificacion.Atencion,
                true,
                Instant.now(),
                null,
                null,
                MARCADOR_ASIGNACION_MESA,
                false,
                null,
                null,
                Instant.now()
        );
        Notificacion guardada = notificacionRepository.save(marcador);

        // Las notificaciones abiertas vuelven al pool compartido
        notificacionRepository.findByCuentaId(cuentaId).stream()
                .filter(n -> !n.leida())
                .forEach(n -> {
                    Notificacion actualizada = new Notificacion(
                            n.id(),
                            n.cuenta(),
                            n.tipo(),
                            false,
                            Instant.now(),
                            n.ordenId(),
                            n.nombreItem(),
                            n.categoriaItem(),
                            false,
                            null,
                            null,
                            null
                    );
                    notificacionRepository.update(n.id(), actualizada);
                });

        return guardada;
    }

    public void marcarNotificacionesRecogerComoLeidasDeOrden(String ordenId) {
        if (ordenId == null || ordenId.isBlank()) {
            return;
        }

        notificacionRepository.findByOrdenId(ordenId).stream()
                .filter(notificacion -> !notificacion.leida())
                .filter(notificacion -> notificacion.tipo() == TipoNotificacion.Recoger)
                .forEach(notificacion -> {
                    Notificacion actualizada = new Notificacion(
                            notificacion.id(),
                            notificacion.cuenta(),
                            notificacion.tipo(),
                            true,
                            notificacion.fecha(),
                            notificacion.ordenId(),
                            notificacion.nombreItem(),
                            notificacion.categoriaItem(),
                            notificacion.enCurso(),
                            notificacion.camareroUid(),
                            notificacion.camareroNombre(),
                            notificacion.fechaEnCurso()
                    );

                    notificacionRepository.update(notificacion.id(), actualizada);
                });
    }

    @Deprecated
    public void eliminarNotificacionesRecogerDeOrden(String ordenId) {
        marcarNotificacionesRecogerComoLeidasDeOrden(ordenId);
    }

    public void marcarNotificacionesRecogerComoLeidasDeCuenta(String cuentaId) {
        if (cuentaId == null || cuentaId.isBlank()) {
            return;
        }

        notificacionRepository.findByCuentaId(cuentaId).stream()
                .filter(notificacion -> !notificacion.leida())
                .filter(notificacion -> notificacion.tipo() == TipoNotificacion.Recoger)
                .forEach(notificacion -> {
                    Notificacion actualizada = new Notificacion(
                            notificacion.id(),
                            notificacion.cuenta(),
                            notificacion.tipo(),
                            true,
                            notificacion.fecha(),
                            notificacion.ordenId(),
                            notificacion.nombreItem(),
                            notificacion.categoriaItem(),
                            notificacion.enCurso(),
                            notificacion.camareroUid(),
                            notificacion.camareroNombre(),
                            notificacion.fechaEnCurso()
                    );

                    notificacionRepository.update(notificacion.id(), actualizada);
                });
    }

    private Optional<AsignacionMesa> resolverAsignacionMesa(String cuentaId) {
        return notificacionRepository.findByCuentaId(cuentaId).stream()
                .filter(n -> n.tipo() == TipoNotificacion.Atencion)
                .filter(this::esMarcadorAsignacionMesa)
                .filter(n -> n.fechaEnCurso() != null || n.fecha() != null)
                .sorted(Comparator.comparing(
                        (Notificacion n) -> n.fechaEnCurso() != null ? n.fechaEnCurso() : n.fecha()
                ).reversed())
                .findFirst()
                .map(n -> new AsignacionMesa(
                        n.enCurso(),
                        n.camareroUid(),
                        n.camareroNombre()
                ));
    }

    private boolean esMarcadorAsignacionMesa(Notificacion notificacion) {
        return notificacion != null
                && notificacion.leida()
                && MARCADOR_ASIGNACION_MESA.equals(notificacion.categoriaItem());
    }

    private Notificacion masRecientePorAsignacion(List<Notificacion> notificaciones) {
        return notificaciones.stream()
                .max(Comparator.comparing(
                        (Notificacion n) -> n.fechaEnCurso() != null ? n.fechaEnCurso() : n.fecha()
                ))
                .orElseThrow();
    }

    private static class AsignacionMesa {
        private final boolean activa;
        private final String camareroUid;
        private final String camareroNombre;

        private AsignacionMesa(boolean activa, String camareroUid, String camareroNombre) {
            this.activa = activa;
            this.camareroUid = camareroUid;
            this.camareroNombre = camareroNombre;
        }
    }
}
