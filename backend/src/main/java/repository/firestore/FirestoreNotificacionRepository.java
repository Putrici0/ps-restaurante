package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.*;
import repository.interfaces.NotificacionRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreNotificacionRepository extends AbstractFirestoreRepository<Notificacion> implements NotificacionRepository {

    public FirestoreNotificacionRepository(Firestore db) {
        super(db, "notificaciones");
    }

    @Override
    protected Notificacion mapToEntity(String id, Map<String, Object> data) {
        Map<String, Object> cData = (Map<String, Object>) data.get("cuenta");
        Cuenta cuenta = null;
        if (cData != null) {
            cuenta = new Cuenta(
                    (String) cData.get("id"),
                    List.<Mesa>of(),
                    get(cData, "estaPagada", false),
                    Optional.<Reserva>empty(),
                    toInstant(cData.get("fechaCreacion")),
                    Optional.<Instant>empty(),
                    cData.get("password") != null ? (String) cData.get("password") : ""
            );
        }

        return new Notificacion(
            id,
            cuenta,
            toEnum(TipoNotificacion.class, data.get("tipo"), TipoNotificacion.Atencion),
            get(data, "leida", false),
            toInstant(data.get("fecha"))
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Notificacion notificacion) {
        Map<String, Object> map = new HashMap<>();
        if (notificacion.cuenta() != null) {
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("id", notificacion.cuenta().id());
            cMap.put("estaPagada", notificacion.cuenta().payed());
            cMap.put("fechaCreacion", toTimestamp(notificacion.cuenta().fechaCreacion()));
            map.put("cuenta", cMap);
        }
        map.put("tipo", notificacion.tipo().name());
        map.put("leida", notificacion.leida());
        map.put("fecha", toTimestamp(notificacion.fecha()));
        return map;
    }

    @Override
    protected String getEntityId(Notificacion notificacion) {
        return notificacion.id();
    }

    @Override
    protected Notificacion createWithId(Notificacion notificacion, String id) {
        return new Notificacion(id, notificacion.cuenta(), notificacion.tipo(), notificacion.leida(), notificacion.fecha());
    }

    @Override
    public List<Notificacion> findByCuenta(Cuenta cuenta) {
        return buscarPorCampo("cuenta.id", cuenta.id());
    }

    @Override
    public List<Notificacion> findByTipoNotificacion(TipoNotificacion tipoNotificacion) {
        return buscarPorCampo("tipo", tipoNotificacion.name());
    }

    @Override
    public List<Notificacion> findByLeida(boolean leida) {
        return buscarPorCampo("leida", leida);
    }
}
