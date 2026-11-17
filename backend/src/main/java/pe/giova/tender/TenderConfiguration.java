package pe.giova.tender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;
import pe.giova.tender.config.OeceProperties;
import pe.giova.tender.contract.OcdsReleasePackageParser;
import pe.giova.tender.contract.TenderJsonCodec;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
@EnableConfigurationProperties(OeceProperties.class)
public class TenderConfiguration {
    @Bean
    OcdsReleasePackageParser ocdsReleasePackageParser() {
        return new OcdsReleasePackageParser();
    }

    @Bean
    TenderJsonCodec tenderJsonCodec(JsonMapper objectMapper) {
        return new TenderJsonCodec(objectMapper);
    }

    @Bean
    TenderMatchEngine tenderMatchEngine() {
        return new TenderMatchEngine();
    }

    @Bean
    TenderProviderClient tenderProviderClient(OeceProperties properties, OcdsReleasePackageParser parser) {
        return new OeceOcdsClient(properties, parser);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService tenderSyncExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "giova-tender-sync"));
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
@ConditionalOnProperty(name = "giova.oece.enabled", havingValue = "true")
@EnableScheduling
class TenderSchedulingConfiguration {
}
