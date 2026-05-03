package service.application;

import dto.cocina.CocinaOrden;
import dto.cocina.CocinaTablero;
import model.Categoria;
import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import model.Plato;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;
import repository.interfaces.PlatoRepository;
import service.domain.cocina.CocinaContextoCuenta;
import service.domain.cocina.CocinaContextoService;
import service.domain.cocina.CocinaOrdenMapper;
import service.domain.cocina.CocinaPrioridadService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OrdenApplicationService {

    private final OrdenRepository ordenRepository;
    private final PedidoRepository pedidoRepository;
    private final PlatoRepository platoRepository;
    private final PedidoApplicationService pedidoApplicationService;
    private final CuentaRepository cuentaRepository;

    private final CocinaContextoService cocinaContextoService = new CocinaContextoService();
    private final CocinaPrioridadService cocinaPrioridadService = new CocinaPrioridadService();
    private final CocinaOrdenMapper cocinaOrdenMapper = new CocinaOrdenMapper();
    private static final long TABLERO_CACHE_TTL_MS = 3000;
    private volatile CocinaTablero cacheTablero;
    private volatile Instant cacheTableroGeneradoEn;

    public OrdenApplicationService(
            OrdenRepository ordenRepository,
            PedidoRepository pedidoRepository,
            PlatoRepository platoRepository,
            PedidoApplicationService pedidoApplicationService
    ) {
        this(ordenRepository, pedidoRepository, platoRepository, pedidoApplicationService, null);
    }

    public OrdenApplicationService(
            OrdenRepository ordenRepository,
            PedidoRepository pedidoRepository,
            PlatoRepository platoRepository,
            PedidoApplicationService pedidoApplicationService,
            CuentaRepository cuentaRepository
    ) {
        this.ordenRepository = ordenRepository;
        this.pedidoRepository = pedidoRepository;
        this.platoRepository = platoRepository;
        this.pedidoApplicationService = pedidoApplicationService;
        this.cuentaRepository = cuentaRepository;
    }

    private List<Orden> obtenerTodasLasOrdenesActivas() {
        if (cuentaRepository == null) {
            return List.of();
        }

        java.util.Set<String> cuentasActivasIds = cuentaRepository.findByEstaPagada(false).stream()
                .map(Cuenta::id)
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        if (cuentasActivasIds.isEmpty()) {
            return List.of();
        }

        return ordenRepository.findAll().stream()
                .filter(orden -> orden.ordenEstado() != OrdenEstado.Cancelado)
                .filter(orden -> !orden.pagada())
                .filter(orden -> {
                    if (orden.pedido() == null || orden.pedido().cuenta() == null) {
                        return false;
                    }
                    String cuentaId = orden.pedido().cuenta().id();
                    return cuentaId != null && cuentasActivasIds.contains(cuentaId);
                })
                .toList();
    }

    public CocinaTablero obtenerTableroCocinaPriorizado() {
        CocinaTablero tableroCacheado = cacheTableroVigente();
        if (tableroCacheado != null) {
            return tableroCacheado;
        }

        Instant ahora = Instant.now();

        List<Orden> ordenesCocina = obtenerTodasLasOrdenesActivas().stream()
                .filter(this::esOrdenDeCocina)
                .toList();

        Map<String, CocinaContextoCuenta> contexto = cocinaContextoService.construirContexto(ordenesCocina);

        List<Orden> pendientes = cocinaPrioridadService.ordenarPorPrioridad(
                ordenesCocina.stream()
                        .filter(o -> o.ordenEstado() == OrdenEstado.Pendiente)
                        .toList(),
                contexto,
                ahora
        );

        List<Orden> enPreparacion = cocinaPrioridadService.ordenarPorPrioridad(
                ordenesCocina.stream()
                        .filter(o -> o.ordenEstado() == OrdenEstado.Preparación)
                        .toList(),
                contexto,
                ahora
        );

        List<Orden> colaActivaPriorizada = cocinaPrioridadService.ordenarPorPrioridad(
                ordenesCocina.stream()
                        .filter(o -> o.ordenEstado() == OrdenEstado.Pendiente || o.ordenEstado() == OrdenEstado.Preparación)
                        .toList(),
                contexto,
                ahora
        );
        int capacidadConcurrente = Math.max(1, enPreparacion.size());
        Map<String, Integer> etaPorOrden = cocinaPrioridadService.calcularEtaMinutosPorOrden(
                colaActivaPriorizada,
                capacidadConcurrente
        );

        List<Orden> listas = ordenesCocina.stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo)
                .sorted(
                        Comparator
                                .comparing(this::claveOrdenacionMesa)
                                .thenComparing(Orden::fecha, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .toList();

        CocinaTablero tablero = new CocinaTablero(
                mapearConPrioridad(pendientes, contexto, ahora, etaPorOrden),
                mapearConPrioridad(enPreparacion, contexto, ahora, etaPorOrden),
                mapearConPrioridad(listas, contexto, ahora, etaPorOrden),
                ahora
        );
        cachearTablero(tablero, ahora);
        return tablero;
    }

    private List<CocinaOrden> mapearConPrioridad(
            List<Orden> ordenes,
            Map<String, CocinaContextoCuenta> contexto,
            Instant ahora,
            Map<String, Integer> etaPorOrden
    ) {
        return ordenes.stream()
                .map(orden -> cocinaOrdenMapper.toCocinaOrden(
                        orden,
                        cocinaPrioridadService.calcularPrioridad(orden, contexto, ahora, etaPorOrden.get(orden.id()))
                ))
                .toList();
    }

    public List<Orden> obtenerBebidasActivasBarra() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() != OrdenEstado.Cancelado)
                .toList();
    }

    public List<Orden> obtenerPlatosActivosSala() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() != Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo || o.ordenEstado() == OrdenEstado.Entregado)
                .toList();
    }

    public List<Orden> obtenerOrdenesPendientes() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Pendiente)
                .toList();
    }

    public List<Orden> obtenerOrdenesEnPreparacion() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Preparación)
                .toList();
    }

    public List<Orden> obtenerOrdenesListas() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo)
                .toList();
    }

    public List<Orden> obtenerOrdenesCocinaPendientes() {
        CocinaTablero tablero = obtenerTableroCocinaPriorizado();

        return tablero.pendientes().stream()
                .map(this::toOrden)
                .toList();
    }

    public List<Orden> obtenerOrdenesCocinaEnPreparacion() {
        CocinaTablero tablero = obtenerTableroCocinaPriorizado();

        return tablero.enPreparacion().stream()
                .map(this::toOrden)
                .toList();
    }

    public List<Orden> obtenerOrdenesCocinaListas() {
        CocinaTablero tablero = obtenerTableroCocinaPriorizado();

        return tablero.listas().stream()
                .map(this::toOrden)
                .toList();
    }

    public List<Orden> obtenerOrdenesBarraPendientes() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Pendiente)
                .toList();
    }

    public List<Orden> obtenerOrdenesBarraEnPreparacion() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Preparación)
                .toList();
    }

    public List<Orden> obtenerOrdenesBarraListas() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo)
                .toList();
    }

    public List<Orden> obtenerOrdenesDePedido(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        return ordenRepository.findByPedido(pedido).stream()
                .map(this::hidratarOrdenCompleta)
                .toList();
    }

    public List<Orden> crearOrdenesDesdePedido(String pedidoId, List<String> platosIds, List<String> detalles) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        List<Orden> ordenesCreadas = new ArrayList<>();

        for (int i = 0; i < platosIds.size(); i++) {
            String platoId = platosIds.get(i);
            String detalle = (detalles != null && i < detalles.size()) ? detalles.get(i) : "";

            Plato plato = platoRepository.findById(platoId)
                    .orElseThrow(() -> new IllegalArgumentException("Plato no encontrado: " + platoId));

            Orden nuevaOrden = new Orden(
                    null,
                    pedido,
                    plato,
                    plato.precio(),
                    OrdenEstado.Pendiente,
                    Instant.now(),
                    detalle
            );

            Orden guardada = ordenRepository.save(nuevaOrden);
            ordenesCreadas.add(hidratarOrdenCompleta(guardada));
        }

        return ordenesCreadas;
    }

    public Orden marcarOrdenPendiente(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Pendiente,
                orden.fecha(),
                orden.detalles(),
                orden.urgente(),
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);
        invalidarCacheTablero();
        return resultado;
    }

    public Orden marcarOrdenEnPreparacion(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Preparación,
                orden.fecha(),
                orden.detalles(),
                orden.urgente(),
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);
        invalidarCacheTablero();
        return resultado;
    }

    public Orden marcarOrdenLista(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Listo,
                orden.fecha(),
                orden.detalles(),
                orden.urgente(),
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);
        invalidarCacheTablero();

        if (pedidoApplicationService != null && orden.pedido() != null && orden.pedido().id() != null) {
            pedidoApplicationService.recalcularEstadoPedido(orden.pedido().id());
        }

        return resultado;
    }

    public Orden marcarOrdenEntregada(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Entregado,
                orden.fecha(),
                orden.detalles(),
                orden.urgente(),
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);
        invalidarCacheTablero();
        return resultado;
    }

    public Orden marcarOrdenComoListoNuevamente(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Listo,
                orden.fecha(),
                orden.detalles(),
                orden.urgente(),
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);
        invalidarCacheTablero();
        return resultado;
    }

    public Orden marcarOrdenUrgente(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                orden.ordenEstado(),
                orden.fecha(),
                orden.detalles(),
                true,
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);
        invalidarCacheTablero();
        return resultado;
    }

    public Orden desmarcarOrdenUrgente(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                orden.ordenEstado(),
                orden.fecha(),
                orden.detalles(),
                false,
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);
        invalidarCacheTablero();
        return resultado;
    }

    private Orden hidratarOrdenCompleta(Orden orden) {
        if (orden == null) return null;

        Pedido pedidoHidratado = hidratarPedido(orden.pedido());

        return new Orden(
                orden.id(),
                pedidoHidratado,
                orden.plato(),
                orden.precio(),
                orden.ordenEstado(),
                orden.fecha(),
                orden.detalles(),
                orden.urgente(),
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );
    }

    private Pedido hidratarPedido(Pedido pedidoBase) {
        if (pedidoBase == null || pedidoBase.id() == null) {
            return pedidoBase;
        }

        Pedido pedidoRepositorio = pedidoRepository.findById(pedidoBase.id()).orElse(pedidoBase);
        Cuenta cuentaBase = pedidoRepositorio.cuenta() != null ? pedidoRepositorio.cuenta() : pedidoBase.cuenta();
        Cuenta cuentaHidratada = hidratarCuenta(cuentaBase);

        return new Pedido(
                pedidoRepositorio.id(),
                cuentaHidratada,
                pedidoRepositorio.pedidoEstado(),
                pedidoRepositorio.fechaPedido()
        );
    }

    private Cuenta hidratarCuenta(Cuenta cuentaBase) {
        if (cuentaBase == null || cuentaBase.id() == null || cuentaRepository == null) {
            return cuentaBase;
        }

        Optional<Cuenta> cuentaOpt = cuentaRepository.findById(cuentaBase.id());
        return cuentaOpt.orElse(cuentaBase);
    }

    private boolean esOrdenDeCocina(Orden orden) {
        return orden != null
                && orden.plato() != null
                && orden.plato().categoria() != Categoria.Bebida
                && orden.ordenEstado() != OrdenEstado.Cancelado
                && !orden.pagada();
    }

    private String claveOrdenacionMesa(Orden orden) {
        if (orden == null || orden.pedido() == null || orden.pedido().cuenta() == null) {
            return "sin-mesa";
        }

        Cuenta cuenta = orden.pedido().cuenta();

        if (cuenta.mesas() == null || cuenta.mesas().isEmpty()) {
            return cuenta.id() != null ? cuenta.id() : "sin-mesa";
        }

        return cuenta.mesas().stream()
                .map(Mesa::id)
                .sorted()
                .reduce((a, b) -> a + "-" + b)
                .orElse("sin-mesa");
    }

    private Orden toOrden(CocinaOrden cocinaOrden) {
        return new Orden(
                cocinaOrden.id(),
                cocinaOrden.pedido(),
                cocinaOrden.plato(),
                cocinaOrden.precio(),
                cocinaOrden.ordenEstado(),
                cocinaOrden.fecha(),
                cocinaOrden.detalles(),
                cocinaOrden.urgente(),
                cocinaOrden.pagada(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private synchronized void cachearTablero(CocinaTablero tablero, Instant generadoEn) {
        this.cacheTablero = tablero;
        this.cacheTableroGeneradoEn = generadoEn;
    }

    private CocinaTablero cacheTableroVigente() {
        CocinaTablero actual = this.cacheTablero;
        Instant generadoEn = this.cacheTableroGeneradoEn;
        if (actual == null || generadoEn == null) {
            return null;
        }

        long edadMs = Instant.now().toEpochMilli() - generadoEn.toEpochMilli();
        return edadMs <= TABLERO_CACHE_TTL_MS ? actual : null;
    }

    private synchronized void invalidarCacheTablero() {
        this.cacheTablero = null;
        this.cacheTableroGeneradoEn = null;
    }
}
