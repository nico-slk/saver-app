package com.saver.user;

import com.saver.common.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** "Hoy" y "mes actual" según la zona horaria del usuario. */
@Component
public class UserTime {

    private final UserRepository users;
    private final Clock clock;

    public UserTime(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public ZoneId zone(UUID userId) {
        return users.findById(userId)
                .map(u -> ZoneId.of(u.getTimezone()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.user_not_found"));
    }

    public LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(zone(userId)));
    }

    public YearMonth currentMonth(UUID userId) {
        return YearMonth.from(today(userId));
    }
}
