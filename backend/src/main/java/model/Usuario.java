package model;

import java.time.LocalDate;

public record Usuario(String id, String username, String contraseña, Rol rol, LocalDate fecha_creacion) { }
