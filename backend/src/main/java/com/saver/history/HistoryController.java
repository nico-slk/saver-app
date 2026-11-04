package com.saver.history;

import com.saver.common.ApiException;
import com.saver.common.Months;
import com.saver.history.HistoryDtos.CompareResponse;
import com.saver.history.HistoryDtos.HistoryResponse;
import com.saver.security.CurrentUser;
import com.saver.user.UserRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService service;
    private final UserRepository users;

    public HistoryController(HistoryService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    /**
     * Serie mensual. {@code from}/{@code to} en AAAA-MM (por defecto: últimos 12 meses hasta el actual).
     * {@code categories}: ids separados por coma para filtrar los gastos.
     */
    @GetMapping
    public HistoryResponse history(@CurrentUser UUID userId,
                                   @RequestParam(required = false) String from,
                                   @RequestParam(required = false) String to,
                                   @RequestParam(required = false) String currency,
                                   @RequestParam(required = false) List<UUID> categories) {
        return service.history(userId, month(from), month(to), currency(userId, currency),
                categories == null ? Set.of() : Set.copyOf(categories));
    }

    /** Compara el mes {@code from} con {@code to} (por defecto, el mes actual). */
    @GetMapping("/compare")
    public CompareResponse compare(@CurrentUser UUID userId,
                                   @RequestParam String from,
                                   @RequestParam(required = false) String to,
                                   @RequestParam(required = false) String currency) {
        return service.compare(userId, Months.parse(from), month(to), currency(userId, currency));
    }

    private static YearMonth month(String value) {
        return value == null || value.isBlank() ? null : Months.parse(value);
    }

    private String currency(UUID userId, String currency) {
        if (currency == null || currency.isBlank()) {
            return users.findById(userId).map(u -> u.getBaseCurrency()).orElse("ARS");
        }
        if (!currency.equals("ARS") && !currency.equals("USD")) throw new ApiException(HttpStatus.BAD_REQUEST, "validation.currency");
        return currency;
    }
}
