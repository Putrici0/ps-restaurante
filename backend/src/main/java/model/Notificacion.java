package model;

import java.time.Instant;
public record Notificacion(String id, Cuenta cuenta, TipoNotificacion tipo, boolean leida, Instant fecha ) { }
