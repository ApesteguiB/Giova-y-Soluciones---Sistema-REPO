package pe.giova.document;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class DocumentConfiguration {
    @Bean
    DocumentRepository documentRepository(NamedParameterJdbcTemplate jdbc) {
        return new DocumentRepository(jdbc);
    }

    @Bean
    DocumentValidator documentValidator(@Value("${giova.documents.max-bytes:10485760}") long maxBytes) {
        return new DocumentValidator(maxBytes);
    }

    @Bean
    DocumentStorageService documentStorageService(DocumentRepository repository, DocumentValidator validator,
                                                  @Value("${giova.documents.root:storage/documents}") String root) {
        return new DocumentStorageService(repository, validator, Path.of(root));
    }
}
