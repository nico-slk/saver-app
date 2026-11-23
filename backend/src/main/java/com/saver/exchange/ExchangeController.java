package com.saver.exchange;

import com.saver.exchange.ExchangeRateService.Conversion;
import com.saver.exchange.ExchangeRateService.Purpose;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exchange-rates")
public class ExchangeController {

    public record RateResponse(ExchangeRateType type, LocalDate day, BigDecimal buy, BigDecimal sell) {}

    private final ExchangeRateService service;

    public ExchangeController(ExchangeRateService service) {
        this.service = service;
    }

    /** Última cotización de cada tipo de dólar. */
    @GetMapping
    public List<RateResponse> latest() {
        return service.latest().values().stream().map(r -> new RateResponse(r.type(), r.day(), r.buy(), r.sell())).toList();
    }

    /** Vista previa de una conversión (la usa el formulario de pago/cobro/transferencia). */
    @GetMapping("/convert")
    public Conversion convert(@RequestParam BigDecimal amount, @RequestParam String from, @RequestParam String to,
                              @RequestParam(defaultValue = "PAYMENT") Purpose purpose,
                              @RequestParam(required = false) BigDecimal rate) {
        return service.convert(amount, from, to, purpose, rate);
    }
}
