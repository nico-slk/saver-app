package com.saver.month;

import com.saver.common.Months;
import com.saver.month.MonthDtos.MonthResponse;
import com.saver.security.CurrentUser;
import com.saver.user.UserTime;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/months")
public class MonthController {

    private final MonthService service;
    private final UserTime time;

    public MonthController(MonthService service, UserTime time) {
        this.service = service;
        this.time = time;
    }

    /** {@code month}: "2026-10" o "current" (mes actual en la zona horaria del usuario). */
    @GetMapping("/{month}")
    public MonthResponse get(@CurrentUser UUID userId, @PathVariable String month) {
        YearMonth ym = "current".equals(month) ? time.currentMonth(userId) : Months.parse(month);
        return service.get(userId, ym);
    }
}
