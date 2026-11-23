package com.saver.exchange;

import com.saver.common.ApiException;
import com.saver.common.HttpJsonClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cotizaciones del dólar y conversiones entre ARS y USD.
 *
 * <p>Qué dólar se usa según el uso (decisión de producto):
 * <ul>
 *   <li>Gasto en USD pagado desde una cuenta en pesos con tarjeta: {@link ExchangeRateType#TARJETA}; sin tarjeta: OFICIAL.</li>
 *   <li>Ingreso en USD cobrado en una cuenta en pesos: OFICIAL.</li>
 *   <li>Transferencias (comprar/vender dólares) y valuación del patrimonio: MEP.</li>
 * </ul>
 * Comprar dólares usa el precio de venta; vender dólares, el de compra. Siempre se puede pasar una cotización manual.
 *
 * <p>La descarga es diaria y al arrancar; además, si la última tiene más de 3 horas, se refresca al pedirla
 * (Cloud Run escala a cero y las tareas programadas pueden no correr).
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final Duration STALE = Duration.ofHours(3);
    private static final Duration RETRY = Duration.ofMinutes(10);

    /** Para qué se convierte: define el tipo de dólar por defecto. */
    public enum Purpose { PAYMENT, PAYMENT_CARD, RECEIPT, TRANSFER, VALUATION }

    /** Resultado de una conversión. {@code rate} y {@code type} son null si no hizo falta convertir. */
    public record Conversion(BigDecimal amount, BigDecimal rate, String type) {}

    private final ExchangeRateRepository repository;
    private final ExchangeProperties props;
    private final DolarApiProvider provider;
    private final Clock clock;
    private final AtomicReference<Instant> lastAttempt = new AtomicReference<>(Instant.EPOCH);
    private final TransactionTemplate newTx;

    public ExchangeRateService(ExchangeRateRepository repository, ExchangeProperties props, ObjectMapper json, Clock clock,
                               PlatformTransactionManager txManager) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
        // La descarga puede dispararse dentro de una consulta de solo lectura: se guarda en su propia transacción.
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.provider = new DolarApiProvider(new HttpJsonClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), json), props.dolarApiUrl(), clock);
    }

    public Map<ExchangeRateType, ExchangeRate> latest() {
        refreshIfStale();
        return repository.latest();
    }

    /**
     * Convierte {@code amount} (en {@code from}) a {@code to}. {@code manualRate}: pesos por dólar elegidos por el usuario.
     * Lanza {@code exchange.rate_required} si hace falta una cotización y no hay ninguna guardada.
     */
    public Conversion convert(BigDecimal amount, String from, String to, Purpose purpose, BigDecimal manualRate) {
        if (from.equals(to)) return new Conversion(amount, null, null);
        boolean buyingUsd = buyingUsd(from, to, purpose);
        BigDecimal rate;
        String type;
        if (manualRate != null) {
            if (manualRate.signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "exchange.invalid_rate");
            rate = manualRate;
            type = "MANUAL";
        } else {
            ExchangeRateType t = defaultType(purpose);
            ExchangeRate r = latest().get(t);
            BigDecimal value = r == null ? null : buyingUsd || r.buy() == null ? r.sell() : r.buy();
            if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "exchange.rate_required");
            rate = value;
            type = t.name();
        }
        BigDecimal converted = "USD".equals(from)
                ? amount.multiply(rate) : amount.divide(rate, 10, RoundingMode.HALF_UP);
        return new Conversion(converted.setScale(2, RoundingMode.HALF_UP), rate, type);
    }

    public static ExchangeRateType defaultType(Purpose purpose) {
        return switch (purpose) {
            case PAYMENT_CARD -> ExchangeRateType.TARJETA;
            case PAYMENT, RECEIPT -> ExchangeRateType.OFICIAL;
            case TRANSFER, VALUATION -> ExchangeRateType.MEP;
        };
    }

    /**
     * ¿La operación compra dólares? Pagar un gasto en USD con pesos o pasar pesos a una cuenta en USD: sí.
     * Cobrar un ingreso en USD en pesos o valuar dólares en pesos: no (se venden).
     */
    static boolean buyingUsd(String from, String to, Purpose purpose) {
        return switch (purpose) {
            case PAYMENT, PAYMENT_CARD -> "USD".equals(from); // from = moneda del gasto
            case RECEIPT, TRANSFER, VALUATION -> "ARS".equals(from);
        };
    }

    // ── descarga ─────────────────────────────────────────────────────────────

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (props.enabled() && props.syncOnStartup()) sync();
    }

    @Scheduled(cron = "0 25 7,13,19 * * *", zone = "America/Argentina/Buenos_Aires")
    public void scheduled() {
        if (props.enabled()) sync();
    }

    private void refreshIfStale() {
        if (!props.enabled()) return;
        Instant now = clock.instant();
        boolean stale = repository.lastFetch().map(t -> t.isBefore(now.minus(STALE))).orElse(true);
        Instant prev = lastAttempt.get();
        if (stale && prev.isBefore(now.minus(RETRY)) && lastAttempt.compareAndSet(prev, now)) sync();
    }

    /** @return cantidad de cotizaciones guardadas, -1 si falló. */
    public int sync() {
        try {
            var rates = provider.fetch();
            newTx.executeWithoutResult(s -> repository.upsert(rates));
            log.info("Exchange rates synced: {}", rates.size());
            return rates.size();
        } catch (RuntimeException e) {
            log.warn("Exchange rates not synced: {}", e.getMessage());
            return -1;
        }
    }
}
