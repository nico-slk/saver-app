package com.saver.inflation;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Inflación de un mes como fracción (0.019 = 1,9 %). */
public record MonthlyRate(YearMonth month, BigDecimal rate) {}
