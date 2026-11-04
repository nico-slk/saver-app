package com.saver.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilidades de montos: siempre BigDecimal con 2 decimales. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() {}

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
