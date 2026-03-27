package model;

import java.math.BigDecimal;

public record Plato(String id, String nombre, Categoria categoria, String descripcion, BigDecimal price, Boolean activo) {}