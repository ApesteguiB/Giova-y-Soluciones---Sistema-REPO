package pe.giova.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class PublicSiteRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PublicSiteRepository repository = new PublicSiteRepository(jdbc);

    /**
     * Los códigos de empresa llevan guion bajo (SOLUCIONES_TI) y los slugs
     * guion medio. Quien escribe ?empresa= no tiene por qué saber cuál es cuál.
     */
    @Test
    @SuppressWarnings("unchecked")
    void looksUpTheCompanyByBothTheCodeAndTheSlugSpelling() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findTenantIdByCodeOrSlug("soluciones-ti");

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertEquals("SOLUCIONES_TI", parameters.getValue().getValue("code"));
        assertEquals("soluciones-ti", parameters.getValue().getValue("slug"));
        assertTrue(sql.getValue().contains("t.code = :code OR t.slug = :slug"), sql.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theDomainLookupOnlyReturnsCompaniesWithAnActiveSite() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findTenantIdByHostname("solucionesti.pe");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("h.hostname = :hostname"), sql.getValue());
        assertTrue(sql.getValue().contains("t.active = TRUE"), sql.getValue());
        assertTrue(sql.getValue().contains("s.active = TRUE"), sql.getValue());
    }
}
