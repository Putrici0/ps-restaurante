package repository.interfaces;

import model.Notificacion;
import model.TipoNotificacion;
import model.Cuenta;

import java.util.List;

public interface NotificacionRepository extends Repository<Notificacion, String> {
    List<Notificacion> findByCuenta(Cuenta cuenta);
    List<Notificacion> findByTipoNotificacion(TipoNotificacion tipoNotificacion);
    List<Notificacion> findByLeida(boolean leida);
}
