package repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FirestoreCuentaRepository implements CuentaRepository {

    private static final String COLLECTION = "cuentas";
    private static final String MESA_IDS_FIELD = "mesaIds";
    private final Firestore db;
    private volatile boolean legacyMesaIdsFallbackEnabled = true;

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
    public List<Cuenta> findPage(int limit, String cursor) {
        try {
            int safeLimit = Math.max(1, Math.min(limit, 100));
            Query query = db.collection(COLLECTION)
                    .orderBy(FieldPath.documentId())
                    .limit(safeLimit);

            if (cursor != null && !cursor.isBlank()) {
                query = query.startAfter(cursor.trim());
            }

            List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();
            List<Cuenta> cuentas = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                cuentas.add(mapDocumentToCuenta(document));
            }

            return cuentas;
        } catch (Exception e) {
            throw new RuntimeException("Error al paginar las cuentas", e);
        }
    }

    @Override
    public Optional<Cuenta> findByMesa(Mesa mesa) {
        try {
            if (mesa == null || mesa.id() == null || mesa.id().isBlank()) {
                return Optional.empty();
            }

            List<Cuenta> cuentasActivas = queryCuentasActivasPorMesaIdsConFallback(List.of(mesa.id()));
            if (cuentasActivas.isEmpty() && legacyMesaIdsFallbackEnabled) {
                cuentasActivas = findCuentasActivasLegacyFallback();
            }

            return cuentasActivas.stream()
                    .filter(cuenta -> contieneMesa(cuenta, mesa.id()))
                    .findFirst();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar cuenta por mesa", e);
        }
    }

    @Override
    public List<Cuenta> findActivasByMesaIds(List<String> mesaIds) {
        try {
            List<String> idsLimpios = limpiarMesaIds(mesaIds);
            if (idsLimpios.isEmpty()) {
                return List.of();
            }

            List<Cuenta> cuentasActivas = queryCuentasActivasPorMesaIdsConFallback(idsLimpios);
            if (cuentasActivas.isEmpty() && legacyMesaIdsFallbackEnabled) {
                cuentasActivas = findCuentasActivasLegacyFallback();
            }

            Set<String> idsBuscados = new LinkedHashSet<>(idsLimpios);
            return cuentasActivas.stream()
                    .filter(cuenta -> cuenta.mesas() != null)
                    .filter(cuenta -> cuenta.mesas().stream().anyMatch(mesa -> mesa != null && idsBuscados.contains(mesa.id())))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar cuentas activas por mesas", e);
        }
    }

    private List<Cuenta> queryCuentasActivasPorMesaIdsConFallback(List<String> mesaIds) {
        try {
            return queryCuentasActivasPorMesaIds(mesaIds);
        } catch (Exception e) {
            return findCuentasActivasLegacyFallback();
        }
    }

    private List<Cuenta> findCuentasActivasLegacyFallback() {
        try {
            List<QueryDocumentSnapshot> documents = db.collection(COLLECTION)
                    .whereEqualTo("payed", false)
                    .get()
                    .get()
                    .getDocuments();

            if (documents.stream().allMatch(document -> document.contains(MESA_IDS_FIELD))) {
                legacyMesaIdsFallbackEnabled = false;
            }

            return documents.stream()
                    .map(this::mapDocumentToCuenta)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar cuentas activas antiguas", e);
        }
    }

    private List<Cuenta> queryCuentasActivasPorMesaIds(List<String> mesaIds) throws Exception {
        List<String> idsLimpios = limpiarMesaIds(mesaIds);
        if (idsLimpios.isEmpty()) {
            return List.of();
        }

        Map<String, Cuenta> cuentasPorId = new LinkedHashMap<>();
        for (int i = 0; i < idsLimpios.size(); i += 10) {
            int fin = Math.min(i + 10, idsLimpios.size());
            List<String> lote = idsLimpios.subList(i, fin);

            List<QueryDocumentSnapshot> documentos = db.collection(COLLECTION)
                    .whereEqualTo("payed", false)
                    .whereArrayContainsAny(MESA_IDS_FIELD, lote)
                    .get()
                    .get()
                    .getDocuments();

            for (QueryDocumentSnapshot document : documentos) {
                Cuenta cuenta = mapDocumentToCuenta(document);
                cuentasPorId.put(cuenta.id(), cuenta);
            }
        }

        return new ArrayList<>(cuentasPorId.values());
    }

    private List<String> limpiarMesaIds(List<String> mesaIds) {
        if (mesaIds == null) {
            return List.of();
        }

        return mesaIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean contieneMesa(Cuenta cuenta, String mesaId) {
        return cuenta != null
                && cuenta.mesas() != null
                && cuenta.mesas().stream().anyMatch(m -> m != null && mesaId.equals(m.id()));
    }

    @Override
    public List<Cuenta> findByEstaPagada(boolean estaPagada) {
        try {
            return db.collection(COLLECTION)
                    .whereEqualTo("payed", estaPagada)
                    .get()
                    .get()
                    .getDocuments()
                    .stream()
                    .map(this::mapDocumentToCuenta)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar cuentas por estado de pago", e);
        }
    }

    @Override
    public Cuenta update(String id, Cuenta cuenta) {
        try {
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
                    .update(cuentaToMap(cuentaActualizada))
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
        data.put(MESA_IDS_FIELD, mesaIds(cuenta.mesas()));
        data.put("payed", cuenta.payed());
        data.put("reserva", null);
        data.put("fechaCreacion", cuenta.fechaCreacion());
        data.put("fechaPago", cuenta.fechaPago().orElse(null));
        data.put("password", cuenta.password());
        data.put("metodoPago", cuenta.metodoPago().map(Enum::name).orElse(null));
        return data;
    }

    private List<String> mesaIds(List<Mesa> mesas) {
        if (mesas == null) {
            return List.of();
        }

        return mesas.stream()
                .filter(mesa -> mesa != null && mesa.id() != null && !mesa.id().isBlank())
                .map(mesa -> mesa.id().trim())
                .distinct()
                .toList();
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
