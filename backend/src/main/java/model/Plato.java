package model;

public record Plato(String id, String nombre, Categoria categoria, String descripcion, double price, Boolean activo) {}