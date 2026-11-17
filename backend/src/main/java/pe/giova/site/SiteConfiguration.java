package pe.giova.site;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.document.TenantLogoStorage;

@Configuration
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class SiteConfiguration {
    @Bean
    PublicSiteRepository publicSiteRepository(NamedParameterJdbcTemplate jdbc) {
        return new PublicSiteRepository(jdbc);
    }

    @Bean
    PublicSiteResolver publicSiteResolver(PublicSiteRepository repository,
                                          @Value("${giova.site.default-tenant-code:GIOVA}") String defaultTenantCode,
                                          @Value("${giova.site.allow-local-override:true}") boolean allowLocalOverride) {
        return new PublicSiteResolver(repository, defaultTenantCode, allowLocalOverride);
    }

    @Bean
    PublicSiteService publicSiteService(PublicSiteRepository repository, PublicSiteResolver resolver,
                                        TenantLogoStorage logoStorage) {
        return new PublicSiteService(repository, resolver, logoStorage);
    }
}
