package service.application;

import model.*;
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
        return pedidoRepository.findByCuenta(cuenta);
    }

    public List<Orden> obtenerOrdenesDeCuenta(String cuentaId) {
        List<Pedido> pedidos = obtenerPedidosDeCuenta(cuentaId);

        return pedidos.stream()
                .flatMap(pedido -> ordenRepository.findByPedido(pedido).stream())
                .filter(orden -> orden.ordenEstado() != OrdenEstado.Cancelado)
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

    public Cuenta pagarCuentaCompleta(String cuentaId, MetodoPago metodoPago) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);

        if (cuenta.payed()) {
            throw new IllegalArgumentException("La cuenta ya está pagada");
        }

        if (metodoPago == null) {
            throw new IllegalArgumentException("El método de pago es obligatorio");
        }

        Cuenta actualizada = new Cuenta(
                cuenta.id(),
                cuenta.mesas(),
                true,
                cuenta.reserva(),
                cuenta.fechaCreacion(),
                Optional.of(Instant.now()),
                "",
                Optional.of(metodoPago)
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
                "",
                cuenta.metodoPago()
        );

        return cuentaRepository.update(cuenta.id(), actualizada);
    }

    public void eliminarOrdenDeCuenta(String cuentaId, String ordenId) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);

        if (cuenta.payed()) {
            throw new IllegalArgumentException("No se pueden eliminar platos de una cuenta ya pagada");
        }

        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("La orden no existe"));

        if (orden.pedido() == null || orden.pedido().id() == null) {
            throw new IllegalArgumentException("La orden no tiene pedido asociado");
        }

        Pedido pedido = pedidoRepository.findById(orden.pedido().id())
                .orElseThrow(() -> new IllegalArgumentException("El pedido asociado a la orden no existe"));

        if (pedido.cuenta() == null || pedido.cuenta().id() == null || !pedido.cuenta().id().equals(cuentaId)) {
            throw new IllegalArgumentException("La orden no pertenece a esa cuenta");
        }

        Orden ordenCancelada = new Orden(
                orden.id(),
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                OrdenEstado.Cancelado,
                orden.fecha(),
                orden.detalles()
        );

        ordenRepository.update(orden.id(), ordenCancelada);

        List<Orden> ordenesActivasDelPedido = ordenRepository.findByPedido(pedido).stream()
                .filter(o -> o.ordenEstado() != OrdenEstado.Cancelado)
                .toList();

        boolean todasListasOEntregadas = !ordenesActivasDelPedido.isEmpty()
                && ordenesActivasDelPedido.stream().allMatch(o ->
                o.ordenEstado() == OrdenEstado.Listo || o.ordenEstado() == OrdenEstado.Entregado
        );

        Pedido pedidoActualizado = new Pedido(
                pedido.id(),
                pedido.cuenta(),
                todasListasOEntregadas ? PedidoEstado.Listo : PedidoEstado.Pendiente,
                pedido.fechaPedido()
        );

        pedidoRepository.update(pedido.id(), pedidoActualizado);
    }
}