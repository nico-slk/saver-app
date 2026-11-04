package com.saver.inflation;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Descarga la inflación oficial una vez por día (y al arrancar). Si una fuente falla se registra un
 * aviso y se siguen usando los datos guardados: la app funciona sin internet.
 */
@Service
public class InflationSyncService {

    private static final Logger log = LoggerFactory.getLogger(InflationSyncService.class);

    private final List<InflationProvider> providers;
    private final InflationRepository repository;
    private final InflationProperties props;
    private final Clock clock;

    public InflationSyncService(List<InflationProvider> providers, InflationRepository repository,
                                InflationProperties props, Clock clock) {
        this.providers = providers;
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (props.enabled() && props.syncOnStartup()) syncAll();
    }

    @Scheduled(cron = "0 20 7 * * *", zone = "America/Argentina/Buenos_Aires")
    public void daily() {
        if (props.enabled()) syncAll();
    }

    /** Sincroniza todas las fuentes. Devuelve, por moneda, las filas nuevas o cambiadas (-1 = falló). */
    public Map<String, Integer> syncAll() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (InflationProvider p : providers) result.put(p.currency(), sync(p));
        return result;
    }

    int sync(InflationProvider p) {
        try {
            List<MonthlyRate> rates = p.fetch();
            int changed = repository.upsert(p.currency(), p.source(), rates, clock.instant());
            log.info("Inflation {} synced from {}: {} months, {} new/changed", p.currency(), p.source(), rates.size(), changed);
            return changed;
        } catch (RuntimeException e) {
            log.warn("Inflation {} not synced ({}): {}", p.currency(), p.source(), e.getMessage());
            return -1;
        }
    }
}
