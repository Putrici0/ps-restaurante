package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Cuenta;
import model.Mesa;
import model.Pedido;
import model.PedidoEstado;
import repository.interfaces.PedidoRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestorePedidoRepository extends AbstractFirestoreRepository<Pedido> implements PedidoRepository {

    public FirestorePedidoRepository(Firestore db) {
        super(db, "pedidos");
    }

    @Override
    protected Pedido mapToEntity(String id, Map<String, Object> data) {
        Map<String, Object> cData = getMap(data, "cuenta");
        Cuenta cuenta = mapCuenta(cData);

        return new Pedido(
                id,
                cuenta,
                toEnum(PedidoEstado.class, data.get("pedidoEstado"), PedidoEstado.Pendiente),
                toInstant(data.get("fechaPedido"))
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Pedido pedido) {
        Map<String, Object> map = new HashMap<>();

        if (pedido.cuenta() != null) {
            map.put("cuenta", cuentaToMap(pedido.cuenta()));
        }

        map.put("pedidoEstado", pedido.pedidoEstado().name());
        map.put("fechaPedido", toTimestamp(pedido.fechaPedido()));

        return map;
    }

    @Override
    protected String getEntityId(Pedido pedido) {
        return pedido.id();
    }

    @Override
    protected Pedido createWithId(Pedido pedido, String id) {
        return new Pedido(id, pedido.cuenta(), pedido.pedidoEstado(), pedido.fechaPedido());
    }

    @Override
    public List<Pedido> findByCuenta(Cuenta cuenta) {
        return buscarPorCampo("cuenta.id", cuenta.id());
    }

    @Override
    public List<Pedido> findByEstado(PedidoEstado estado) {
        return buscarPorCampo("pedidoEstado", estado.name());
    }

    private Cuenta mapCuenta(Map<String, Object> cData) {
        if (cData == null) {
            return null;
        }

        return new Cuenta(
                (String) cData.get("id"),
                mapMesas(cData.get("mesas")),
                getBoolean(cData, "payed", getBoolean(cData, "estaPagada", false)),
                Optional.empty(),
                toInstant(cData.get("fechaCreacion")),
                Optional.ofNullable(toInstant(cData.get("fechaPago"))),
                getString(cData, "password"),
                Optional.empty()
        );
    }

    private Map<String, Object> cuentaToMap(Cuenta cuenta) {
        Map<String, Object> cMap = new HashMap<>();
        cMap.put("id", cuenta.id());
        cMap.put("mesas", mesasToList(cuenta.mesas()));
        cMap.put("payed", cuenta.payed());
        cMap.put("estaPagada", cuenta.payed());
        cMap.put("fechaCreacion", toTimestamp(cuenta.fechaCreacion()));
        cMap.put("fechaPago", cuenta.fechaPago().map(this::toTimestamp).orElse(null));
        cMap.put("password", cuenta.password());
        return cMap;
    }

    private List<Map<String, Object>> mesasToList(List<Mesa> mesas) {
        List<Map<String, Object>> lista = new ArrayList<>();

        if (mesas == null) {
            return lista;
        }

        for (Mesa mesa : mesas) {
            if (mesa == null) {
                continue;
            }

            Map<String, Object> mesaMap = new HashMap<>();
            mesaMap.put("id", mesa.id());
            mesaMap.put("capacidad", mesa.capacidad());
            lista.add(mesaMap);
        }

        return lista;
    }

    @SuppressWarnings("unchecked")
    private List<Mesa> mapMesas(Object mesasObj) {
        List<Mesa> mesas = new ArrayList<>();

        if (!(mesasObj instanceof List<?> listaMesas)) {
            return mesas;
        }

        for (Object obj : listaMesas) {
            if (!(obj instanceof Map<?, ?> mesaMapRaw)) {
                continue;
            }

            Map<String, Object> mesaMap = (Map<String, Object>) mesaMapRaw;
            String mesaId = mesaMap.get("id") != null ? mesaMap.get("id").toString() : null;
            Number capacidadNumber = (Number) mesaMap.get("capacidad");
            int capacidad = capacidadNumber != null ? capacidadNumber.intValue() : 0;

            if (mesaId != null && !mesaId.isBlank()) {
                mesas.add(new Mesa(mesaId, capacidad));
            }
        }

        return mesas;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        return value instanceof Boolean b ? b : defaultValue;
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : "";
    }
}