package pe.giova.api;

import java.util.Map;
import javax.sql.DataSource;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT 1")) {
            statement.executeQuery();
            return ResponseEntity.ok(Map.of("status", "READY", "database", "UP"));
        } catch (SQLException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "NOT_READY", "database", "DOWN"));
        }
    }
}
