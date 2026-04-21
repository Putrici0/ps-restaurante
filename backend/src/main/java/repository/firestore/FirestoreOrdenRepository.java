package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.Categoria;
import model.Cuenta;
import model.Mesa;
import model.Orden;
import model.OrdenEstado;
import model.Pedido;
import model.PedidoEstado;
import model.Plato;
import repository.interfaces.CuentaRepository;
import repository.interfaces.OrdenRepository;
import repository.interfaces.PedidoRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreOrdenRepository extends AbstractFirestoreRepository<Orden> implements OrdenRepository {

    private final PedidoRepository pedidoRepository;
    private final CuentaRepository cuentaRepository;

    public FirestoreOrdenRepository(Firestore db) {
        this(db, null, null);
    }

    public FirestoreOrdenRepository(
            Firestore db,
            PedidoRepository pedidoRepository,
            CuentaRepository cuentaRepository
    ) {
        super(db, "ordenes");
        this.pedidoRepository = pedidoRepository;
        this.cuentaRepository = cuentaRepository;
    }

    @Override
    protected Orden mapToEntity(String id, Map<String, Object> data) {
        Map<String, Object> pData = getMap(data, "pedido");
        Pedido pedido = mapPedido(pData);
        pedido = hidratarPedidoSiHaceFalta(pedido);

        Map<String, Object> plData = getMap(data, "plato");
        Plato plato = mapPlato(plData);

        return new Orden(
                id,
                pedido,
                plato,
                toBigDecimal(data.get("precio")),
                toEnum(OrdenEstado.class, data.get("ordenEstado"), OrdenEstado.Pendiente),
                toInstant(data.get("fecha")),
                getString(data, "detalles")
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Orden orden) {
        Map<String, Object> map = new HashMap<>();

        map.put("precio", toCents(orden.precio()));
        map.put("ordenEstado", orden.ordenEstado().name());
        map.put("fecha", toTimestamp(orden.fecha()));
        map.put("detalles", orden.detalles());

        if (orden.pedido() != null) {
            map.put("pedido", pedidoToMap(orden.pedido()));
        }

        if (orden.plato() != null) {
            map.put("plato", platoToMap(orden.plato()));
        }

        return map;
    }

    @Override
    protected String getEntityId(Orden orden) {
        return orden.id();
    }

    @Override
    protected Orden createWithId(Orden orden, String id) {
        return new Orden(
                id,
                orden.pedido(),
                orden.plato(),
                orden.precio(),
                orden.ordenEstado(),
                orden.fecha(),
                orden.detalles()
        );
    }

    @Override
    public List<Orden> findByPedido(Pedido pedido) {
        return buscarPorCampo("pedido.id", pedido.id());
    }

    @Override
    public List<Orden> findByEstado(OrdenEstado estado) {
        return buscarPorCampo("ordenEstado", estado.name());
    }

    private Pedido mapPedido(Map<String, Object> pData) {
        if (pData == null) {
            return null;
        }

        Map<String, Object> cData = getMap(pData, "cuenta");
        Cuenta cuenta = mapCuenta(cData);

        return new Pedido(
                (String) pData.get("id"),
                cuenta,
                toEnum(PedidoEstado.class, pData.get("pedidoEstado"), PedidoEstado.Pendiente),
                toInstant(pData.get("fechaPedido"))
        );
    }

    private Pedido hidratarPedidoSiHaceFalta(Pedido pedidoBase) {
        if (pedidoBase == null) {
            return null;
        }

        Cuenta cuenta = pedidoBase.cuenta();

        boolean faltaCuenta = cuenta == null;
        boolean faltanMesas = cuenta != null && (cuenta.mesas() == null || cuenta.mesas().isEmpty());

        if (!faltaCuenta && !faltanMesas) {
            return pedidoBase;
        }

        if (pedidoRepository == null || pedidoBase.id() == null) {
            return pedidoBase;
        }

        Optional<Pedido> pedidoRealOpt = pedidoRepository.findById(pedidoBase.id());
        if (pedidoRealOpt.isEmpty()) {
            return pedidoBase;
        }

        Pedido pedidoReal = pedidoRealOpt.get();
        Cuenta cuentaReal = hidratarCuentaSiHaceFalta(pedidoReal.cuenta());

        return new Pedido(
                pedidoReal.id(),
                cuentaReal,
                pedidoReal.pedidoEstado(),
                pedidoReal.fechaPedido()
        );
    }

    private Cuenta hidratarCuentaSiHaceFalta(Cuenta cuentaBase) {
        if (cuentaBase == null) {
            return null;
        }

        boolean tieneMesas = cuentaBase.mesas() != null && !cuentaBase.mesas().isEmpty();
        if (tieneMesas) {
            return cuentaBase;
        }

        if (cuentaRepository == null || cuentaBase.id() == null) {
            return cuentaBase;
        }

        return cuentaRepository.findById(cuentaBase.id()).orElse(cuentaBase);
    }

    private Plato mapPlato(Map<String, Object> plData) {
        if (plData == null) {
            return null;
        }

        return new Plato(
                (String) plData.get("id"),
                (String) plData.get("nombre"),
                toEnum(Categoria.class, plData.get("categoria"), Categoria.Principal),
                getString(plData, "descripcion"),
                toBigDecimal(plData.get("precio")),
                getBoolean(plData, "estaActivo", true),
                getString(plData, "imagen")
        );
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

    private Map<String, Object> pedidoToMap(Pedido pedido) {
        Map<String, Object> pMap = new HashMap<>();
        pMap.put("id", pedido.id());
        pMap.put("pedidoEstado", pedido.pedidoEstado().name());
        pMap.put("fechaPedido", toTimestamp(pedido.fechaPedido()));

        if (pedido.cuenta() != null) {
            pMap.put("cuenta", cuentaToMap(pedido.cuenta()));
        }

        return pMap;
    }

    private Map<String, Object> platoToMap(Plato plato) {
        Map<String, Object> plMap = new HashMap<>();
        plMap.put("id", plato.id());
        plMap.put("nombre", plato.nombre());
        plMap.put("categoria", plato.categoria().name());
        plMap.put("descripcion", plato.descripcion());
        plMap.put("precio", toCents(plato.precio()));
        plMap.put("estaActivo", plato.estaActivo());
        plMap.put("imagen", plato.imagen());
        return plMap;
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