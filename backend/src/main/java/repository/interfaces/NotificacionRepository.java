package repository.interfaces;

import model.Notificacion;
import model.TipoNotificacion;
import model.Cuenta;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface NotificacionRepository extends Repository<Notificacion, String> {
    List<Notificacion> findByCuenta(Cuenta cuenta);

    List<Notificacion> findByCuentaId(String cuentaId);

    List<Notificacion> findByOrdenId(String ordenId);
    List<Notificacion> findByTipoNotificacion(TipoNotificacion tipoNotificacion);
    List<Notificacion> findByLeida(boolean leida);
    List<Notificacion> findByTipoAndLeida(TipoNotificacion tipoNotificacion, boolean leida);
    List<Notificacion> findEnCursoNoLeidas();
    Optional<Notificacion> findActiveAtencionByCuentaId(String cuentaId);
    Notificacion saveWithDedup(Notificacion notificacion);
    Optional<Notificacion> marcarEnCursoSiDisponible(
            String notificacionId,
            String camareroUid,
            String camareroNombre,
            UnaryOperator<Notificacion> onSuccessBuilder
    );
}
