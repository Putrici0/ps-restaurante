package service.domain.cocina;

import model.Categoria;
import model.Cuenta;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CocinaContextoCuenta {

    private final String claveCuenta;
    private final Cuenta cuenta;
    private final List<Orden> ordenes;

    public CocinaContextoCuenta(String claveCuenta, Cuenta cuenta, List<Orden> ordenes) {
        this.claveCuenta = claveCuenta;
        this.cuenta = cuenta;
        this.ordenes = ordenes == null ? List.of() : List.copyOf(ordenes);
    }

    public String claveCuenta() {
        return claveCuenta;
    }

    public Cuenta cuenta() {
        return cuenta;
    }

    public List<Orden> ordenes() {
        return ordenes;
    }

    public boolean mesaSinServir() {
        return ordenes.stream()
                .noneMatch(o -> o.ordenEstado() == OrdenEstado.Entregado);
    }

    public boolean quedanEntrantesPendientes() {
        return ordenes.stream()
                .anyMatch(o ->
                        o.plato() != null
                                && o.plato().categoria() == Categoria.Entrante
                                && esOrdenNoEntregadaNiCancelada(o)
                );
    }

    public boolean quedanPrincipalesPendientes() {
        return ordenes.stream()
                .anyMatch(o ->
                        o.plato() != null
                                && o.plato().categoria() == Categoria.Principal
                                && esOrdenNoEntregadaNiCancelada(o)
                );
    }

    public long numeroPlatosPendientesMismaCategoria(Orden orden) {
        if (orden == null || orden.plato() == null) return 0;

        Categoria categoria = orden.plato().categoria();

        return ordenes.stream()
                .filter(o -> o.id() != null && !Objects.equals(o.id(), orden.id()))
                .filter(o -> o.plato() != null && o.plato().categoria() == categoria)
                .filter(CocinaContextoCuenta::esOrdenActivaDeCocina)
                .count();
    }

    public long numeroPlatosActivosMismaCategoria(Orden orden) {
        if (orden == null || orden.plato() == null) return 0;

        Categoria categoria = orden.plato().categoria();

        return ordenes.stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == categoria)
                .filter(CocinaContextoCuenta::esOrdenActivaDeCocina)
                .count();
    }

    public long numeroPlatosListosMismaCategoria(Orden orden) {
        if (orden == null || orden.plato() == null) return 0;

        Categoria categoria = orden.plato().categoria();

        return ordenes.stream()
                .filter(o -> o.plato() != null && o.plato().categoria() == categoria)
                .filter(o -> o.ordenEstado() == OrdenEstado.Listo)
                .count();
    }

    public Instant fechaPrimerPedido() {
        return ordenes.stream()
                .map(Orden::pedido)
                .filter(Objects::nonNull)
                .map(Pedido::fechaPedido)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean esOrdenNoEntregadaNiCancelada(Orden orden) {
        return orden.ordenEstado() != OrdenEstado.Entregado
                && orden.ordenEstado() != OrdenEstado.Cancelado;
    }

    public static boolean esOrdenActivaDeCocina(Orden orden) {
        return orden != null
                && orden.plato() != null
                && orden.plato().categoria() != Categoria.Bebida
                && orden.ordenEstado() != OrdenEstado.Entregado
                && orden.ordenEstado() != OrdenEstado.Cancelado;
    }
}