package model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record Mesa(String id, int capacidad, List<String> mesasUnidas) {

    public Mesa {
        mesasUnidas = normalizarMesasUnidas(id, mesasUnidas);
    }

    public Mesa(String id, int capacidad) {
        this(id, capacidad, List.of(id));
    }

    private static List<String> normalizarMesasUnidas(String id, List<String> mesasUnidas) {
        return java.util.stream.Stream.concat(
                        id != null && !id.isBlank() ? java.util.stream.Stream.of(id) : java.util.stream.Stream.empty(),
                        mesasUnidas == null ? java.util.stream.Stream.empty() : mesasUnidas.stream()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(comparadorIdsMesa())
                .toList();
    }

    private static Comparator<String> comparadorIdsMesa() {
        return Comparator
                .comparingInt(Mesa::parseMesaId)
                .thenComparing(Comparator.naturalOrder());
    }

    private static int parseMesaId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}