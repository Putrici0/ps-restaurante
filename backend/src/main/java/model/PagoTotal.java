package model;

import java.math.BigDecimal;

public record PagoTotal(String id, BigDecimal total, Cuenta cuenta) { }
