package service.application;

import model.*;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;
import repository.interfaces.PlatoRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrdenApplicationService {
    private final OrdenRepository ordenRepository;
    private final PedidoRepository pedidoRepository;
    private final PlatoRepository platoRepository;
    private final PedidoApplicationService pedidoApplicationService;
    private final CuentaRepository cuentaRepository;

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

    /// =========================================================
    // MÉTODO MAESTRO OPTIMIZADO (Cero N+1)
    // =========================================================
    private List<Orden> obtenerTodasLasOrdenesActivas() {
        if (cuentaRepository == null) {
            return List.of();
        }

        // 1. Obtenemos cuentas sin pagar (1 sola llamada a BD)
        List<Cuenta> cuentasActivas = cuentaRepository.findByEstaPagada(false);

        // 2. Obtenemos pedidos y los "hidratamos" INMEDIATAMENTE usando las cuentas en memoria
        List<Pedido> pedidosActivos = cuentasActivas.parallelStream()
                .flatMap(cuenta -> pedidoRepository.findByCuenta(cuenta).stream()
                        // Hidratación in-memory: Le inyectamos la cuenta que ya tenemos
                        .map(p -> new Pedido(p.id(), cuenta, p.pedidoEstado(), p.fechaPedido()))
                )
                .toList();

        // Creamos un mapa rápido para buscar pedidos por ID en O(1)
        java.util.Map<String, Pedido> pedidoMap = pedidosActivos.stream()
                .filter(p -> p.id() != null)
                .collect(java.util.stream.Collectors.toMap(Pedido::id, p -> p));

        // 3. Obtenemos las órdenes y las enlazamos con los pedidos en memoria (Cero llamadas a BD)
        return pedidosActivos.parallelStream()
                .flatMap(pedido -> ordenRepository.findByPedido(pedido).stream())
                .map(orden -> {
                    // Buscamos el pedido en la RAM, no en Firebase
                    String pedidoId = (orden.pedido() != null) ? orden.pedido().id() : null;
                    Pedido pedidoHidratado = pedidoMap.getOrDefault(pedidoId, orden.pedido());

                    return new Orden(
                            orden.id(),
                            pedidoHidratado,
                            orden.plato(),
                            orden.precio(),
                            orden.ordenEstado(),
                            orden.fecha(),
                            orden.detalles()
                    );
                })
                .toList();
    }

    // =========================================================
    // MÉTODOS DE LECTURA OPTIMIZADOS (VISTAS PRINCIPALES)
    // =========================================================

    public List<Orden> obtenerBebidasActivasBarra() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                // Excluimos las entregadas y canceladas
                .filter(o -> o.ordenEstado() != OrdenEstado.Entregado && o.ordenEstado() != OrdenEstado.Cancelado)
                .toList();
    }

    public List<Orden> obtenerPlatosActivosSala() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() != Categoria.Bebida)
                // En sala solo nos interesan los listos o entregados recientemente
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo || o.ordenEstado() == OrdenEstado.Entregado)
                .toList();
    }

    // =========================================================
    // MÉTODOS RESTAURADOS PARA QUE COMPILE EL CONTROLLER Y COCINA
    // =========================================================

    // --- GENÉRICOS ---
    public List<Orden> obtenerOrdenesPendientes() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Pendiente).toList();
    }
    public List<Orden> obtenerOrdenesEnPreparacion() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Preparación).toList();
    }
    public List<Orden> obtenerOrdenesListas() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo).toList();
    }

    // --- COCINA (Platos) ---
    public List<Orden> obtenerOrdenesCocinaPendientes() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() != Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Pendiente).toList();
    }
    public List<Orden> obtenerOrdenesCocinaEnPreparacion() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() != Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Preparación).toList();
    }
    public List<Orden> obtenerOrdenesCocinaListas() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() != Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo).toList();
    }

    // --- BARRA (Bebidas) ---
    public List<Orden> obtenerOrdenesBarraPendientes() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Pendiente).toList();
    }
    public List<Orden> obtenerOrdenesBarraEnPreparacion() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Preparación).toList();
    }
    public List<Orden> obtenerOrdenesBarraListas() {
        return obtenerTodasLasOrdenesActivas().stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == Categoria.Bebida)
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo).toList();
    }

    // --- GESTIÓN DE CREACIÓN DE PEDIDOS ---
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


    // =========================================================
    // MÉTODOS DE CAMBIO DE ESTADO (ESCRITURA)
    // =========================================================

    public Orden marcarOrdenPendiente(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(), orden.pedido(), orden.plato(), orden.precio(),
                OrdenEstado.Pendiente, orden.fecha(), orden.detalles()
        );

        return ordenRepository.update(id, actualizada);
    }

    public Orden marcarOrdenEnPreparacion(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(), orden.pedido(), orden.plato(), orden.precio(),
                OrdenEstado.Preparación, orden.fecha(), orden.detalles()
        );

        return ordenRepository.update(id, actualizada);
    }

    public Orden marcarOrdenLista(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(), orden.pedido(), orden.plato(), orden.precio(),
                OrdenEstado.Listo, orden.fecha(), orden.detalles()
        );

        Orden resultado = ordenRepository.update(id, actualizada);

        // Si todos los platos de un pedido están listos, recalcula el estado general del pedido
        if (pedidoApplicationService != null && orden.pedido() != null && orden.pedido().id() != null) {
            pedidoApplicationService.recalcularEstadoPedido(orden.pedido().id());
        }
        return resultado;
    }

    public Orden marcarOrdenEntregada(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(), orden.pedido(), orden.plato(), orden.precio(),
                OrdenEstado.Entregado, orden.fecha(), orden.detalles()
        );

        return ordenRepository.update(id, actualizada);
    }

    public Orden marcarOrdenComoListoNuevamente(String id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(), orden.pedido(), orden.plato(), orden.precio(),
                OrdenEstado.Listo, orden.fecha(), orden.detalles()
        );

        return ordenRepository.update(id, actualizada);
    }

    // =========================================================
    // UTILIDADES DE HIDRATACIÓN (Para resolver referencias de Firestore)
    // =========================================================

    private Orden hidratarOrdenCompleta(Orden orden) {
        if (orden == null) return null;
        Pedido pedidoHidratado = hidratarPedido(orden.pedido());
        return new Orden(
                orden.id(), pedidoHidratado, orden.plato(), orden.precio(),
                orden.ordenEstado(), orden.fecha(), orden.detalles()
        );
    }

    private Pedido hidratarPedido(Pedido pedidoBase) {
        if (pedidoBase == null || pedidoBase.id() == null) {
            return pedidoBase;
        }

        Pedido pedidoRepositorio = pedidoRepository.findById(pedidoBase.id()).orElse(pedidoBase);

        Cuenta cuentaBase = pedidoRepositorio.cuenta() != null
                ? pedidoRepositorio.cuenta()
                : pedidoBase.cuenta();

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
}