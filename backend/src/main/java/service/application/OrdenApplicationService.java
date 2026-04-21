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

    public Orden crearOrdenDesdePedidoYPlato(String pedidoId, String platoId, String detalles) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe"));

        Pedido pedidoHidratado = hidratarPedido(pedido);

        Plato plato = platoRepository.findById(platoId)
                .orElseThrow(() -> new IllegalArgumentException("El plato no existe"));

        Orden orden = new Orden(
                null,
                pedidoHidratado,
                plato,
                plato.precio(),
                OrdenEstado.Pendiente,
                Instant.now(),
                detalles == null ? "" : detalles.trim()
        );

        return hidratarOrden(ordenRepository.save(orden));
    }

    public List<Orden> crearOrdenesDesdePedido(String pedidoId, List<String> platosIds) {
        return crearOrdenesDesdePedido(pedidoId, platosIds, null);
    }

    public List<Orden> crearOrdenesDesdePedido(String pedidoId, List<String> platosIds, List<String> detalles) {
        if (platosIds == null || platosIds.isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos un plato");
        }

        List<Orden> ordenes = new ArrayList<>();

        for (int i = 0; i < platosIds.size(); i++) {
            String platoId = platosIds.get(i);
            String detalle = "";

            if (detalles != null && i < detalles.size() && detalles.get(i) != null) {
                detalle = detalles.get(i);
            }

            Orden orden = crearOrdenDesdePedidoYPlato(pedidoId, platoId, detalle);
            ordenes.add(orden);
        }

        pedidoApplicationService.recalcularEstadoPedido(pedidoId);
        return ordenes;
    }

    public Orden obtenerOrdenPorId(String ordenId) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        return hidratarOrden(orden);
    }

    public List<Orden> obtenerOrdenesDePedido(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe"));

        return ordenRepository.findByPedido(pedido).stream()
                .map(this::hidratarOrden)
                .toList();
    }

    public List<Orden> obtenerOrdenesPendientes() {
        return ordenRepository.findByEstado(OrdenEstado.Pendiente).stream()
                .map(this::hidratarOrden)
                .filter(this::cuentaNoPagadaYaHidratada)
                .toList();
    }

    public List<Orden> obtenerOrdenesEnPreparacion() {
        return ordenRepository.findByEstado(OrdenEstado.Preparación).stream()
                .map(this::hidratarOrden)
                .filter(this::cuentaNoPagadaYaHidratada)
                .toList();
    }

    public List<Orden> obtenerOrdenesListas() {
        return ordenRepository.findByEstado(OrdenEstado.Listo).stream()
                .map(this::hidratarOrden)
                .filter(this::cuentaNoPagadaYaHidratada)
                .toList();
    }

    public List<Orden> obtenerOrdenesCocinaPendientes() {
        return obtenerOrdenesCocinaPorEstados(List.of(OrdenEstado.Pendiente));
    }

    public List<Orden> obtenerOrdenesCocinaEnPreparacion() {
        return obtenerOrdenesCocinaPorEstados(List.of(OrdenEstado.Preparación));
    }

    public List<Orden> obtenerOrdenesCocinaListas() {
        return obtenerOrdenesCocinaPorEstados(List.of(OrdenEstado.Listo));
    }

    public List<Orden> obtenerOrdenesBarraPendientes() {
        return obtenerOrdenesBarraPorEstados(List.of(OrdenEstado.Pendiente));
    }

    public List<Orden> obtenerOrdenesBarraEnPreparacion() {
        return obtenerOrdenesBarraPorEstados(List.of(OrdenEstado.Preparación));
    }

    public List<Orden> obtenerOrdenesBarraListas() {
        return obtenerOrdenesBarraPorEstados(List.of(OrdenEstado.Listo));
    }

    public List<Orden> obtenerOrdenesSalaPlatos() {
        List<Orden> listas = ordenRepository.findByEstado(OrdenEstado.Listo);
        List<Orden> entregadas = ordenRepository.findByEstado(OrdenEstado.Entregado);

        List<Orden> combinadas = new ArrayList<>();
        combinadas.addAll(listas);
        combinadas.addAll(entregadas);

        return combinadas.stream()
                .map(this::hidratarOrden)
                .filter(orden -> orden.plato() != null)
                .filter(orden -> orden.plato().categoria() != null)
                .filter(orden -> orden.plato().categoria() != Categoria.Bebida)
                .filter(this::cuentaNoPagadaYaHidratada)
                .sorted((a, b) -> {
                    Instant fechaPedidoA = a.pedido() != null ? a.pedido().fechaPedido() : a.fecha();
                    Instant fechaPedidoB = b.pedido() != null ? b.pedido().fechaPedido() : b.fecha();
                    return fechaPedidoA.compareTo(fechaPedidoB);
                })
                .toList();
    }

    public Orden marcarOrdenPendiente(String ordenId) {
        Orden orden = obtenerOrdenPorId(ordenId);

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Pendiente,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = ordenRepository.update(orden.id(), actualizada);
        pedidoApplicationService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    public Orden marcarOrdenEnPreparacion(String ordenId) {
        Orden orden = obtenerOrdenPorId(ordenId);

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Preparación,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = ordenRepository.update(orden.id(), actualizada);
        pedidoApplicationService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    public Orden marcarOrdenLista(String ordenId) {
        Orden orden = obtenerOrdenPorId(ordenId);

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Listo,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = ordenRepository.update(orden.id(), actualizada);
        pedidoApplicationService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    public Orden marcarOrdenEntregada(String ordenId) {
        Orden orden = obtenerOrdenPorId(ordenId);

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Entregado,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = ordenRepository.update(orden.id(), actualizada);
        pedidoApplicationService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    public boolean estanTodasListasLasOrdenes(String pedidoId) {
        List<Orden> ordenesActivas = obtenerOrdenesDePedido(pedidoId).stream()
                .filter(orden -> orden.ordenEstado() != OrdenEstado.Cancelado)
                .toList();

        return !ordenesActivas.isEmpty()
                && ordenesActivas.stream().allMatch(orden ->
                orden.ordenEstado() == OrdenEstado.Listo || orden.ordenEstado() == OrdenEstado.Entregado
        );
    }

    private List<Orden> obtenerOrdenesCocinaPorEstados(List<OrdenEstado> estados) {
        List<Orden> acumuladas = new ArrayList<>();

        for (OrdenEstado estado : estados) {
            acumuladas.addAll(ordenRepository.findByEstado(estado));
        }

        return acumuladas.stream()
                .map(this::hidratarOrden)
                .filter(orden -> orden.plato() != null)
                .filter(orden -> orden.plato().categoria() != null)
                .filter(orden -> orden.plato().categoria() != Categoria.Bebida)
                .filter(this::cuentaNoPagadaYaHidratada)
                .sorted((a, b) -> a.fecha().compareTo(b.fecha()))
                .toList();
    }

    private List<Orden> obtenerOrdenesBarraPorEstados(List<OrdenEstado> estados) {
        List<Orden> acumuladas = new ArrayList<>();

        for (OrdenEstado estado : estados) {
            acumuladas.addAll(ordenRepository.findByEstado(estado));
        }

        return acumuladas.stream()
                .map(this::hidratarOrden)
                .filter(orden -> orden.plato() != null)
                .filter(orden -> orden.plato().categoria() != null)
                .filter(orden -> orden.plato().categoria() == Categoria.Bebida)
                .filter(this::cuentaNoPagadaYaHidratada)
                .sorted((a, b) -> {
                    Instant fechaPedidoA = a.pedido() != null ? a.pedido().fechaPedido() : a.fecha();
                    Instant fechaPedidoB = b.pedido() != null ? b.pedido().fechaPedido() : b.fecha();
                    return fechaPedidoA.compareTo(fechaPedidoB);
                })
                .toList();
    }

    private boolean cuentaNoPagadaYaHidratada(Orden orden) {
        if (orden == null || orden.pedido() == null || orden.pedido().cuenta() == null) {
            return true;
        }
        return !orden.pedido().cuenta().payed();
    }

    private Orden hidratarOrden(Orden orden) {
        if (orden == null) {
            return null;
        }

        Pedido pedidoHidratado = hidratarPedido(orden.pedido());

        return new Orden(
                orden.id(),
                pedidoHidratado,
                orden.plato(),
                orden.precio(),
                orden.ordenEstado(),
                orden.fecha(),
                orden.detalles()
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
        if (cuentaBase == null || cuentaBase.id() == null) {
            return cuentaBase;
        }

        if (cuentaRepository == null) {
            return cuentaBase;
        }

        Optional<Cuenta> cuentaOpt = cuentaRepository.findById(cuentaBase.id());
        return cuentaOpt.orElse(cuentaBase);
    }
}