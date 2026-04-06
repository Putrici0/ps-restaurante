package service.application;

import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.Pedido;
import repository.interfaces.CuentaRepository;
import repository.interfaces.MesaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class MesaApplicationService {

    private final MesaRepository mesaRepository;
    private final CuentaRepository cuentaRepository;
    private final PedidoRepository pedidoRepository;
    private final OrdenRepository ordenRepository;

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
        // Optimización: Una sola consulta filtrada en Google Cloud
        return cuentaRepository.findActiveByMesa(mesa);
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
                Optional.empty()
        );

        return cuentaRepository.save(nuevaCuenta);
    }

    public void liberarMesa(String mesaId) {
        if (estaOcupada(mesaId)) {
            throw new IllegalArgumentException("No se puede liberar la mesa porque su cuenta sigue activa");
        }
    }

    public List<Pedido> obtenerPedidosActivosDeMesa(String mesaId) {
        return obtenerCuentaActivaDeMesa(mesaId)
                .map(pedidoRepository::findByCuenta) // Optimización: Búsqueda directa por ID de cuenta
                .orElse(List.of());
    }

    public List<Orden> obtenerOrdenesActivasDeMesa(String mesaId) {
        List<Pedido> pedidos = obtenerPedidosActivosDeMesa(mesaId);
        // Optimización: flatMap para evitar bucles pesados y múltiples lecturas innecesarias
        return pedidos.stream()
                .flatMap(pedido -> ordenRepository.findByPedido(pedido).stream())
                .toList();
    }
}
