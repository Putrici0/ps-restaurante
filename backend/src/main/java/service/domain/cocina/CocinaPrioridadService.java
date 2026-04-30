package service.domain.cocina;

import dto.cocina.CocinaPrioridad;
import model.Categoria;
import model.Orden;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CocinaPrioridadService {

    private static final int MAX_PUNTOS_ESPERA = 120;

    public List<Orden> ordenarPorPrioridad(
            List<Orden> ordenes,
            Map<String, CocinaContextoCuenta> contextoPorCuenta,
            Instant ahora
    ) {
        return ordenes.stream()
                .sorted(
                        Comparator
                                .comparingInt((Orden orden) -> calcularPrioridad(orden, contextoPorCuenta, ahora).total())
                                .reversed()
                                .thenComparing(Orden::fecha, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .toList();
    }

    public CocinaPrioridad calcularPrioridad(
            Orden orden,
            Map<String, CocinaContextoCuenta> contextoPorCuenta,
            Instant ahora
    ) {
        CocinaContextoService contextoService = new CocinaContextoService();
        String clave = contextoService.claveCuentaDeOrden(orden);
        CocinaContextoCuenta contexto = contextoPorCuenta.get(clave);

        List<String> motivos = new ArrayList<>();

        int categoria = puntosCategoria(orden, motivos);
        int espera = puntosEspera(orden, ahora, motivos);
        int mesa = puntosMesa(contexto, motivos);
        int flujo = puntosFlujo(orden, contexto, motivos);
        int tiempoEstimado = puntosTiempoEstimado(orden, motivos);
        int sincronizacion = puntosSincronizacion(orden, contexto, motivos);
        int urgencia = puntosUrgencia(orden, motivos);

        int total = categoria + espera + mesa + flujo + tiempoEstimado + sincronizacion + urgencia;

        return new CocinaPrioridad(
                total,
                categoria,
                espera,
                mesa,
                flujo,
                tiempoEstimado,
                sincronizacion,
                urgencia,
                motivos
        );
    }

    private int puntosCategoria(Orden orden, List<String> motivos) {
        if (orden == null || orden.plato() == null || orden.plato().categoria() == null) return 0;

        Categoria categoria = orden.plato().categoria();

        return switch (categoria) {
            case Entrante -> {
                motivos.add("+30 por ser entrante");
                yield 30;
            }
            case Principal -> {
                motivos.add("+18 por ser principal");
                yield 18;
            }
            case Postre -> {
                motivos.add("+5 por ser postre");
                yield 5;
            }
            case Bebida -> 0;
        };
    }

    private int puntosEspera(Orden orden, Instant ahora, List<String> motivos) {
        if (orden == null || orden.fecha() == null || ahora == null) return 0;

        long minutos = Math.max(0, Duration.between(orden.fecha(), ahora).toMinutes());
        int puntos = (int) Math.min(minutos * 3, MAX_PUNTOS_ESPERA);

        if (puntos > 0) {
            motivos.add("+" + puntos + " por " + minutos + " min de espera");
        }

        return puntos;
    }

    private int puntosMesa(CocinaContextoCuenta contexto, List<String> motivos) {
        if (contexto == null) return 0;

        if (contexto.mesaSinServir()) {
            motivos.add("+25 porque la mesa aún no recibió ningún plato");
            return 25;
        }

        return 0;
    }

    private int puntosFlujo(Orden orden, CocinaContextoCuenta contexto, List<String> motivos) {
        if (orden == null || orden.plato() == null || contexto == null) return 0;

        Categoria categoria = orden.plato().categoria();

        if (categoria == Categoria.Entrante && contexto.quedanEntrantesPendientes()) {
            motivos.add("+8 por mantener fase de entrantes");
            return 8;
        }

        if (categoria == Categoria.Principal && contexto.quedanEntrantesPendientes()) {
            motivos.add("-12 porque aún quedan entrantes pendientes");
            return -12;
        }

        if (categoria == Categoria.Principal && !contexto.quedanEntrantesPendientes()) {
            motivos.add("+6 porque ya puede comenzar fase de principales");
            return 6;
        }

        if (categoria == Categoria.Postre && contexto.quedanPrincipalesPendientes()) {
            motivos.add("-20 porque aún quedan principales pendientes");
            return -20;
        }

        return 0;
    }

    private int puntosTiempoEstimado(Orden orden, List<String> motivos) {
        int minutosEstimados = tiempoEstimadoMinutos(orden);

        int puntos;
        if (minutosEstimados <= 5) {
            puntos = 10;
        } else if (minutosEstimados <= 10) {
            puntos = 6;
        } else if (minutosEstimados <= 15) {
            puntos = 2;
        } else {
            puntos = 0;
        }

        if (puntos > 0) {
            motivos.add("+" + puntos + " por preparación estimada rápida (" + minutosEstimados + " min)");
        }

        return puntos;
    }

    private int puntosSincronizacion(Orden orden, CocinaContextoCuenta contexto, List<String> motivos) {
        if (orden == null || orden.plato() == null || contexto == null) return 0;

        long activasMismaFase = contexto.numeroPlatosActivosMismaCategoria(orden);
        long otrasPendientesMismaFase = contexto.numeroPlatosPendientesMismaCategoria(orden);
        long listasMismaFase = contexto.numeroPlatosListosMismaCategoria(orden);

        int puntos = 0;

        if (otrasPendientesMismaFase > 0) {
            puntos += 10;
            motivos.add("+10 por sincronizar platos de la misma fase en la mesa");
        }

        if (activasMismaFase > 1 && otrasPendientesMismaFase == 0) {
            puntos += 15;
            motivos.add("+15 por ayudar a completar la fase de la mesa");
        }

        if (listasMismaFase > 0 && orden.ordenEstado().name().equals("Pendiente")) {
            puntos += 10;
            motivos.add("+10 porque ya hay platos de la misma fase listos");
        }

        return puntos;
    }

    private int puntosUrgencia(Orden orden, List<String> motivos) {
        String detalles = orden != null && orden.detalles() != null
                ? orden.detalles().toLowerCase()
                : "";

        if (detalles.contains("urgente") || detalles.contains("prioridad")) {
            motivos.add("+50 por marca de urgencia en detalles");
            return 50;
        }

        return 0;
    }

    private int tiempoEstimadoMinutos(Orden orden) {
        if (orden == null || orden.plato() == null || orden.plato().categoria() == null) return 12;

        return switch (orden.plato().categoria()) {
            case Entrante -> 8;
            case Principal -> 15;
            case Postre -> 6;
            case Bebida -> 4;
        };
    }
}