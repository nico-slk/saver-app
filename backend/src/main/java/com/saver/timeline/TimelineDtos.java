package com.saver.timeline;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class TimelineDtos {

    private TimelineDtos() {}

    /** PAST = real; CURRENT = mes en curso (gastos generados); FUTURE = proyectado. */
    public enum PointType { PAST, CURRENT, FUTURE }

    public record TimelinePoint(YearMonth month, PointType type, BigDecimal income, BigDecimal expenses,
                                BigDecimal balance) {}

    public record TimelineResponse(YearMonth from, YearMonth to, YearMonth current, LocalDate today,
                                   String currency, List<TimelinePoint> points) {}
}
