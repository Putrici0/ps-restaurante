package model;

import java.time.Instant;

public record Pedido(String id, Cuenta cuenta, PedidoEstado pedidoEstado, Instant fechaPedido){}
