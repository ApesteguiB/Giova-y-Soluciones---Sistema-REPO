package pe.giova.tender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.giova.tender.enums.TenderSyncTrigger;

@Component
@ConditionalOnProperty(name = "giova.oece.enabled", havingValue = "true")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TenderScheduler {
    private static final Logger log = LoggerFactory.getLogger(TenderScheduler.class);
    private final TenderService service;

    public TenderScheduler(TenderService service) {
        this.service = service;
    }

    @Scheduled(cron = "${giova.oece.sync-cron}")
    public void synchronize() {
        try {
            service.startSync(TenderSyncTrigger.SCHEDULED, null);
        } catch (RuntimeException exception) {
            log.info("Sincronización OECE omitida o rechazada: {}", exception.getMessage());
        }
    }
}
