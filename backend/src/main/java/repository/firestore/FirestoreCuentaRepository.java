package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Cuenta;
import model.Mesa;
import model.Reserva;
import repository.interfaces.CuentaRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class FirestoreCuentaRepository extends AbstractFirestoreRepository<Cuenta> implements CuentaRepository {

    public FirestoreCuentaRepository(Firestore db) {
        super(db, "cuentas");
    }

    @Override
    protected Cuenta mapToEntity(String id, Map<String, Object> data) {
        List<Map<String, Object>> mesasData = (List<Map<String, Object>>) data.getOrDefault("mesas", List.of());
        List<Mesa> mesas = mesasData.stream()
                .map(m -> new Mesa((String) m.get("id"), ((Long) m.getOrDefault("capacidad", 0L)).intValue()))
                .collect(Collectors.toList());

        Map<String, Object> resData = (Map<String, Object>) data.get("reserva");
        Optional<Reserva> reserva = Optional.empty();
        if (resData != null) {
            reserva = Optional.of(new Reserva(
                    (String) resData.get("id"),
                    (String) resData.get("nombre"),
                    toInstant(resData.get("fecha")),
                    ((Long) resData.getOrDefault("capacidad", 0L)).intValue(),
                    toInstant(resData.get("fechaCreacion"))
            ));
        }

        return new Cuenta(
                id,
                mesas,
                get(data, "estaPagada", false),
                reserva,
                toInstant(data.get("fechaCreacion")),
                Optional.ofNullable(toInstant(data.get("fechaPago")))
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Cuenta cuenta) {
        Map<String, Object> map = new HashMap<>();
        
        List<Map<String, Object>> mesasMap = cuenta.mesas().stream()
                .map(m -> {
                    Map<String, Object> mData = new HashMap<>();
                    mData.put("id", m.id());
                    mData.put("capacidad", m.capacidad());
                    return mData;
                }).collect(Collectors.toList());
        
        map.put("mesas", mesasMap);
        map.put("estaPagada", cuenta.estaPagada());
        map.put("fechaCreacion", toTimestamp(cuenta.fechaCreacion()));
        cuenta.fechaPago().ifPresent(f -> map.put("fechaPago", toTimestamp(f)));

        cuenta.reserva().ifPresent(r -> {
            Map<String, Object> rMap = new HashMap<>();
            rMap.put("id", r.id());
            rMap.put("nombre", r.nombre());
            rMap.put("fecha", toTimestamp(r.fecha()));
            rMap.put("capacidad", r.capacidad());
            rMap.put("fechaCreacion", toTimestamp(r.fechaCreacion()));
            map.put("reserva", rMap);
        });

        return map;
    }

    @Override
    protected String getEntityId(Cuenta cuenta) {
        return cuenta.id();
    }

    @Override
    protected Cuenta createWithId(Cuenta cuenta, String id) {
        return new Cuenta(id, cuenta.mesas(), cuenta.estaPagada(), cuenta.reserva(), cuenta.fechaCreacion(), cuenta.fechaPago());
    }

    @Override
    public Optional<Cuenta> findByMesa(Mesa mesa) {
        Map<String, Object> mesaMap = new HashMap<>();
        mesaMap.put("id", mesa.id());
        mesaMap.put("capacidad", mesa.capacidad());
        return buscar(collection.whereArrayContains("mesas", mesaMap)).stream().findFirst();
    }

    @Override
    public List<Cuenta> findByEstaPagada(boolean estaPagada) {
        return buscarPorCampo("estaPagada", estaPagada);
    }

    @Override
    public Optional<Cuenta> findActiveByMesa(Mesa mesa) {
        Map<String, Object> mesaMap = new HashMap<>();
        mesaMap.put("id", mesa.id());
        mesaMap.put("capacidad", mesa.capacidad());

        return buscar(collection
                .whereArrayContains("mesas", mesaMap)
                .whereEqualTo("estaPagada", false)
                .limit(1))
                .stream()
                .findFirst();
    }
}
