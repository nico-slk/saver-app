package com.saver.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Cotización de un día: {@code buy} = a cuánto te compran un dólar, {@code sell} = a cuánto te lo venden. */
public record ExchangeRate(LocalDate day, ExchangeRateType type, BigDecimal buy, BigDecimal sell, String source,
                           Instant fetchedAt) {}
