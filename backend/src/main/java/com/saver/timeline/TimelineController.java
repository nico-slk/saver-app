package com.saver.timeline;

import com.saver.common.ApiException;
import com.saver.common.Months;
import com.saver.security.CurrentUser;
import com.saver.timeline.TimelineDtos.TimelineResponse;
import com.saver.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {

    private final TimelineService service;
    private final UserRepository users;

    public TimelineController(TimelineService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    /**
     * @param from   primer mes (AAAA-MM); por defecto 6 meses antes del actual
     * @param months cantidad de meses (1–120); por defecto 13 (6 antes, el actual y 6 después)
     */
    @GetMapping
    public TimelineResponse timeline(@CurrentUser UUID userId,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) Integer months,
                                     @RequestParam(required = false) String currency) {
        String cur = currency == null || currency.isBlank()
                ? users.findById(userId).map(u -> u.getBaseCurrency()).orElse("ARS") : currency;
        if (!cur.equals("ARS") && !cur.equals("USD")) throw new ApiException(HttpStatus.BAD_REQUEST, "validation.currency");
        return service.timeline(userId, from == null || from.isBlank() ? null : Months.parse(from), months, cur);
    }
}
