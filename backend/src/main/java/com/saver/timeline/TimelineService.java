package com.saver.timeline;

import com.saver.common.ApiException;
import com.saver.common.Months;
import com.saver.history.HistoryDtos.MonthPoint;
import com.saver.history.HistoryService;
import com.saver.projection.ProjectionDtos.ProjectionMonth;
import com.saver.projection.ProjectionService;
import com.saver.timeline.TimelineDtos.PointType;
import com.saver.timeline.TimelineDtos.TimelinePoint;
import com.saver.timeline.TimelineDtos.TimelineResponse;
import com.saver.user.UserTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serie de meses desde el pasado hasta el futuro: los meses anteriores al actual salen del historial
 * y el actual y los siguientes de la proyección (sin saldo inicial).
 */
@Service
public class TimelineService {

    static final int MAX_MONTHS = 120;
    /** Por defecto: 6 meses antes del actual, el actual y 6 después. */
    static final int DEFAULT_BEFORE = 6;
    static final int DEFAULT_MONTHS = 13;

    private final HistoryService history;
    private final ProjectionService projection;
    private final UserTime time;

    public TimelineService(HistoryService history, ProjectionService projection, UserTime time) {
        this.history = history;
        this.projection = projection;
        this.time = time;
    }

    @Transactional
    public TimelineResponse timeline(UUID userId, YearMonth from, Integer months, String currency) {
        LocalDate today = time.today(userId);
        YearMonth current = YearMonth.from(today);
        int count = months == null ? DEFAULT_MONTHS : months;
        if (count < 1 || count > MAX_MONTHS) throw new ApiException(HttpStatus.BAD_REQUEST, "timeline.invalid_months");
        YearMonth start = from == null ? current.minusMonths(DEFAULT_BEFORE) : from;
        if (Math.abs(Months.between(current, start)) > MAX_MONTHS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "timeline.invalid_from");
        }
        YearMonth end = start.plusMonths(count - 1L);

        List<TimelinePoint> points = new ArrayList<>();
        YearMonth pastEnd = end.isBefore(current) ? end : current.minusMonths(1);
        if (!start.isAfter(pastEnd)) {
            for (MonthPoint p : history.history(userId, start, pastEnd, currency, Set.of()).months()) {
                points.add(new TimelinePoint(p.month(), PointType.PAST, p.income(), p.expensesTotal(), p.savings()));
            }
        }
        if (!end.isBefore(current)) {
            int ahead = (int) Months.between(current, end) + 1;
            for (ProjectionMonth p : projection.project(userId, ahead, currency, null, null).series()) {
                if (p.month().isBefore(start)) continue;
                points.add(new TimelinePoint(p.month(), p.month().equals(current) ? PointType.CURRENT : PointType.FUTURE,
                        p.income(), p.expenses(), p.balance()));
            }
        }
        return new TimelineResponse(start, end, current, today, currency, points);
    }
}
