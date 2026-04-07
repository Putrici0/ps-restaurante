package repository.firestore;

import com.google.cloud.firestore.Firestore;
import model.*;
import repository.interfaces.PedidoRepository;

import java.time.Instant;
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
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("id", pedido.cuenta().id());
            cMap.put("estaPagada", pedido.cuenta().payed());
            cMap.put("fechaCreacion", toTimestamp(pedido.cuenta().fechaCreacion()));
            map.put("cuenta", cMap);
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
}
