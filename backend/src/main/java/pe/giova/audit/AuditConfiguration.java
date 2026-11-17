package pe.giova.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class AuditConfiguration {
    @Bean
    AuditEventRepository auditEventRepository(NamedParameterJdbcTemplate jdbc) { return new AuditEventRepository(jdbc); }
}
