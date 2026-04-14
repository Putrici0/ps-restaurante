package service;

import dto.OrdenRequest;
import model.Categoria;
import model.Cuenta;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import model.Plato;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;
import repository.interfaces.PlatoRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrdenService {

    private final OrdenRepository repository;
    private final PedidoRepository pedidoRepository;
    private final PlatoRepository platoRepository;
    private final PedidoService pedidoService;
    private final CuentaRepository cuentaRepository;

    public OrdenService(
            OrdenRepository repository,
            PedidoRepository pedidoRepository,
            PlatoRepository platoRepository,
            PedidoService pedidoService,
            CuentaRepository cuentaRepository
    ) {
        this.repository = repository;
        this.pedidoRepository = pedidoRepository;
        this.platoRepository = platoRepository;
        this.pedidoService = pedidoService;
        this.cuentaRepository = cuentaRepository;
    }

    public Orden create(OrdenRequest request) {
        validate(request);

        Pedido pedido = pedidoRepository.findById(request.pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe"));

        Plato plato = platoRepository.findById(request.platoId)
                .orElseThrow(() -> new IllegalArgumentException("El plato no existe"));

        Orden orden = new Orden(
                null,
                pedido,
                plato,
                new BigDecimal(request.precio.trim()),
                OrdenEstado.valueOf(request.estado.trim()),
                Instant.now(),
                request.detalles == null ? "" : request.detalles.trim()
        );

        return repository.save(orden);
    }

    public List<Orden> findAll() {
        return repository.findAll();
    }

    public Optional<Orden> findById(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    // Business Methods from OrdenApplicationService

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

        return hidratarOrden(repository.save(orden));
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

        pedidoService.recalcularEstadoPedido(pedidoId);
        return ordenes;
    }

    public List<Orden> obtenerOrdenesDePedido(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe"));

        return repository.findAll().stream()
                .filter(orden -> orden.pedido() != null)
                .filter(orden -> orden.pedido().id() != null)
                .filter(orden -> orden.pedido().id().equals(pedido.id()))
                .map(this::hidratarOrden)
                .toList();
    }

    public List<Orden> obtenerOrdenesPendientes() {
        return repository.findAll().stream()
                .map(this::hidratarOrden)
                .filter(orden -> orden.ordenEstado() == OrdenEstado.Pendiente)
                .filter(this::cuentaNoPagadaYaHidratada)
                .toList();
    }

    public List<Orden> obtenerOrdenesEnPreparacion() {
        return repository.findAll().stream()
                .map(this::hidratarOrden)
                .filter(orden -> orden.ordenEstado() == OrdenEstado.Preparación)
                .filter(this::cuentaNoPagadaYaHidratada)
                .toList();
    }

    public List<Orden> obtenerOrdenesListas() {
        return repository.findAll().stream()
                .map(this::hidratarOrden)
                .filter(orden -> orden.ordenEstado() == OrdenEstado.Listo)
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
        return repository.findAll().stream()
                .map(this::hidratarOrden)
                .filter(orden -> orden.plato() != null)
                .filter(orden -> orden.plato().categoria() != null)
                .filter(orden -> orden.plato().categoria() != Categoria.Bebida)
                .filter(orden -> orden.ordenEstado() == OrdenEstado.Listo || orden.ordenEstado() == OrdenEstado.Entregado)
                .filter(this::cuentaNoPagadaYaHidratada)
                .sorted((a, b) -> {
                    Instant fechaPedidoA = a.pedido() != null ? a.pedido().fechaPedido() : a.fecha();
                    Instant fechaPedidoB = b.pedido() != null ? b.pedido().fechaPedido() : b.fecha();
                    return fechaPedidoA.compareTo(fechaPedidoB);
                })
                .toList();
    }

    public Orden marcarOrdenPendiente(String ordenId) {
        Orden orden = findById(ordenId).orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Pendiente,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = repository.update(orden.id(), actualizada);
        pedidoService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    public Orden marcarOrdenEnPreparacion(String ordenId) {
        Orden orden = findById(ordenId).orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Preparación,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = repository.update(orden.id(), actualizada);
        pedidoService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    public Orden marcarOrdenLista(String ordenId) {
        Orden orden = findById(ordenId).orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Listo,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = repository.update(orden.id(), actualizada);
        pedidoService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    public Orden marcarOrdenEntregada(String ordenId) {
        Orden orden = findById(ordenId).orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        Orden actualizada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Entregado,
                orden.fecha(),
                orden.detalles()
        );

        Orden guardada = repository.update(orden.id(), actualizada);
        pedidoService.recalcularEstadoPedido(orden.pedido().id());
        return hidratarOrden(guardada);
    }

    private List<Orden> obtenerOrdenesCocinaPorEstados(List<OrdenEstado> estados) {
        return repository.findAll().stream()
                .map(this::hidratarOrden)
                .filter(orden -> estados.contains(orden.ordenEstado()))
                .filter(orden -> orden.plato() != null)
                .filter(orden -> orden.plato().categoria() != null)
                .filter(orden -> orden.plato().categoria() != Categoria.Bebida)
                .filter(this::cuentaNoPagadaYaHidratada)
                .sorted((a, b) -> a.fecha().compareTo(b.fecha()))
                .toList();
    }

    private List<Orden> obtenerOrdenesBarraPorEstados(List<OrdenEstado> estados) {
        return repository.findAll().stream()
                .map(this::hidratarOrden)
                .filter(orden -> estados.contains(orden.ordenEstado()))
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

    private void validate(OrdenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.pedidoId == null || request.pedidoId.isBlank()) {
            throw new IllegalArgumentException("El pedido es obligatorio");
        }
        if (request.platoId == null || request.platoId.isBlank()) {
            throw new IllegalArgumentException("El plato es obligatorio");
        }
        if (request.precio == null || request.precio.isBlank()) {
            throw new IllegalArgumentException("El precio es obligatorio");
        }
        if (request.estado == null || request.estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        try {
            new BigDecimal(request.precio.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("El precio no tiene un formato válido");
        }

        try {
            OrdenEstado.valueOf(request.estado.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("El estado de la orden no es válido");
        }
    }
}
