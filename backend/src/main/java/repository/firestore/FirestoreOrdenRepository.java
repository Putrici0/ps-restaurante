package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.*;
import repository.interfaces.OrdenRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreOrdenRepository extends AbstractFirestoreRepository<Orden> implements OrdenRepository {

    public FirestoreOrdenRepository(Firestore db) {
        super(db, "ordenes");
    }

    @Override
    protected Orden mapToEntity(String id, Map<String, Object> data) {
        Map<String, Object> pData = (Map<String, Object>) data.get("pedido");
        Pedido pedido = null;
        if (pData != null) {
            pedido = new Pedido((String) pData.get("id"), null, toEnum(PedidoEstado.class, pData.get("pedidoEstado"), PedidoEstado.Pendiente), toInstant(pData.get("fechaPedido")));
        }

        Map<String, Object> plData = (Map<String, Object>) data.get("plato");
        Plato plato = null;
        if (plData != null) {
            plato = new Plato((String) plData.get("id"), (String) plData.get("nombre"), toEnum(Categoria.class, plData.get("categoria"), Categoria.Principal), "", toBigDecimal(0L), true);
        }

        return new Orden(
            id,
            pedido,
            plato,
            toBigDecimal(data.get("precio")),
            toEnum(OrdenEstado.class, data.get("ordenEstado"), OrdenEstado.Pendiente),
            toInstant(data.get("fecha")),
            get(data, "detalles", "")
        );
    }

    @Override
    protected Map<String, Object> entityToMap(Orden orden) {
        Map<String, Object> map = new HashMap<>();
        map.put("precio", toCents(orden.precio()));
        
        if (orden.pedido() != null) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("id", orden.pedido().id());
            pMap.put("pedidoEstado", orden.pedido().pedidoEstado().name());
            pMap.put("fechaPedido", toTimestamp(orden.pedido().fechaPedido()));
            map.put("pedido", pMap);
        }

        if (orden.plato() != null) {
            Map<String, Object> plMap = new HashMap<>();
            plMap.put("id", orden.plato().id());
            plMap.put("nombre", orden.plato().nombre());
            plMap.put("categoria", orden.plato().categoria().name());
            map.put("plato", plMap);
        }

        map.put("ordenEstado", orden.ordenEstado().name());
        map.put("fecha", toTimestamp(orden.fecha()));
        map.put("detalles", orden.detalles());
        return map;
    }

    @Override
    protected String getEntityId(Orden orden) {
        return orden.id();
    }

    @Override
    protected Orden createWithId(Orden orden, String id) {
        return new Orden(id, orden.pedido(), orden.plato(), orden.precio(), orden.ordenEstado(), orden.fecha(), orden.detalles());
    }

    @Override
    public List<Orden> findByPedido(Pedido pedido) {
        return buscarPorCampo("pedido.id", pedido.id());
    }

    @Override
    public List<Orden> findByEstado(OrdenEstado estado) {
        return buscarPorCampo("ordenEstado", estado.name());
    }
}
