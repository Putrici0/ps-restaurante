package repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import model.Cuenta;
import model.Mesa;
import model.MetodoPago;
import model.Reserva;
import repository.interfaces.CuentaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreCuentaRepository implements CuentaRepository {

    private static final String COLLECTION = "cuentas";
    private final Firestore db;

    public FirestoreCuentaRepository(Firestore db) {
        this.db = db;
    }

    @Override
    public Cuenta save(Cuenta cuenta) {
        try {
            String id = cuenta.id() != null
                    ? cuenta.id()
                    : db.collection(COLLECTION).document().getId();

            Cuenta cuentaConId = new Cuenta(
                    id,
                    cuenta.mesas(),
                    cuenta.payed(),
                    cuenta.reserva(),
                    cuenta.fechaCreacion(),
                    cuenta.fechaPago(),
                    cuenta.password(),
                    cuenta.metodoPago()
            );

            db.collection(COLLECTION)
                    .document(id)
                    .set(cuentaToMap(cuentaConId))
                    .get();

            return cuentaConId;
        } catch (Exception e) {
            throw new RuntimeException("Error al guardar la cuenta", e);
        }
    }

    @Override
    public Optional<Cuenta> findById(String id) {
        try {
            DocumentSnapshot document = db.collection(COLLECTION).document(id).get().get();

            if (!document.exists()) {
                return Optional.empty();
            }

            return Optional.of(mapDocumentToCuenta(document));
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar la cuenta por id", e);
        }
    }

    @Override
    public List<Cuenta> findAll() {
        try {
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION).get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            List<Cuenta> cuentas = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                cuentas.add(mapDocumentToCuenta(document));
            }

            return cuentas;
        } catch (Exception e) {
            throw new RuntimeException("Error al listar las cuentas", e);
        }
    }

    @Override
    public Optional<Cuenta> findByMesa(Mesa mesa) {
        try {
            return findAll().stream()
                    .filter(cuenta -> cuenta.mesas() != null)
                    .filter(cuenta -> cuenta.mesas().stream().anyMatch(m -> m.id().equals(mesa.id())))
                    .filter(cuenta -> !cuenta.payed())
                    .findFirst();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar cuenta por mesa", e);
        }
    }

    @Override
    public List<Cuenta> findByEstaPagada(boolean estaPagada) {
        try {
            return findAll().stream()
                    .filter(cuenta -> cuenta.payed() == estaPagada)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar cuentas por estado de pago", e);
        }
    }

    @Override
    public Cuenta update(String id, Cuenta cuenta) {
        try {
            if (!existsById(id)) {
                throw new IllegalArgumentException("La cuenta no existe");
            }

            Cuenta cuentaActualizada = new Cuenta(
                    id,
                    cuenta.mesas(),
                    cuenta.payed(),
                    cuenta.reserva(),
                    cuenta.fechaCreacion(),
                    cuenta.fechaPago(),
                    cuenta.password(),
                    cuenta.metodoPago()
            );

            db.collection(COLLECTION)
                    .document(id)
                    .set(cuentaToMap(cuentaActualizada))
                    .get();

            return cuentaActualizada;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar la cuenta", e);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            if (!existsById(id)) {
                throw new IllegalArgumentException("La cuenta no existe");
            }

            db.collection(COLLECTION).document(id).delete().get();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error al borrar la cuenta", e);
        }
    }

    @Override
    public boolean existsById(String id) {
        try {
            DocumentSnapshot document = db.collection(COLLECTION).document(id).get().get();
            return document.exists();
        } catch (Exception e) {
            throw new RuntimeException("Error al comprobar si existe la cuenta", e);
        }
    }

    private Cuenta mapDocumentToCuenta(DocumentSnapshot document) {
        String id = document.contains("id") ? document.getString("id") : document.getId();

        List<Mesa> mesas = mapMesas(document.get("mesas"));
        boolean payed = Boolean.TRUE.equals(document.getBoolean("payed"));

        Optional<Reserva> reserva = Optional.empty();

        Timestamp fechaCreacionTimestamp = document.getTimestamp("fechaCreacion");
        Instant fechaCreacion = fechaCreacionTimestamp != null
                ? fechaCreacionTimestamp.toDate().toInstant()
                : Instant.now();

        Timestamp fechaPagoTimestamp = document.getTimestamp("fechaPago");
        Optional<Instant> fechaPago = fechaPagoTimestamp != null
                ? Optional.of(fechaPagoTimestamp.toDate().toInstant())
                : Optional.empty();

        String password = document.getString("password");
        if (password == null) {
            password = "";
        }

        String metodoPagoRaw = document.getString("metodoPago");

        Optional<MetodoPago> metodoPago = Optional.empty();
        if (metodoPagoRaw != null && !metodoPagoRaw.isBlank()) {
            metodoPago = Optional.of(MetodoPago.valueOf(metodoPagoRaw));
        }

        return new Cuenta(
                id,
                mesas,
                payed,
                reserva,
                fechaCreacion,
                fechaPago,
                password,
                metodoPago
        );
    }

    private Map<String, Object> cuentaToMap(Cuenta cuenta) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", cuenta.id());
        data.put("mesas", mesasToList(cuenta.mesas()));
        data.put("payed", cuenta.payed());
        data.put("reserva", null);
        data.put("fechaCreacion", cuenta.fechaCreacion());
        data.put("fechaPago", cuenta.fechaPago().orElse(null));
        data.put("password", cuenta.password());
        data.put("metodoPago", cuenta.metodoPago().map(Enum::name).orElse(null));
        return data;
    }

    private List<Map<String, Object>> mesasToList(List<Mesa> mesas) {
        List<Map<String, Object>> lista = new ArrayList<>();

        if (mesas == null) {
            return lista;
        }

        for (Mesa mesa : mesas) {
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
            if (obj instanceof Map<?, ?> mesaMap) {
                String id = mesaMap.get("id") != null ? mesaMap.get("id").toString() : null;

                Number capacidadNumber = (Number) mesaMap.get("capacidad");
                int capacidad = capacidadNumber != null ? capacidadNumber.intValue() : 0;

                if (id != null) {
                    mesas.add(new Mesa(id, capacidad));
                }
            }
        }

        return mesas;
    }
}