package service.application;

import dto.cocina.CocinaOrden;
import dto.cocina.CocinaTablero;
import service.domain.cocina.CocinaContextoCuenta;
import service.domain.cocina.CocinaContextoService;
import service.domain.cocina.CocinaOrdenMapper;
import service.domain.cocina.CocinaPrioridadService;
import model.*;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;
import repository.interfaces.PlatoRepository;

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

        List<Cuenta> cuentasActivas = cuentaRepository.findByEstaPagada(false);

        List<Pedido> pedidosActivos = cuentasActivas.parallelStream()
                .flatMap(cuenta -> pedidoRepository.findByCuenta(cuenta).stream()
                        .map(p -> new Pedido(p.id(), cuenta, p.pedidoEstado(), p.fechaPedido()))
                )
                .toList();

        java.util.Map<String, Pedido> pedidoMap = pedidosActivos.stream()
                .filter(p -> p.id() != null)
                .collect(java.util.stream.Collectors.toMap(Pedido::id, p -> p));

        return pedidosActivos.parallelStream()
                .flatMap(pedido -> ordenRepository.findByPedido(pedido).stream())
                .map(orden -> {
                    String pedidoId = (orden.pedido() != null) ? orden.pedido().id() : null;
                    Pedido pedidoHidratado = pedidoMap.getOrDefault(pedidoId, orden.pedido());

                    return new Orden(
                            orden.id(),
                            pedidoHidratado,
                            orden.plato(),
                            orden.precio(),
                            orden.ordenEstado(),
                            orden.fecha(),
                            orden.detalles(),
                            orden.pagada(),
                            orden.fechaPago(),
                            orden.metodoPago()
                    );
                })
                .toList();
    }

    public CocinaTablero obtenerTableroCocinaPriorizado() {
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

        List<Orden> listas = ordenesCocina.stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo)
                .sorted(
                        Comparator
                                .comparing(this::claveOrdenacionMesa)
                                .thenComparing(Orden::fecha, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .toList();

        return new CocinaTablero(
                mapearConPrioridad(pendientes, contexto, ahora),
                mapearConPrioridad(enPreparacion, contexto, ahora),
                mapearConPrioridad(listas, contexto, ahora),
                ahora
        );
    }

    private List<CocinaOrden> mapearConPrioridad(
            List<Orden> ordenes,
            Map<String, CocinaContextoCuenta> contexto,
            Instant ahora
    ) {
        return ordenes.stream()
                .map(orden -> cocinaOrdenMapper.toCocinaOrden(
                        orden,
                        cocinaPrioridadService.calcularPrioridad(orden, contexto, ahora)
                ))
                .toList();
    }

    public List<Orden> obtenerBebidasActivasBarra() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() != OrdenEstado.Entregado && o.ordenEstado() != OrdenEstado.Cancelado)
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
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        return ordenRepository.update(id, actualizada);
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
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        return ordenRepository.update(id, actualizada);
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
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        Orden resultado = ordenRepository.update(id, actualizada);

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
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        return ordenRepository.update(id, actualizada);
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
                orden.pagada(),
                orden.fechaPago(),
                orden.metodoPago()
        );

        return ordenRepository.update(id, actualizada);
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
                cocinaOrden.detalles()
        );
    }
}