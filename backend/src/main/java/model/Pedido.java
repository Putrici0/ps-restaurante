package model;

import java.time.LocalDate;


public record Pedido(String id, Cuenta cuenta, PedidoEstado pedidoEstado, LocalDate localDate){}

