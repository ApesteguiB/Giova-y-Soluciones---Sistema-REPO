package pe.giova.tenant;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.document.TenantLogoStorage;

@Configuration
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class TenantConfiguration {
    @Bean
    TenantBrandingRepository tenantBrandingRepository(NamedParameterJdbcTemplate jdbc) {
        return new TenantBrandingRepository(jdbc);
    }

    @Bean
    TenantLogoStorage tenantLogoStorage(@Value("${giova.documents.root:storage/documents}") String root) {
        return new TenantLogoStorage(Path.of(root));
    }
}
