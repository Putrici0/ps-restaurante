package service.application;

import dto.CuentaPagadaResumenResponse;
import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.Pedido;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        List<Cuenta> cuentasPagadas = cuentaRepository.findByEstaPagada(true).stream()
                .filter(cuenta -> coincideFecha(cuenta, fecha))
                .toList();

        Map<String, BigDecimal> totalesPorCuenta = calcularTotalesPorCuenta(cuentasPagadas);

        return cuentasPagadas.stream()
                .map(cuenta -> mapearResumen(cuenta, totalesPorCuenta.getOrDefault(cuenta.id(), BigDecimal.ZERO)))
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

    private CuentaPagadaResumenResponse mapearResumen(Cuenta cuenta, BigDecimal total) {
        Instant fechaHora = cuenta.fechaPago().orElse(cuenta.fechaCreacion());
        String mesa = obtenerMesaPrincipal(cuenta);

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

        return mesas.stream()
                .map(Mesa::id)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.joining(", "));
    }

    private Map<String, BigDecimal> calcularTotalesPorCuenta(List<Cuenta> cuentas) {
        if (cuentas.isEmpty()) {
            return Map.of();
        }

        Map<String, String> cuentaIdsValidos = cuentas.stream()
                .map(Cuenta::id)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toMap(id -> id, id -> id, (a, b) -> a));

        if (cuentaIdsValidos.isEmpty()) {
            return Map.of();
        }

        Map<String, String> pedidoIdACuentaId = pedidoRepository.findByCuentaIds(List.copyOf(cuentaIdsValidos.keySet())).stream()
                .filter(pedido -> pedido.id() != null && !pedido.id().isBlank())
                .filter(pedido -> pedido.cuenta() != null && pedido.cuenta().id() != null && !pedido.cuenta().id().isBlank())
                .filter(pedido -> cuentaIdsValidos.containsKey(pedido.cuenta().id()))
                .collect(Collectors.toMap(Pedido::id, pedido -> pedido.cuenta().id(), (a, b) -> a));

        List<String> pedidosIds = pedidoIdACuentaId.keySet().stream().toList();
        if (pedidosIds.isEmpty()) {
            return Map.of();
        }

        return ordenRepository.findByPedidosIds(pedidosIds).stream()
                .filter(orden -> orden.pedido() != null && orden.pedido().id() != null)
                .collect(Collectors.groupingBy(
                        orden -> pedidoIdACuentaId.get(orden.pedido().id()),
                        Collectors.mapping(Orden::precio, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
    }
}
