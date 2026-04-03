package service.application;

import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.Pedido;
import repository.firestore.FirestoreCuentaRepository;
import repository.firestore.FirestoreMesaRepository;
import repository.firestore.FirestoreOrdenRepository;
import repository.firestore.FirestorePedidoRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MesaApplicationService {

    private final FirestoreMesaRepository mesaRepository;
    private final FirestoreCuentaRepository cuentaRepository;
    private final FirestorePedidoRepository pedidoRepository;
    private final FirestoreOrdenRepository ordenRepository;

    public MesaApplicationService(
            FirestoreMesaRepository mesaRepository,
            FirestoreCuentaRepository cuentaRepository,
            FirestorePedidoRepository pedidoRepository,
            FirestoreOrdenRepository ordenRepository
    ) {
        this.mesaRepository = mesaRepository;
        this.cuentaRepository = cuentaRepository;
        this.pedidoRepository = pedidoRepository;
        this.ordenRepository = ordenRepository;
    }

    /**
     * Una mesa está ocupada si existe una cuenta activa (no pagada) asociada a ella.
     */
    public boolean estaOcupada(String mesaId) {
        return obtenerCuentaActivaDeMesa(mesaId).isPresent();
    }

    /**
     * Una mesa está libre si no tiene cuenta activa.
     */
    public boolean estaLibre(String mesaId) {
        return !estaOcupada(mesaId);
    }

    /**
     * Devuelve la cuenta activa de una mesa.
     */
    public Optional<Cuenta> obtenerCuentaActivaDeMesa(String mesaId) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe"));

        return cuentaRepository.findAll().stream()
                .filter(cuenta -> cuenta.mesas() != null && cuenta.mesas().stream()
                        .anyMatch(m -> m.id().equals(mesa.id())))
                .filter(cuenta -> !cuenta.estaPagada())
                .findFirst();
    }

    /**
     * Ocupar mesa = crear una cuenta nueva asociada a esa mesa.
     */
    public Cuenta ocuparMesa(String mesaId) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe"));

        if (estaOcupada(mesaId)) {
            throw new IllegalArgumentException("La mesa ya está ocupada");
        }

        Cuenta nuevaCuenta = new Cuenta(
                null,
                List.of(mesa),
                false,
                Optional.empty(),
                Instant.now(),
                Optional.empty()
        );

        return cuentaRepository.save(nuevaCuenta);
    }

    /**
     * Liberar mesa no toca Mesa directamente.
     * Solo comprueba que no quede cuenta activa pendiente.
     * Si existe cuenta activa no pagada, no se puede liberar.
     */
    public void liberarMesa(String mesaId) {
        Optional<Cuenta> cuentaActiva = obtenerCuentaActivaDeMesa(mesaId);

        if (cuentaActiva.isEmpty()) {
            return;
        }

        if (!cuentaActiva.get().estaPagada()) {
            throw new IllegalArgumentException("No se puede liberar la mesa porque su cuenta sigue activa");
        }
    }

    /**
     * Pedidos activos = pedidos asociados a la cuenta activa de la mesa.
     */
    public List<Pedido> obtenerPedidosActivosDeMesa(String mesaId) {
        Optional<Cuenta> cuentaActiva = obtenerCuentaActivaDeMesa(mesaId);

        if (cuentaActiva.isEmpty()) {
            return List.of();
        }

        Cuenta cuenta = cuentaActiva.get();

        return pedidoRepository.findAll().stream()
                .filter(pedido -> pedido.cuenta() != null)
                .filter(pedido -> pedido.cuenta().id() != null)
                .filter(pedido -> pedido.cuenta().id().equals(cuenta.id()))
                .toList();
    }

    /**
     * Órdenes activas = todas las órdenes de todos los pedidos activos de la mesa.
     */
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

    /**
     * Devuelve la mesa validando que exista.
     */
    public Mesa obtenerMesa(String mesaId) {
        return mesaRepository.findById(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe"));
    }
}