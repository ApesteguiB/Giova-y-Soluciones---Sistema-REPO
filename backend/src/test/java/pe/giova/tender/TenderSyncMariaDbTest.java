package pe.giova.tender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pe.giova.tender.contract.OcdsReleasePackageParser;
import pe.giova.tender.enums.TenderSyncStatus;
import pe.giova.tender.enums.TenderSyncTrigger;

/** Opt-in: local MariaDB 10.4, synthetic provider and transactional rollback. */
@SpringBootTest(properties = {"giova.oece.enabled=true", "giova.oece.sync-cron=-"})
@EnabledIfEnvironmentVariable(named = "GIOVA_TEST_TENDER_DB", matches = "true")
@Transactional
class TenderSyncMariaDbTest {
    @Autowired TenderService service;
    @Autowired TenderRepository repository;
    @Autowired NamedParameterJdbcTemplate namedJdbc;
    @MockitoBean TenderProviderClient provider;
    @MockitoBean(name = "tenderSyncExecutor") ExecutorService executor;

    @Test
    void syncAndDuplicateUpdateSucceedForTwoTenantsWithoutLeakingMatches() {
        var jdbc = namedJdbc.getJdbcTemplate();
        assertEquals("giova_v1", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String fixture = "fixture-oece-" + UUID.randomUUID();
        for (long tenantId : new long[]{1L, 2L}) {
            jdbc.update("""
                    INSERT INTO tender_match_profiles(tenant_id,public_id,name,include_keywords,
                        exclude_keywords,buyers,regions,classification_codes,active)
                    VALUES (?, ?, ?, ?, '[]', '[]', '[]', '[]', TRUE)
                    """, tenantId, UUID.randomUUID().toString(), fixture, "[\"" + fixture + "\"]");
        }
        URI uri = URI.create("https://contratacionesabiertas.oece.gob.pe/api/v1/releases");
        when(provider.initialUri(any(), any())).thenReturn(uri);
        String json = """
                {"uri":"https://contratacionesabiertas.oece.gob.pe/api/v1/releases",
                 "version":"1.1","publisher":{"name":"Fixture"},"publishedDate":"%s",
                 "releases":[{"ocid":"%s","id":"%s","date":"%s","tag":["tender"],"initiationType":"tender",
                   "tender":{"title":"%s","status":"active"}}]}
                """.formatted(Instant.now(), fixture, fixture, Instant.now(), fixture);
        var page = new TenderProviderClient.ProviderPage(new OcdsReleasePackageParser().parse(json), null);
        when(provider.fetch(uri)).thenReturn(page);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        });
        for (int version = 0; version < 2; version++) {
            var accepted = service.startSync(TenderSyncTrigger.MANUAL, null);
            var run = repository.findRun(accepted.runId()).orElseThrow();
            assertEquals(TenderSyncStatus.SUCCEEDED, run.status());
            assertEquals(2, run.matches());
            assertEquals(2L, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM tender_matches m
                    JOIN tender_match_profiles mp ON mp.id=m.profile_id AND mp.tenant_id=m.tenant_id
                    WHERE mp.name=? AND m.version=?
                    """, Long.class, fixture, version));
        }
    }
}
