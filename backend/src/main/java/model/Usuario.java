package model;

import java.util.Date;

public record Usuario(String id, String username, String password, Rol rol, Date fecha_creacion) { }
