package pe.giova.submissions;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.site.PublicSiteResolver;

@Configuration
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class SubmissionConfiguration {
    @Bean
    ContactRepository contactRepository(NamedParameterJdbcTemplate jdbc, PublicSiteResolver siteResolver) {
        return new ContactRepository(jdbc, siteResolver);
    }

    @Bean
    ClaimRepository claimRepository(NamedParameterJdbcTemplate jdbc, PublicSiteResolver siteResolver) {
        return new ClaimRepository(jdbc, siteResolver);
    }

    @Bean
    SubmissionRateLimiter submissionRateLimiter() {
        return new SubmissionRateLimiter();
    }
}
