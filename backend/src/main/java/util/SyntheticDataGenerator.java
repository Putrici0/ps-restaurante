package util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import model.*;

import java.io.File;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Generador Maestro de Datos Sintéticos (v4 Pro).
 * Simula flujos de trabajo de restaurante con alta precisión estadística.
 */
public class SyntheticDataGenerator {

    private final List<Plato> platos;
    private final List<UserSim> staff;
    private final Random random = new Random();
    private final ObjectMapper objectMapper;

    private record UserSim(String uid, String nombre, String apellido, String correo, String rol, String creadoEn,
                           double skill) {
    }

    public record SimulationExport(
            List<Cuenta> cuentas,
            List<Pedido> pedidos,
            List<Orden> ordenes,
            List<Map<String, Object>> notificaciones
    ) {
    }

    public SyntheticDataGenerator(List<Plato> platosActuales) {
        this.platos = platosActuales;
        this.staff = List.of(
                new UserSim("uid-pepelu", "Pepelu", "García", "pepelu@rest.com", "camarero", "2026-01-01T10:00:00Z", 1.2),
                new UserSim("uid-maria", "Maria", "Lopez", "maria@rest.com", "camarero", "2026-01-01T10:00:00Z", 1.0),
                new UserSim("uid-juan", "Juan", "Pérez", "juan@rest.com", "camarero", "2026-01-01T10:00:00Z", 0.8)
        );
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void generarYExportar(int diasAtras, String outputPath) {
        List<Cuenta> todasCuentas = new ArrayList<>();
        List<Pedido> todosPedidos = new ArrayList<>();
        List<Orden> todasOrdenes = new ArrayList<>();
        List<Map<String, Object>> todasNotificaciones = new ArrayList<>();

        LocalDate hoy = LocalDate.now();
        LocalDate inicio = hoy.minusDays(diasAtras);

        for (LocalDate date = inicio; date.isBefore(hoy); date = date.plusDays(1)) {
            simularDia(date, todasCuentas, todosPedidos, todasOrdenes, todasNotificaciones);
        }

        try {
            SimulationExport export = new SimulationExport(todasCuentas, todosPedidos, todasOrdenes, todasNotificaciones);
            objectMapper.writeValue(new File(outputPath), export);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void simularDia(LocalDate date, List<Cuenta> cuentas, List<Pedido> pedidos, List<Orden> ordenes, List<Map<String, Object>> notif) {
        double dayFactor = switch (date.getDayOfWeek()) {
            case FRIDAY, SUNDAY -> 1.5;
            case SATURDAY -> 2.2;
            default -> 1.0;
        };
        int numCuentas = (int) (65 * dayFactor * (0.85 + random.nextDouble() * 0.3));

        for (int i = 0; i < numCuentas; i++) {
            simularServicio(date, cuentas, pedidos, ordenes, notif);
        }
    }

    private void simularServicio(LocalDate date, List<Cuenta> cuentas, List<Pedido> pedidos, List<Orden> ordenes, List<Map<String, Object>> notif) {
        double r = random.nextDouble();
        LocalTime hLlegada;
        if (r < 0.45) hLlegada = generateGaussianTime(LocalTime.of(12, 0), LocalTime.of(16, 30), LocalTime.of(14, 15));
        else if (r < 0.90)
            hLlegada = generateGaussianTime(LocalTime.of(19, 30), LocalTime.of(23, 59), LocalTime.of(21, 45));
        else hLlegada = LocalTime.of(16 + random.nextInt(3), random.nextInt(60));

        Instant tInicio = date.atTime(hLlegada).atZone(ZoneId.systemDefault()).toInstant();
        int comensales = 1 + random.nextInt(6);
        List<Mesa> mesas = new ArrayList<>();
        mesas.add(new Mesa(String.valueOf(1 + random.nextInt(20)), 4));
        if (comensales > 4) mesas.add(new Mesa(String.valueOf(1 + random.nextInt(20)), 4));

        MetodoPago metodo = random.nextDouble() > 0.25 ? MetodoPago.TARJETA : MetodoPago.EFECTIVO;
        Cuenta cuenta = new Cuenta(UUID.randomUUID().toString(), mesas, true, Optional.empty(), tInicio, Optional.empty(), "", Optional.of(metodo));
        UserSim cam = staff.get(random.nextInt(staff.size()));

        // SIMULACIÓN DE CICLO COMPLETO CON COBERTURA DE CATÁLOGO
        Instant tActual = tInicio.plus(Duration.ofMinutes(2 + random.nextInt(3)));
        Instant tMaxTotal = tActual;

        // 1. Ronda de Bebidas
        if (random.nextDouble() < 0.95) {
            Pedido pB = new Pedido(UUID.randomUUID().toString(), cuenta, PedidoEstado.Listo, tActual);
            pedidos.add(pB);
            tMaxTotal = simularGrupoItems(pB, comensales, "Bebida", tActual, cam, ordenes, notif, metodo);
        }

        // 2. Ronda de Entrantes
        tActual = tActual.plus(Duration.ofMinutes(5));
        if (random.nextDouble() < 0.60) {
            Pedido pE = new Pedido(UUID.randomUUID().toString(), cuenta, PedidoEstado.Listo, tActual);
            pedidos.add(pE);
            Instant tE = simularGrupoItems(pE, Math.max(1, comensales / 2), "Entrante", tActual, cam, ordenes, notif, metodo);
            if (tE.isAfter(tMaxTotal)) tMaxTotal = tE;
        }

        // 3. Ronda de Principales
        tActual = tActual.plus(Duration.ofMinutes(10));
        Pedido pP = new Pedido(UUID.randomUUID().toString(), cuenta, PedidoEstado.Listo, tActual);
        pedidos.add(pP);
        Instant tP = simularGrupoItems(pP, comensales, "Principal", tActual, cam, ordenes, notif, metodo);
        if (tP.isAfter(tMaxTotal)) tMaxTotal = tP;

        // 4. Ronda de Postres
        tActual = tMaxTotal.plus(Duration.ofMinutes(2));
        if (random.nextDouble() < 0.40) {
            Pedido pPo = new Pedido(UUID.randomUUID().toString(), cuenta, PedidoEstado.Listo, tActual);
            pedidos.add(pPo);
            Instant tPo = simularGrupoItems(pPo, Math.max(1, comensales / 2), "Postre", tActual, cam, ordenes, notif, metodo);
            if (tPo.isAfter(tMaxTotal)) tMaxTotal = tPo;
        }

        double sobremesa = (random.nextDouble() > 0.8) ? 40 + random.nextInt(40) : 15 + random.nextInt(15);
        Instant tPago = tMaxTotal.plus(Duration.ofMinutes((long) sobremesa));
        cuentas.add(new Cuenta(cuenta.id(), mesas, true, Optional.empty(), tInicio, Optional.of(tPago), "", Optional.of(metodo)));
    }

    private Instant simularGrupoItems(Pedido ped, int cantidad, String categoria, Instant tPed, UserSim cam, List<Orden> ords, List<Map<String, Object>> notif, MetodoPago metodo) {
        List<Plato> opciones = platos.stream().filter(p -> p.categoria().name().equalsIgnoreCase(categoria)).toList();
        if (opciones.isEmpty()) return tPed;
        Instant tMax = tPed;
        for (int i = 0; i < cantidad; i++) {
            Plato plato = opciones.get(random.nextInt(opciones.size()));
            int baseMin = switch (categoria) {
                case "Entrante" -> 10;
                case "Principal" -> 18;
                case "Postre" -> 8;
                case "Bebida" -> 4;
                default -> 12;
            };
            int cookMins = (int) Math.max(1, baseMin + (random.nextGaussian() * (baseMin * 0.2)));
            Instant tListo = tPed.plus(Duration.ofMinutes(cookMins));
            if (tListo.isAfter(tMax)) tMax = tListo;
            BigDecimal precioCents = plato.precio().multiply(new BigDecimal("100"));
            Orden o = new Orden(UUID.randomUUID().toString(), ped, plato, precioCents, OrdenEstado.Entregado, tPed, "", false, true, Optional.of(tListo), Optional.of(metodo));
            ords.add(o);
            Map<String, Object> n = new HashMap<>();
            n.put("id", UUID.randomUUID().toString());
            n.put("tipo", "Recoger");
            n.put("fecha", tListo.toString());
            n.put("ordenId", o.id());
            n.put("nombreItem", plato.nombre());
            n.put("categoriaItem", categoria);
            n.put("camareroUid", cam.uid());
            n.put("camareroNombre", cam.nombre() + " " + cam.apellido());
            int reaction = (int) Math.max(1, (2 + random.nextInt(3)) * (2.0 - cam.skill));
            n.put("fechaEnCurso", tListo.plus(Duration.ofMinutes(reaction)).toString());
            notif.add(n);
        }
        return tMax;
    }

    private Plato buscarPlato(String cat) {
        return platos.stream().filter(p -> p.categoria().name().equalsIgnoreCase(cat)).findAny().orElse(null);
    }

    private LocalTime generateGaussianTime(LocalTime start, LocalTime end, LocalTime peak) {
        long s = start.toSecondOfDay(), e = end.toSecondOfDay(), p = peak.toSecondOfDay();
        double stdDev = (e - s) / 6.0;
        long val = (long) (random.nextGaussian() * stdDev + p);
        return LocalTime.ofSecondOfDay(Math.max(s, Math.min(e - 1, val)));
    }
}
