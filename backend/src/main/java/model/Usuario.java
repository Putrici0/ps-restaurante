package model;

import java.time.Instant;

public record Usuario(String id, String username, String passwordHash, Rol rol, Instant fechaCreacion) { }
