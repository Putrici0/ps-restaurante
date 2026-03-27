package model;

import java.util.Date;

public record Notificacion(String id, Cuenta cuenta, TipoNotificacion tipo, boolean leida, Date fecha ) { }
