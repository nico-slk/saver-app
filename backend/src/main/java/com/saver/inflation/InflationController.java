package com.saver.inflation;

import com.saver.inflation.InflationDtos.InflationOverview;
import com.saver.inflation.InflationDtos.ManualInflationRequest;
import com.saver.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inflation")
public class InflationController {

    private final InflationService service;

    public InflationController(InflationService service) {
        this.service = service;
    }

    @GetMapping
    public InflationOverview overview(@CurrentUser UUID userId) {
        return service.overview(userId);
    }

    @PutMapping("/{currency}/manual")
    public InflationOverview setManual(@CurrentUser UUID userId, @PathVariable String currency,
                                       @Valid @RequestBody ManualInflationRequest req) {
        service.setManual(userId, currency, req.monthlyPercent());
        return service.overview(userId);
    }

    @DeleteMapping("/{currency}/manual")
    public ResponseEntity<Void> clearManual(@CurrentUser UUID userId, @PathVariable String currency) {
        service.clearManual(userId, currency);
        return ResponseEntity.noContent().build();
    }
}
