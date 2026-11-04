package com.saver.projection;

import com.saver.common.ApiException;
import com.saver.projection.ProjectionDtos.ProjectionResponse;
import com.saver.security.CurrentUser;
import com.saver.user.User;
import com.saver.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projections")
public class ProjectionController {

    private final ProjectionService service;
    private final UserRepository users;

    public ProjectionController(ProjectionService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    /**
     * @param months           horizonte (1–120, por defecto 12) contando el mes actual
     * @param inflationPercent inflación mensual en % para los meses sin dato oficial
     *                         (por defecto, la manual de la moneda o la estimada)
     * @param startBalance     dinero disponible hoy (por defecto 0)
     */
    @GetMapping
    public ProjectionResponse project(@CurrentUser UUID userId,
                                      @RequestParam(defaultValue = "12") int months,
                                      @RequestParam(required = false) String currency,
                                      @RequestParam(required = false) BigDecimal inflationPercent,
                                      @RequestParam(required = false) BigDecimal startBalance) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.user_not_found"));
        String cur = currency == null || currency.isBlank() ? user.getBaseCurrency() : currency;
        if (!cur.equals("ARS") && !cur.equals("USD")) throw new ApiException(HttpStatus.BAD_REQUEST, "validation.currency");
        BigDecimal inflation = null;
        if (inflationPercent != null) {
            if (inflationPercent.signum() < 0 || inflationPercent.compareTo(BigDecimal.valueOf(50)) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "projection.invalid_inflation");
            }
            inflation = inflationPercent.movePointLeft(2).setScale(5, RoundingMode.HALF_UP);
        }
        return service.project(userId, months, cur, startBalance, inflation);
    }
}
