package util;

import model.Mesa;
import repository.firestore.FirestoreMesaRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class MesaSeeder {

    private MesaSeeder() {
    }

    public static void seed(FirestoreMesaRepository mesaRepository) {
        List<Mesa> mesasBase = List.of(
                new Mesa("1", 2),
                new Mesa("2", 4),
                new Mesa("3", 2),
                new Mesa("4", 2),
                new Mesa("5", 4),
                new Mesa("6", 4),
                new Mesa("7", 2),
                new Mesa("8", 4),
                new Mesa("9", 4),
                new Mesa("10", 2),
                new Mesa("11", 4),
                new Mesa("12", 2),
                new Mesa("13", 4),
                new Mesa("14", 4),
                new Mesa("15", 4),
                new Mesa("16", 2),
                new Mesa("17", 2),
                new Mesa("18", 4),
                new Mesa("19", 4),
                new Mesa("20", 4)
        );

        Set<String> idsExistentes = mesaRepository.findAll().stream()
                .map(Mesa::id)
                .collect(Collectors.toSet());

        for (Mesa mesa : mesasBase) {
            if (!idsExistentes.contains(mesa.id())) {
                mesaRepository.save(mesa);
            }
        }
    }
}