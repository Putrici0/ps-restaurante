package model;

import java.time.LocalDate;

public record Notificacion(String id, Cuenta cuenta, TipoNotificacion tipo, boolean leida, LocalDate fecha ) { }
