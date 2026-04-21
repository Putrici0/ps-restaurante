package service;

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
                generarPassword(),
                Optional.empty()
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
                "",
                cuentaActiva.metodoPago()
        );

        return cuentaRepository.update(cuentaActiva.id(), cuentaLiberada);
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
        List<Orden> ordenes = new ArrayList<>();

        for (Pedido pedido : pedidos) {
            ordenes.addAll(ordenRepository.findByPedido(pedido));
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
}