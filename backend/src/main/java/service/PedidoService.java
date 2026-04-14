package service;

import dto.CrearPedidoClienteRequest;
import dto.PedidoRequest;
import model.Cuenta;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import model.PedidoEstado;
import model.Plato;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;
import repository.interfaces.PlatoRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PedidoService {

    private final PedidoRepository repository;
    private final CuentaRepository cuentaRepository;
    private final OrdenRepository ordenRepository;
    private final PlatoRepository platoRepository;
    private final MesaService mesaService;

    public PedidoService(PedidoRepository repository,
                         CuentaRepository cuentaRepository,
                         OrdenRepository ordenRepository,
                         PlatoRepository platoRepository,
                         MesaService mesaService) {
        this.repository = repository;
        this.cuentaRepository = cuentaRepository;
        this.ordenRepository = ordenRepository;
        this.platoRepository = platoRepository;
        this.mesaService = mesaService;
    }

    public Pedido create(PedidoRequest request) {
        validate(request);

        Cuenta cuenta = cuentaRepository.findById(request.cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        Pedido pedido = new Pedido(
                null,
                cuenta,
                PedidoEstado.valueOf(request.estado.trim()),
                Instant.now()
        );

        return repository.save(pedido);
    }

    public List<Pedido> findAll() {
        return repository.findAll();
    }

    public Optional<Pedido> findById(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    // Business Methods from PedidoApplicationService

    public Pedido crearPedidoDesdeMesa(String mesaId) {
        Cuenta cuenta = mesaService.obtenerCuentaActivaDeMesa(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no tiene cuenta activa"));

        return crearPedidoEnCuenta(cuenta.id());
    }

    public Pedido crearPedidoEnCuenta(String cuentaId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        if (cuenta.payed()) {
            throw new IllegalArgumentException("No se puede crear un pedido en una cuenta pagada");
        }

        Pedido nuevoPedido = new Pedido(
                null,
                cuenta,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        return repository.save(nuevoPedido);
    }

    public CrearPedidoResultado crearPedidoConOrdenesDesdeMesa(String mesaId, CrearPedidoClienteRequest request) {
        Cuenta cuenta = mesaService.obtenerCuentaActivaDeMesa(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no tiene cuenta activa"));

        return crearPedidoConOrdenesEnCuenta(cuenta.id(), request);
    }

    public CrearPedidoResultado crearPedidoConOrdenesEnCuenta(String cuentaId, CrearPedidoClienteRequest request) {
        validarRequestCreacion(request);

        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        if (cuenta.payed()) {
            throw new IllegalArgumentException("No se puede crear un pedido en una cuenta pagada");
        }

        Pedido pedido = new Pedido(
                null,
                cuenta,
                PedidoEstado.Pendiente,
                Instant.now()
        );

        Pedido pedidoGuardado = repository.save(pedido);

        List<Orden> ordenesCreadas = new ArrayList<>();

        try {
            for (CrearPedidoClienteRequest.ItemPedidoRequest item : request.items) {
                validarItem(item);

                Plato plato = platoRepository.findById(item.platoId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "El plato con id " + item.platoId + " no existe"
                        ));

                if (!plato.estaActivo()) {
                    throw new IllegalArgumentException("El plato " + plato.nombre() + " no está disponible");
                }

                String detalles = item.detalles == null ? "" : item.detalles.trim();

                for (int i = 0; i < item.cantidad; i++) {
                    Orden orden = new Orden(
                            null,
                            pedidoGuardado,
                            plato,
                            plato.precio(),
                            OrdenEstado.Pendiente,
                            Instant.now(),
                            detalles
                    );

                    Orden ordenGuardada = ordenRepository.save(orden);
                    ordenesCreadas.add(ordenGuardada);
                }
            }

            return new CrearPedidoResultado(pedidoGuardado, List.copyOf(ordenesCreadas));
        } catch (RuntimeException e) {
            rollbackPedido(pedidoGuardado, ordenesCreadas);
            throw e;
        }
    }

    public List<Pedido> obtenerPedidosDeCuenta(String cuentaId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));

        return repository.findAll().stream()
                .filter(pedido -> pedido.cuenta() != null)
                .filter(pedido -> pedido.cuenta().id() != null)
                .filter(pedido -> pedido.cuenta().id().equals(cuenta.id()))
                .toList();
    }

    public List<Pedido> obtenerPedidosActivosDeMesa(String mesaId) {
        Cuenta cuenta = mesaService.obtenerCuentaActivaDeMesa(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no tiene cuenta activa"));

        return obtenerPedidosDeCuenta(cuenta.id());
    }

    public Pedido recalcularEstadoPedido(String pedidoId) {
        Pedido pedido = findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe"));

        List<Orden> ordenes = ordenRepository.findAll().stream()
                .filter(orden -> orden.pedido() != null)
                .filter(orden -> orden.pedido().id() != null)
                .filter(orden -> orden.pedido().id().equals(pedido.id()))
                .toList();

        boolean todasListasOEntregadas = !ordenes.isEmpty() && ordenes.stream().allMatch(o ->
                o.ordenEstado() == OrdenEstado.Listo || o.ordenEstado() == OrdenEstado.Entregado
        );

        Pedido actualizado = new Pedido(
                pedido.id(),
                pedido.cuenta(),
                todasListasOEntregadas ? PedidoEstado.Listo : PedidoEstado.Pendiente,
                pedido.fechaPedido()
        );

        return repository.update(pedido.id(), actualizado);
    }

    public boolean pedidoEstaListo(String pedidoId) {
        Pedido pedido = findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe"));
        return pedido.pedidoEstado() == PedidoEstado.Listo;
    }

    private void validarRequestCreacion(CrearPedidoClienteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }

        if (request.items == null || request.items.isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos un plato");
        }
    }

    private void validarItem(CrearPedidoClienteRequest.ItemPedidoRequest item) {
        if (item == null) {
            throw new IllegalArgumentException("Uno de los items del pedido es nulo");
        }

        if (item.platoId == null || item.platoId.isBlank()) {
            throw new IllegalArgumentException("Todos los items deben tener platoId");
        }

        if (item.cantidad == null || item.cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de cada plato debe ser mayor que 0");
        }
    }

    private void rollbackPedido(Pedido pedido, List<Orden> ordenesCreadas) {
        for (Orden orden : ordenesCreadas) {
            if (orden != null && orden.id() != null) {
                try {
                    ordenRepository.deleteById(orden.id());
                } catch (Exception ignored) {
                }
            }
        }

        if (pedido != null && pedido.id() != null) {
            try {
                repository.deleteById(pedido.id());
            } catch (Exception ignored) {
            }
        }
    }

    public record CrearPedidoResultado(Pedido pedido, List<Orden> ordenes) {
    }

    private void validate(PedidoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.cuentaId == null || request.cuentaId.isBlank()) {
            throw new IllegalArgumentException("La cuenta es obligatoria");
        }
        if (request.estado == null || request.estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        try {
            PedidoEstado.valueOf(request.estado.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("El estado del pedido no es válido");
        }
    }
}