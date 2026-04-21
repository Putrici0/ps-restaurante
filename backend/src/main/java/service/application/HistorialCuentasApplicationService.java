package service.application;

import dto.CuentaPagadaResumenResponse;
import model.Cuenta;
import model.Mesa;
import model.Orden;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

public class HistorialCuentasApplicationService {
    private final CuentaRepository cuentaRepository;
    private final PedidoRepository pedidoRepository;
    private final OrdenRepository ordenRepository;

    public HistorialCuentasApplicationService(
            CuentaRepository cuentaRepository,
            PedidoRepository pedidoRepository,
            OrdenRepository ordenRepository
    ) {
        this.cuentaRepository = cuentaRepository;
        this.pedidoRepository = pedidoRepository;
        this.ordenRepository = ordenRepository;
    }

    public List<CuentaPagadaResumenResponse> obtenerCuentasPagadas(LocalDate fecha) {
        return cuentaRepository.findByEstaPagada(true).stream()
                .filter(cuenta -> coincideFecha(cuenta, fecha))
                .map(this::mapearResumen)
                .sorted(Comparator.comparing(CuentaPagadaResumenResponse::fechaHora).reversed())
                .toList();
    }

    private boolean coincideFecha(Cuenta cuenta, LocalDate fecha) {
        if (fecha == null) {
            return true;
        }

        Instant fechaBase = cuenta.fechaPago().orElse(cuenta.fechaCreacion());
        LocalDate fechaCuenta = fechaBase.atZone(ZoneId.systemDefault()).toLocalDate();
        return fechaCuenta.equals(fecha);
    }

    private CuentaPagadaResumenResponse mapearResumen(Cuenta cuenta) {
        Instant fechaHora = cuenta.fechaPago().orElse(cuenta.fechaCreacion());
        String mesa = obtenerMesaPrincipal(cuenta);
        BigDecimal total = calcularTotalCuenta(cuenta);

        return new CuentaPagadaResumenResponse(
                cuenta.id(),
                fechaHora,
                mesa,
                total,
                cuenta.metodoPago().orElse(null)
        );
    }

    private String obtenerMesaPrincipal(Cuenta cuenta) {
        List<Mesa> mesas = cuenta.mesas();
        if (mesas == null || mesas.isEmpty()) {
            return "-";
        }
        return mesas.get(0).id();
    }

    private BigDecimal calcularTotalCuenta(Cuenta cuenta) {
        return pedidoRepository.findByCuenta(cuenta).stream()
                .flatMap(pedido -> ordenRepository.findByPedido(pedido).stream())
                .map(Orden::precio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}