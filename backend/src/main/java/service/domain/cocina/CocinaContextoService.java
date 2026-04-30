package service.domain.cocina;

import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.Pedido;
import service.domain.cocina.CocinaContextoCuenta;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CocinaContextoService {

    public Map<String, CocinaContextoCuenta> construirContexto(List<Orden> ordenes) {
        List<Orden> seguras = ordenes == null ? List.of() : ordenes;

        return seguras.stream()
                .collect(Collectors.groupingBy(this::claveCuentaDeOrden))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new CocinaContextoCuenta(
                                entry.getKey(),
                                cuentaDeOrden(entry.getValue().isEmpty() ? null : entry.getValue().getFirst()),
                                entry.getValue()
                        )
                ));
    }

    public String claveCuentaDeOrden(Orden orden) {
        Cuenta cuenta = cuentaDeOrden(orden);

        if (cuenta != null && cuenta.id() != null && !cuenta.id().isBlank()) {
            return "cuenta:" + cuenta.id();
        }

        if (cuenta != null && cuenta.mesas() != null && !cuenta.mesas().isEmpty()) {
            return "mesa:" + cuenta.mesas().stream()
                    .map(Mesa::id)
                    .sorted()
                    .collect(Collectors.joining("-"));
        }

        Pedido pedido = orden != null ? orden.pedido() : null;

        if (pedido != null && pedido.id() != null && !pedido.id().isBlank()) {
            return "pedido:" + pedido.id();
        }

        return "orden:" + (orden != null ? orden.id() : "sin-id");
    }

    private Cuenta cuentaDeOrden(Orden orden) {
        if (orden == null || orden.pedido() == null) return null;
        return orden.pedido().cuenta();
    }
}