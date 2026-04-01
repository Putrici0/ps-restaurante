package model;

import java.math.BigDecimal;

public record Plato(String id, String nombre, Categoria categoria, String descripcion, BigDecimal precio, boolean estaActivo) {}
