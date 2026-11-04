package com.saver.common;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;

public final class Months {

    private Months() {}

    /** Parsea "2026-10" o lanza {@code month.invalid}. */
    public static YearMonth parse(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "month.invalid");
        }
    }

    /** Cantidad de meses de {@code from} a {@code to} (0 si son el mismo mes). */
    public static long between(YearMonth from, YearMonth to) {
        return ChronoUnit.MONTHS.between(from, to);
    }

    public static boolean inRange(YearMonth month, YearMonth start, YearMonth end) {
        return !month.isBefore(start) && (end == null || !month.isAfter(end));
    }
}
