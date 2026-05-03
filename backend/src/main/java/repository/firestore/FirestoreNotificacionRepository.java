package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Cuenta;
import model.Mesa;
import model.Notificacion;
import model.TipoNotificacion;
import repository.interfaces.NotificacionRepository;

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
            List<Mesa> mesas = mapMesas(cData.get("mesas"));

            cuenta = new Cuenta(
                    (String) cData.get("id"),
                    mesas,
                    get(cData, "estaPagada", false),
                    Optional.empty(),
                    toInstant(cData.get("fechaCreacion")),
                    Optional.empty(),
                    cData.get("password") != null ? (String) cData.get("password") : "",
                    Optional.empty()
            );
        }

        return new Notificacion(
                id,
                cuenta,
                toEnum(TipoNotificacion.class, data.get("tipo"), TipoNotificacion.Atencion),
                get(data, "leida", false),
                toInstant(data.get("fecha")),
                data.get("ordenId") != null ? String.valueOf(data.get("ordenId")) : null,
                data.get("nombreItem") != null ? String.valueOf(data.get("nombreItem")) : null,
                data.get("categoriaItem") != null ? String.valueOf(data.get("categoriaItem")) : null,
                get(data, "enCurso", false),
                data.get("camareroUid") != null ? String.valueOf(data.get("camareroUid")) : null,
                data.get("camareroNombre") != null ? String.valueOf(data.get("camareroNombre")) : null,
                toInstant(data.get("fechaEnCurso"))
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
            cMap.put("mesas", mapMesas(notificacion.cuenta().mesas()));
            map.put("cuenta", cMap);
        }

        map.put("tipo", notificacion.tipo().name());
        map.put("leida", notificacion.leida());
        map.put("fecha", toTimestamp(notificacion.fecha()));
        map.put("ordenId", notificacion.ordenId());
        map.put("nombreItem", notificacion.nombreItem());
        map.put("categoriaItem", notificacion.categoriaItem());
        map.put("enCurso", notificacion.enCurso());
        map.put("camareroUid", notificacion.camareroUid());
        map.put("camareroNombre", notificacion.camareroNombre());
        map.put("fechaEnCurso", toTimestamp(notificacion.fechaEnCurso()));

        return map;
    }

    @Override
    protected String getEntityId(Notificacion notificacion) {
        return notificacion.id();
    }

    @Override
    protected Notificacion createWithId(Notificacion notificacion, String id) {
        return new Notificacion(
                id,
                notificacion.cuenta(),
                notificacion.tipo(),
                notificacion.leida(),
                notificacion.fecha(),
                notificacion.ordenId(),
                notificacion.nombreItem(),
                notificacion.categoriaItem(),
                notificacion.enCurso(),
                notificacion.camareroUid(),
                notificacion.camareroNombre(),
                notificacion.fechaEnCurso()
        );
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

    private List<Map<String, Object>> mapMesas(List<Mesa> mesas) {
        if (mesas == null) {
            return List.of();
        }

        return mesas.stream()
                .map(mesa -> {
                    Map<String, Object> mesaMap = new HashMap<>();
                    mesaMap.put("id", mesa.id());
                    mesaMap.put("capacidad", mesa.capacidad());
                    return mesaMap;
                })
                .toList();
    }

    private List<Mesa> mapMesas(Object mesasObject) {
        if (!(mesasObject instanceof List<?> mesasRaw)) {
            return List.of();
        }

        return mesasRaw.stream()
                .filter(item -> item instanceof Map)
                .map(item -> {
                    Map<String, Object> mesaMap = (Map<String, Object>) item;

                    String mesaId = String.valueOf(mesaMap.get("id"));
                    int capacidad = 0;

                    Object capacidadObject = mesaMap.get("capacidad");

                    if (capacidadObject instanceof Number number) {
                        capacidad = number.intValue();
                    }

                    return new Mesa(mesaId, capacidad);
                })
                .toList();
    }
}
