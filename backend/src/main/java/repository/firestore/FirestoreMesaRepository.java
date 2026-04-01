package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Mesa;
import repository.interfaces.MesaRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreMesaRepository extends AbstractFirestoreRepository<Mesa> implements MesaRepository {

    public FirestoreMesaRepository(Firestore db) {
        super(db, "mesas");
    }

    @Override
    protected Mesa mapToEntity(String id, Map<String, Object> data) {
        return new Mesa(
            id,
            ((Long) data.getOrDefault("capacidad", 0L)).intValue()
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Mesa mesa) {
        Map<String, Object> map = new HashMap<>();
        map.put("capacidad", mesa.capacidad());
        return map;
    }

    @Override
    protected String getEntityId(Mesa mesa) {
        return mesa.id();
    }

    @Override
    protected Mesa createWithId(Mesa mesa, String id) {
        return new Mesa(id, mesa.capacidad());
    }

    @Override
    public List<Mesa> findByCapacidad(int capacidad) {
        return buscarPorCampo("capacidad", capacidad);
    }
}
