package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Reserva;
import repository.interfaces.ReservaRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreReservaRepository extends AbstractFirestoreRepository<Reserva> implements ReservaRepository {

    public FirestoreReservaRepository(Firestore db) {
        super(db, "reservas");
    }

    @Override
    protected Reserva mapToEntity(String id, Map<String, Object> data) {
        return new Reserva(
            id,
            get(data, "nombre", ""),
            toInstant(data.get("fecha")),
            ((Long) data.getOrDefault("capacidad", 0L)).intValue(),
            toInstant(data.get("fechaCreacion"))
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Reserva reserva) {
        Map<String, Object> map = new HashMap<>();
        map.put("nombre", reserva.nombre());
        map.put("fecha", toTimestamp(reserva.fecha()));
        map.put("capacidad", reserva.capacidad());
        map.put("fechaCreacion", toTimestamp(reserva.fechaCreacion()));
        return map;
    }

    @Override
    protected String getEntityId(Reserva reserva) {
        return reserva.id();
    }

    @Override
    protected Reserva createWithId(Reserva reserva, String id) {
        return new Reserva(id, reserva.nombre(), reserva.fecha(), reserva.capacidad(), reserva.fechaCreacion());
    }

    @Override
    public List<Reserva> findByFecha(Instant fecha) {
        return buscarPorCampo("fecha", toTimestamp(fecha));
    }
}
