package service;

import dto.MesaRequest;
import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.Pedido;
import repository.interfaces.CuentaRepository;
import repository.interfaces.MesaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MesaService {

    private final MesaRepository mesaRepository;
    private final CuentaRepository cuentaRepository;
    private final PedidoRepository pedidoRepository;
    private final OrdenRepository ordenRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    public MesaService(
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

    public Mesa create(MesaRequest request) {
        validate(request);

        Mesa mesa = new Mesa(
                null,
                request.capacidad
        );

        return mesaRepository.save(mesa);
    }

    public List<Mesa> findAll() {
        return mesaRepository.findAll();
    }

    public Optional<Mesa> findById(String id) {
        return mesaRepository.findById(id);
    }

    public Mesa update(String id, MesaRequest request) {
        validate(request);

        Mesa actualizada = new Mesa(
                id,
                request.capacidad
        );

        return mesaRepository.update(id, actualizada);
    }

    public void delete(String id) {
        mesaRepository.deleteById(id);
    }

    public boolean estaOcupada(String mesaId) {
        return obtenerCuentaActivaDeMesa(mesaId).isPresent();
    }

    public boolean estaLibre(String mesaId) {
        return !estaOcupada(mesaId);
    }

    public Mesa obtenerMesa(String mesaId) {
        return findById(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe"));
    }

    public Optional<Cuenta> obtenerCuentaActivaDeMesa(String mesaId) {
        Mesa mesa = obtenerMesa(mesaId);
        return cuentaRepository.findByMesa(mesa);
    }

    public Cuenta ocuparMesa(String mesaId) {
        Mesa mesa = obtenerMesa(mesaId);

        if (estaOcupada(mesaId)) {
            throw new IllegalArgumentException("La mesa ya está ocupada");
        }

        Cuenta nuevaCuenta = new Cuenta(
                null,
                List.of(mesa),
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty(),
                generarPassword()
        );

        return cuentaRepository.save(nuevaCuenta);
    }

    public Cuenta liberarMesa(String mesaId) {
        Cuenta cuentaActiva = obtenerCuentaActivaDeMesa(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa ya está libre"));

        Cuenta cuentaLiberada = new Cuenta(
                cuentaActiva.id(),
                cuentaActiva.mesas(),
                true,
                cuentaActiva.reserva(),
                cuentaActiva.fechaCreacion(),
                Optional.of(Instant.now()),
                ""
        );

        return cuentaRepository.update(cuentaActiva.id(), cuentaLiberada);
    }

    public List<Pedido> obtenerPedidosActivosDeMesa(String mesaId) {
        Optional<Cuenta> cuentaActiva = obtenerCuentaActivaDeMesa(mesaId);

        if (cuentaActiva.isEmpty()) {
            return List.of();
        }

        String cuentaId = cuentaActiva.get().id();

        return pedidoRepository.findAll().stream()
                .filter(pedido -> pedido.cuenta() != null)
                .filter(pedido -> pedido.cuenta().id() != null)
                .filter(pedido -> pedido.cuenta().id().equals(cuentaId))
                .toList();
    }

    public List<Orden> obtenerOrdenesActivasDeMesa(String mesaId) {
        List<Pedido> pedidos = obtenerPedidosActivosDeMesa(mesaId);
        List<Orden> ordenes = new ArrayList<>();

        for (Pedido pedido : pedidos) {
            List<Orden> ordenesPedido = ordenRepository.findAll().stream()
                    .filter(orden -> orden.pedido() != null)
                    .filter(orden -> orden.pedido().id() != null)
                    .filter(orden -> orden.pedido().id().equals(pedido.id()))
                    .toList();

            ordenes.addAll(ordenesPedido);
        }

        return ordenes;
    }

    public Cuenta validarAccesoMesa(String mesaId, String password) {
        Cuenta cuentaActiva = obtenerCuentaActivaDeMesa(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no tiene cuenta activa"));

        String passwordGuardada = cuentaActiva.password() == null ? "" : cuentaActiva.password().trim();
        String passwordRecibida = password == null ? "" : password.trim();

        if (!passwordGuardada.equals(passwordRecibida)) {
            throw new IllegalArgumentException("La contraseña no es correcta");
        }

        return cuentaActiva;
    }

    private String generarPassword() {
        int pin = RANDOM.nextInt(9000) + 1000;
        return String.valueOf(pin);
    }

    private void validate(MesaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la petición no puede ser nulo");
        }
        if (request.capacidad == null) {
            throw new IllegalArgumentException("La capacidad es obligatoria");
        }
        if (request.capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser mayor que 0");
        }
    }
}