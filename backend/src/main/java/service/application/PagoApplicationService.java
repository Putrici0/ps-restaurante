package service.application;

import model.Cuenta;
import model.Orden;
import model.Pedido;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PagoApplicationService {

    private final CuentaRepository cuentaRepository;
    private final PedidoRepository pedidoRepository;
    private final OrdenRepository ordenRepository;

    public PagoApplicationService(
            CuentaRepository cuentaRepository,
            PedidoRepository pedidoRepository,
            OrdenRepository ordenRepository
    ) {
        this.cuentaRepository = cuentaRepository;
        this.pedidoRepository = pedidoRepository;
        this.ordenRepository = ordenRepository;
    }

    public Cuenta obtenerCuentaPorId(String cuentaId) {
        return cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta no existe"));
    }

    public List<Pedido> obtenerPedidosDeCuenta(String cuentaId) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);

        return pedidoRepository.findAll().stream()
                .filter(pedido -> pedido.cuenta() != null)
                .filter(pedido -> pedido.cuenta().id() != null)
                .filter(pedido -> pedido.cuenta().id().equals(cuenta.id()))
                .toList();
    }

    public List<Orden> obtenerOrdenesDeCuenta(String cuentaId) {
        List<Pedido> pedidos = obtenerPedidosDeCuenta(cuentaId);

        return ordenRepository.findAll().stream()
                .filter(orden -> orden.pedido() != null)
                .filter(orden -> orden.pedido().id() != null)
                .filter(orden -> pedidos.stream().anyMatch(p -> p.id().equals(orden.pedido().id())))
                .toList();
    }

    public BigDecimal calcularTotalCuenta(String cuentaId) {
        return obtenerOrdenesDeCuenta(cuentaId).stream()
                .map(Orden::precio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calcularPendienteCuenta(String cuentaId) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);

        if (cuenta.payed()) {
            return BigDecimal.ZERO;
        }

        return calcularTotalCuenta(cuentaId);
    }

    public boolean cuentaEstaSaldada(String cuentaId) {
        return calcularPendienteCuenta(cuentaId).compareTo(BigDecimal.ZERO) == 0;
    }

    public Cuenta pagarCuentaCompleta(String cuentaId) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);

        if (cuenta.payed()) {
            throw new IllegalArgumentException("La cuenta ya está pagada");
        }

        Cuenta actualizada = new Cuenta(
                cuenta.id(),
                cuenta.mesas(),
                true,
                cuenta.reserva(),
                cuenta.fechaCreacion(),
                Optional.of(Instant.now()),
                ""
        );

        return cuentaRepository.update(cuenta.id(), actualizada);
    }

    public Cuenta cerrarCuentaSiProcede(String cuentaId) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);

        if (!cuentaEstaSaldada(cuentaId)) {
            throw new IllegalArgumentException("La cuenta todavía tiene saldo pendiente");
        }

        if (cuenta.payed()) {
            return cuenta;
        }

        Cuenta actualizada = new Cuenta(
                cuenta.id(),
                cuenta.mesas(),
                true,
                cuenta.reserva(),
                cuenta.fechaCreacion(),
                Optional.of(Instant.now()),
                ""
        );

        return cuentaRepository.update(cuenta.id(), actualizada);
    }
}