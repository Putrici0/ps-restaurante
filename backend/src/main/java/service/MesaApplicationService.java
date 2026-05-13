package service;

import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import repository.interfaces.CuentaRepository;
import repository.interfaces.MesaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MesaApplicationService {
    private final MesaRepository mesaRepository;
    private final CuentaRepository cuentaRepository;
    private final PedidoRepository pedidoRepository;
    private final OrdenRepository ordenRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    public MesaApplicationService(
            MesaRepository mesaRepository,
            CuentaRepository cuentaRepository,
            PedidoRepository pedidoRepository,
            OrdenRepository ordenRepository
    ) {
        this.mesaRepository = mesaRepository;
        this.cuentaRepository = cuentaRepository;
        this.pedidoRepository = pedidoRepository;
        this.ordenRepository = ordenRepository;
    }

    public boolean estaOcupada(String mesaId) {
        return obtenerCuentaActivaDeMesa(mesaId).isPresent();
    }

    public boolean estaLibre(String mesaId) {
        return !estaOcupada(mesaId);
    }

    public Mesa obtenerMesa(String mesaId) {
        return mesaRepository.findById(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe"));
    }

    public Optional<Cuenta> obtenerCuentaActivaDeMesa(String mesaId) {
        Mesa mesa = obtenerMesa(mesaId);
        return cuentaRepository.findByMesa(mesa);
    }

    public Cuenta ocuparMesa(String mesaId) {
        List<Mesa> grupoMesas = obtenerGrupoMesas(mesaId);
        Set<String> grupoIds = grupoMesas.stream().map(Mesa::id).collect(Collectors.toSet());

        if (!cuentasActivasDeGrupo(grupoIds).isEmpty()) {
            throw new IllegalArgumentException("La mesa ya esta ocupada");
        }

        Cuenta nuevaCuenta = new Cuenta(
                null,
                grupoMesas,
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty(),
                generarPassword(),
                Optional.empty()
        );

        return cuentaRepository.save(nuevaCuenta);
    }

    public Cuenta liberarMesa(String mesaId) {
        Cuenta cuentaActiva = obtenerCuentaActivaDeMesa(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa ya esta libre"));

        List<Pedido> pedidos = pedidoRepository.findByCuenta(cuentaActiva);
        List<String> pedidosIds = pedidos.stream()
                .map(Pedido::id)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        List<Orden> ordenes = ordenRepository.findByPedidosIds(pedidosIds);
        for (Orden orden : ordenes) {
            if (orden.ordenEstado() == OrdenEstado.Cancelado) {
                continue;
            }

            Orden ordenCancelada = new Orden(
                    orden.id(),
                    orden.pedido(),
                    orden.plato(),
                    orden.precio(),
                    OrdenEstado.Cancelado,
                    orden.fecha(),
                    orden.detalles(),
                    orden.urgente(),
                    orden.pagada(),
                    orden.fechaPago(),
                    orden.metodoPago()
            );

            ordenRepository.update(orden.id(), ordenCancelada);
        }

        Cuenta cuentaLiberada = new Cuenta(
                cuentaActiva.id(),
                cuentaActiva.mesas(),
                true,
                cuentaActiva.reserva(),
                cuentaActiva.fechaCreacion(),
                Optional.of(Instant.now()),
                "",
                cuentaActiva.metodoPago()
        );

        return cuentaRepository.update(cuentaActiva.id(), cuentaLiberada);
    }

    public List<Mesa> unirMesas(String mesaIdOrigen, String mesaIdDestino) {
        if (mesaIdOrigen == null || mesaIdOrigen.isBlank() || mesaIdDestino == null || mesaIdDestino.isBlank()) {
            throw new IllegalArgumentException("Debes indicar dos mesas validas para unir");
        }

        Mesa mesaOrigen = obtenerMesa(mesaIdOrigen);
        Mesa mesaDestino = obtenerMesa(mesaIdDestino);

        Set<String> grupoIds = new LinkedHashSet<>(grupoIdsDe(mesaOrigen));
        grupoIds.addAll(grupoIdsDe(mesaDestino));

        if (grupoIds.size() < 2) {
            return obtenerGrupoMesas(mesaIdOrigen);
        }

        List<Mesa> mesasDelGrupo = grupoIds.stream()
                .map(this::obtenerMesa)
                .sorted(Comparator.comparing(Mesa::id, this::compararMesaId))
                .toList();

        List<Cuenta> cuentasActivas = cuentasActivasDeGrupo(grupoIds);
        Set<String> cuentasActivasIds = cuentasActivas.stream()
                .map(Cuenta::id)
                .collect(Collectors.toSet());

        if (cuentasActivasIds.size() > 1) {
            throw new IllegalArgumentException("No se pueden unir mesas con cuentas activas distintas");
        }

        for (Mesa mesa : mesasDelGrupo) {
            mesaRepository.update(mesa.id(), new Mesa(mesa.id(), mesa.capacidad(), List.copyOf(grupoIds)));
        }

        if (!cuentasActivas.isEmpty()) {
            Cuenta cuentaActiva = cuentasActivas.getFirst();
            Cuenta cuentaActualizada = new Cuenta(
                    cuentaActiva.id(),
                    mesasDelGrupo,
                    cuentaActiva.payed(),
                    cuentaActiva.reserva(),
                    cuentaActiva.fechaCreacion(),
                    cuentaActiva.fechaPago(),
                    cuentaActiva.password(),
                    cuentaActiva.metodoPago()
            );
            cuentaRepository.update(cuentaActiva.id(), cuentaActualizada);
        }

        return obtenerGrupoMesas(mesaIdOrigen);
    }

    public List<Mesa> separarMesa(String mesaId) {
        List<Mesa> grupoMesas = obtenerGrupoMesas(mesaId);
        if (grupoMesas.size() <= 1) {
            return grupoMesas;
        }

        Set<String> grupoIds = grupoMesas.stream().map(Mesa::id).collect(Collectors.toSet());
        if (!cuentasActivasDeGrupo(grupoIds).isEmpty()) {
            throw new IllegalArgumentException("No se puede separar una agrupacion con una cuenta activa");
        }

        for (Mesa mesa : grupoMesas) {
            mesaRepository.update(mesa.id(), new Mesa(mesa.id(), mesa.capacidad(), List.of(mesa.id())));
        }

        return obtenerGrupoMesas(mesaId);
    }

    public List<Pedido> obtenerPedidosActivosDeMesa(String mesaId) {
        Optional<Cuenta> cuentaActiva = obtenerCuentaActivaDeMesa(mesaId);

        if (cuentaActiva.isEmpty()) {
            return List.of();
        }

        return pedidoRepository.findByCuenta(cuentaActiva.get());
    }

    public List<Orden> obtenerOrdenesActivasDeMesa(String mesaId) {
        List<Pedido> pedidos = obtenerPedidosActivosDeMesa(mesaId);
        List<String> pedidosIds = pedidos.stream()
                .map(Pedido::id)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        return ordenRepository.findByPedidosIds(pedidosIds);
    }

    public Cuenta validarAccesoMesa(String mesaId, String password) {
        Cuenta cuentaActiva = obtenerCuentaActivaDeMesa(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no tiene cuenta activa"));

        String passwordGuardada = cuentaActiva.password() == null ? "" : cuentaActiva.password().trim();
        String passwordRecibida = password == null ? "" : password.trim();

        if (!passwordGuardada.equals(passwordRecibida)) {
            throw new IllegalArgumentException("La contrasena no es correcta");
        }

        return cuentaActiva;
    }

    private String generarPassword() {
        int pin = RANDOM.nextInt(9000) + 1000;
        return String.valueOf(pin);
    }

    private List<Mesa> obtenerGrupoMesas(String mesaId) {
        Mesa mesa = obtenerMesa(mesaId);
        return grupoIdsDe(mesa).stream()
                .map(id -> mesa.id().equals(id) ? mesa : obtenerMesa(id))
                .sorted(Comparator.comparing(Mesa::id, this::compararMesaId))
                .toList();
    }

    private List<String> grupoIdsDe(Mesa mesa) {
        if (mesa == null) {
            return List.of();
        }

        return mesa.mesasUnidas() == null || mesa.mesasUnidas().isEmpty()
                ? List.of(mesa.id())
                : mesa.mesasUnidas();
    }

    private List<Cuenta> cuentasActivasDeGrupo(Set<String> grupoIds) {
        if (grupoIds == null || grupoIds.isEmpty()) {
            return List.of();
        }

        return cuentaRepository.findActivasByMesaIds(List.copyOf(grupoIds));
    }

    private int compararMesaId(String left, String right) {
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException e) {
            return left.compareTo(right);
        }
    }
}
