package pe.giova.tender;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.giova.auth.TenantScope;
import pe.giova.tender.contract.TenderJsonCodec;
import pe.giova.tender.enums.TenderMatchState;
import pe.giova.tender.enums.TenderSyncStatus;
import pe.giova.tender.enums.TenderSyncTrigger;

public final class TenderRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final TenderJsonCodec jsonCodec;

    public TenderRepository(NamedParameterJdbcTemplate jdbc, TenderJsonCodec jsonCodec) {
        this.jdbc = jdbc;
        this.jsonCodec = jsonCodec;
    }

    public Optional<SourceRecord> findSource(String code) {
        return jdbc.query("""
                SELECT id, public_id, code, base_url, enabled, last_successful_at, cursor_value, version
                FROM tender_sources WHERE code=:code
                """, new MapSqlParameterSource("code", code), (rs, row) -> mapSource(rs)).stream().findFirst();
    }

    public void updateSourceUrl(long sourceId, String baseUrl) {
        jdbc.update("UPDATE tender_sources SET base_url=:baseUrl, version=version+1 WHERE id=:sourceId",
                new MapSqlParameterSource().addValue("baseUrl", baseUrl).addValue("sourceId", sourceId));
    }

    public void markSourceSuccessful(long sourceId, Instant successfulAt, String cursor) {
        jdbc.update("""
                UPDATE tender_sources
                SET last_successful_at=:successfulAt, cursor_value=:cursor, version=version+1
                WHERE id=:sourceId
                """, new MapSqlParameterSource().addValue("sourceId", sourceId)
                .addValue("successfulAt", timestamp(successfulAt)).addValue("cursor", cursor));
    }

    public void insertRun(String publicId, long sourceId, TenderSyncTrigger trigger, Long requestedBy,
                          String cursorBefore) {
        jdbc.update("""
                INSERT INTO tender_sync_runs(public_id,source_id,trigger_type,requested_by_worker_id,cursor_before)
                VALUES (:publicId,:sourceId,:triggerType,:requestedBy,:cursorBefore)
                """, new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("sourceId", sourceId).addValue("triggerType", trigger.name())
                .addValue("requestedBy", requestedBy).addValue("cursorBefore", cursorBefore));
    }

    public void markRunSucceeded(String publicId, String cursorAfter, int pages, int releases,
                                 int upserts, int matches) {
        jdbc.update("""
                UPDATE tender_sync_runs
                SET status='SUCCEEDED', finished_at=CURRENT_TIMESTAMP(6), cursor_after=:cursorAfter,
                    pages_count=:pages, releases_count=:releases, upserts_count=:upserts,
                    matches_count=:matches, error_code=NULL, error_message=NULL, version=version+1
                WHERE public_id=:publicId AND status='RUNNING'
                """, new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("cursorAfter", cursorAfter).addValue("pages", pages)
                .addValue("releases", releases).addValue("upserts", upserts).addValue("matches", matches));
    }

    public void markRunFailed(String publicId, String errorCode, String errorMessage, int pages, int releases,
                              int upserts, int matches) {
        String safeMessage = errorMessage == null ? "La sincronización falló" : errorMessage;
        jdbc.update("""
                UPDATE tender_sync_runs
                SET status='FAILED', finished_at=CURRENT_TIMESTAMP(6), pages_count=:pages,
                    releases_count=:releases, upserts_count=:upserts, matches_count=:matches,
                    error_code=:errorCode, error_message=:errorMessage, version=version+1
                WHERE public_id=:publicId AND status='RUNNING'
                """, new MapSqlParameterSource().addValue("publicId", publicId)
                .addValue("errorCode", errorCode).addValue("errorMessage", safeMessage.substring(0, Math.min(500, safeMessage.length())))
                .addValue("pages", pages).addValue("releases", releases)
                .addValue("upserts", upserts).addValue("matches", matches));
    }

    public Optional<SyncRunRecord> findRun(String publicId) {
        return jdbc.query("""
                SELECT r.public_id, s.code, r.trigger_type, r.requested_by_worker_id, r.started_at,
                       r.finished_at, r.status, r.cursor_before, r.cursor_after, r.pages_count,
                       r.releases_count, r.upserts_count, r.matches_count, r.error_code, r.error_message
                FROM tender_sync_runs r JOIN tender_sources s ON s.id=r.source_id
                WHERE r.public_id=:publicId
                """, new MapSqlParameterSource("publicId", publicId), (rs, row) -> mapRun(rs)).stream().findFirst();
    }

    public Optional<SyncRunRecord> latestRun(long sourceId) {
        return jdbc.query("""
                SELECT r.public_id, s.code, r.trigger_type, r.requested_by_worker_id, r.started_at,
                       r.finished_at, r.status, r.cursor_before, r.cursor_after, r.pages_count,
                       r.releases_count, r.upserts_count, r.matches_count, r.error_code, r.error_message
                FROM tender_sync_runs r JOIN tender_sources s ON s.id=r.source_id
                WHERE r.source_id=:sourceId ORDER BY r.started_at DESC, r.id DESC LIMIT 1
                """, new MapSqlParameterSource("sourceId", sourceId), (rs, row) -> mapRun(rs)).stream().findFirst();
    }

    public void upsertRelease(long sourceId, ReleaseInput input) {
        jdbc.update("""
                INSERT INTO tender_releases(public_id,source_id,release_id,ocid,published_date,release_tag,
                    source_url,source_hash,raw_json)
                VALUES (:publicId,:sourceId,:releaseId,:ocid,:publishedDate,:releaseTag,
                    :sourceUrl,:sourceHash,:rawJson)
                ON DUPLICATE KEY UPDATE
                    ocid=VALUES(ocid), published_date=VALUES(published_date), release_tag=VALUES(release_tag),
                    source_url=VALUES(source_url), source_hash=VALUES(source_hash), raw_json=VALUES(raw_json),
                    last_seen_at=CURRENT_TIMESTAMP(6), version=version+1
                """, new MapSqlParameterSource().addValue("publicId", UUID.randomUUID().toString())
                .addValue("sourceId", sourceId).addValue("releaseId", input.releaseId())
                .addValue("ocid", input.ocid()).addValue("publishedDate", timestamp(input.publishedDate()))
                .addValue("releaseTag", input.releaseTag()).addValue("sourceUrl", input.sourceUrl())
                .addValue("sourceHash", input.sourceHash()).addValue("rawJson", input.rawJson()));
    }

    public long upsertProcess(long sourceId, ProcessInput input) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("publicId", UUID.randomUUID().toString()).addValue("sourceId", sourceId)
                .addValue("ocid", input.ocid()).addValue("title", input.title())
                .addValue("description", input.description()).addValue("buyerId", input.buyerId())
                .addValue("buyerName", input.buyerName()).addValue("region", input.region())
                .addValue("classificationCodes", jsonCodec.writeStrings(input.classificationCodes()))
                .addValue("procurementMethod", input.procurementMethod()).addValue("status", input.status())
                .addValue("currencyCode", input.currencyCode()).addValue("amount", input.amount())
                .addValue("tenderStart", timestamp(input.tenderStart())).addValue("tenderEnd", timestamp(input.tenderEnd()))
                .addValue("officialUrl", input.officialUrl()).addValue("latestReleaseDate", timestamp(input.latestReleaseDate()))
                .addValue("sourceHash", input.sourceHash());
        jdbc.update("""
                INSERT INTO tender_processes(public_id,source_id,ocid,title,description,buyer_id,buyer_name,region,
                    classification_codes,procurement_method,status,currency_code,amount,tender_start,tender_end,
                    official_url,latest_release_date,source_hash)
                VALUES (:publicId,:sourceId,:ocid,:title,:description,:buyerId,:buyerName,:region,
                    :classificationCodes,:procurementMethod,:status,:currencyCode,:amount,:tenderStart,:tenderEnd,
                    :officialUrl,:latestReleaseDate,:sourceHash)
                ON DUPLICATE KEY UPDATE
                    title=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(title), title),
                    description=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(description), description),
                    buyer_id=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(buyer_id), buyer_id),
                    buyer_name=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(buyer_name), buyer_name),
                    region=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(region), region),
                    classification_codes=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(classification_codes), classification_codes),
                    procurement_method=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(procurement_method), procurement_method),
                    status=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(status), status),
                    currency_code=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(currency_code), currency_code),
                    amount=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(amount), amount),
                    tender_start=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(tender_start), tender_start),
                    tender_end=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(tender_end), tender_end),
                    official_url=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(official_url), official_url),
                    latest_release_date=GREATEST(latest_release_date, VALUES(latest_release_date)),
                    source_hash=IF(VALUES(latest_release_date) >= latest_release_date, VALUES(source_hash), source_hash)
                """, params);
        return jdbc.queryForObject("SELECT id FROM tender_processes WHERE source_id=:sourceId AND ocid=:ocid",
                new MapSqlParameterSource().addValue("sourceId", sourceId).addValue("ocid", input.ocid()), Long.class);
    }

    public void upsertMatch(long processId, long profileId, int score, String reasons) {
        upsertMatchForTenant(processId, profileId, TenantScope.requireTenantId(), score, reasons);
    }

    public void upsertMatchForTenant(long processId, long profileId, long tenantId, int score, String reasons) {
        int rows = jdbc.update("""
                INSERT INTO tender_matches(tenant_id,public_id,process_id,profile_id,score,reasons)
                SELECT :tenantId,:publicId,:processId,:profileId,:score,:reasons
                FROM tender_match_profiles mp WHERE mp.id=:profileId AND mp.tenant_id=:tenantId
                ON DUPLICATE KEY UPDATE score=VALUES(score), reasons=VALUES(reasons),
                    matched_at=CURRENT_TIMESTAMP(6), version=tender_matches.version+1
                """, new MapSqlParameterSource().addValue("publicId", UUID.randomUUID().toString())
                .addValue("tenantId", tenantId)
                .addValue("processId", processId).addValue("profileId", profileId)
                .addValue("score", score).addValue("reasons", reasons));
        if (rows == 0) {
            throw new IllegalStateException("El perfil de coincidencia no pertenece a la empresa indicada");
        }
    }

    /**
     * Retira una coincidencia que dejó de cumplir el perfil. Solo elimina las que nadie
     * tocó: si un trabajador la revisó, siguió, descartó o cerró, el histórico se conserva.
     */
    public void deleteUntouchedMatch(long processId, long profileId) {
        deleteUntouchedMatchForTenant(processId, profileId, TenantScope.requireTenantId());
    }

    public void deleteUntouchedMatchForTenant(long processId, long profileId, long tenantId) {
        jdbc.update("""
                DELETE FROM tender_matches
                WHERE tenant_id=:tenantId AND process_id=:processId AND profile_id=:profileId AND state='NEW'
                  AND EXISTS (SELECT 1 FROM tender_match_profiles mp
                              WHERE mp.id=tender_matches.profile_id AND mp.tenant_id=tender_matches.tenant_id)
                  AND NOT EXISTS (SELECT 1 FROM tender_match_actions a
                                  WHERE a.match_id=tender_matches.id AND a.tenant_id=tender_matches.tenant_id)
                """, new MapSqlParameterSource().addValue("processId", processId)
                .addValue("profileId", profileId).addValue("tenantId", tenantId));
    }

    public Optional<ProcessRecord> findProcess(String publicId) {
        return jdbc.query(processSql() + " WHERE p.public_id=:publicId",
                new MapSqlParameterSource("publicId", publicId), (rs, row) -> mapProcess(rs)).stream().findFirst();
    }

    public List<ProcessRecord> findProcesses() {
        return jdbc.query(processSql() + " ORDER BY p.id", new MapSqlParameterSource(), (rs, row) -> mapProcess(rs));
    }

    public Optional<ProfileRecord> findProfile(String publicId) {
        return jdbc.query(profileSql() + " WHERE mp.public_id=:publicId AND mp.tenant_id=:tenantId",
                TenantScope.withTenant(new MapSqlParameterSource("publicId", publicId)),
                (rs, row) -> mapProfile(rs)).stream().findFirst();
    }

    public List<ProfileRecord> findProfiles(boolean activeOnly) {
        String active = activeOnly ? " AND mp.active=TRUE" : "";
        return jdbc.query(profileSql() + " WHERE mp.tenant_id=:tenantId" + active + " ORDER BY mp.name, mp.id",
                TenantScope.withTenant(new MapSqlParameterSource()),
                (rs, row) -> mapProfile(rs));
    }

    /** All active profiles for the global synchronization worker; never used by request endpoints. */
    public List<ProfileRecord> findProfilesForMatching() {
        return jdbc.query(profileSql() + " WHERE mp.active=TRUE ORDER BY mp.tenant_id, mp.name, mp.id",
                new MapSqlParameterSource(), (rs, row) -> mapProfile(rs));
    }

    public String insertProfile(ProfileInput input) {
        String publicId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO tender_match_profiles(tenant_id,public_id,name,include_keywords,exclude_keywords,buyers,regions,
                    classification_codes,min_amount,max_amount,closing_from,closing_to,active)
                VALUES (:tenantId,:publicId,:name,:includeKeywords,:excludeKeywords,:buyers,:regions,
                    :classificationCodes,:minAmount,:maxAmount,:closingFrom,:closingTo,:active)
                """, profileParams(publicId, input).addValue("tenantId", TenantScope.requireTenantId()));
        return publicId;
    }

    public int updateProfile(String publicId, long version, ProfileInput input) {
        return jdbc.update("""
                UPDATE tender_match_profiles
                SET name=:name, include_keywords=:includeKeywords, exclude_keywords=:excludeKeywords,
                    buyers=:buyers, regions=:regions, classification_codes=:classificationCodes,
                    min_amount=:minAmount, max_amount=:maxAmount, closing_from=:closingFrom,
                    closing_to=:closingTo, active=:active, version=version+1
                WHERE public_id=:publicId AND tenant_id=:tenantId AND version=:version
                """, profileParams(publicId, input).addValue("tenantId", TenantScope.requireTenantId())
                .addValue("version", version));
    }

    public PageData findMatches(String state, String query, String buyer, String region, LocalDate from,
                                LocalDate to, Integer minScore, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("state", state).addValue("pattern", pattern(query))
                .addValue("buyerPattern", pattern(buyer)).addValue("regionPattern", pattern(region))
                .addValue("fromAt", from == null ? null : Timestamp.valueOf(from.atStartOfDay()))
                .addValue("toExclusive", to == null ? null : Timestamp.valueOf(to.plusDays(1).atStartOfDay()))
                .addValue("minScore", minScore).addValue("offset", (long) page * size).addValue("pageSize", size);
        String where = matchWhere();
        List<MatchRecord> items = jdbc.query(matchSql() + where
                        + " ORDER BY m.score DESC, m.matched_at DESC, m.id DESC LIMIT :pageSize OFFSET :offset",
                TenantScope.withTenant(params), (rs, row) -> mapMatch(rs));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tender_matches m JOIN tender_processes p ON p.id=m.process_id"
                        + " JOIN tender_match_profiles mp ON mp.id=m.profile_id AND mp.tenant_id=m.tenant_id " + where,
                params, Long.class);
        int totalPages = total == null || total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageData(items, total == null ? 0 : total, page, size, totalPages);
    }

    public List<MatchRecord> findMatchesForProcess(long processId) {
        return jdbc.query(matchSql() + " WHERE m.tenant_id=:tenantId AND m.process_id=:processId ORDER BY m.score DESC, m.id",
                TenantScope.withTenant(new MapSqlParameterSource("processId", processId)),
                (rs, row) -> mapMatch(rs));
    }

    public Optional<MatchRecord> findMatch(String processPublicId, String profilePublicId) {
        String suffix = profilePublicId == null
                ? " WHERE m.tenant_id=:tenantId AND p.public_id=:processPublicId ORDER BY m.score DESC LIMIT 1"
                : " WHERE m.tenant_id=:tenantId AND p.public_id=:processPublicId AND mp.public_id=:profilePublicId";
        MapSqlParameterSource params = new MapSqlParameterSource("processPublicId", processPublicId);
        if (profilePublicId != null) params.addValue("profilePublicId", profilePublicId);
        params = TenantScope.withTenant(params);
        return jdbc.query(matchSql() + suffix, params, (rs, row) -> mapMatch(rs)).stream().findFirst();
    }

    public List<ReleaseRecord> findReleasesForProcess(long processId) {
        return jdbc.query("""
                SELECT r.public_id, r.release_id, r.ocid, r.published_date, r.release_tag,
                       r.source_url, r.source_hash, r.first_seen_at, r.last_seen_at
                FROM tender_releases r JOIN tender_processes p ON p.source_id=r.source_id AND p.ocid=r.ocid
                WHERE p.id=:processId ORDER BY r.published_date DESC, r.id DESC
                """, new MapSqlParameterSource("processId", processId), (rs, row) -> new ReleaseRecord(
                rs.getString("public_id"), rs.getString("release_id"), rs.getString("ocid"),
                instant(rs, "published_date"), rs.getString("release_tag"), rs.getString("source_url"),
                rs.getString("source_hash"), instant(rs, "first_seen_at"), instant(rs, "last_seen_at")));
    }

    public int updateMatchState(long matchId, long version, TenderMatchState state, Long ownerWorkerId) {
        return jdbc.update("""
                UPDATE tender_matches
                SET state=:state, owner_worker_id=COALESCE(:ownerWorkerId, owner_worker_id), version=version+1
                WHERE id=:matchId AND tenant_id=:tenantId AND version=:version
                """, TenantScope.withTenant(new MapSqlParameterSource().addValue("state", state.name())
                .addValue("ownerWorkerId", ownerWorkerId).addValue("matchId", matchId).addValue("version", version)));
    }

    public void appendMatchAction(long matchId, long actorWorkerId, String action, String note) {
        jdbc.update("""
                INSERT INTO tender_match_actions(tenant_id,action_id,match_id,actor_worker_id,action_code,note)
                SELECT :tenantId,:actionId,:matchId,:actorId,:actionCode,:note
                FROM tender_matches m WHERE m.id=:matchId AND m.tenant_id=:tenantId
                """, new MapSqlParameterSource().addValue("actionId", UUID.randomUUID().toString())
                .addValue("tenantId", TenantScope.requireTenantId())
                .addValue("matchId", matchId).addValue("actorId", actorWorkerId)
                .addValue("actionCode", action).addValue("note", note));
    }

    private MapSqlParameterSource profileParams(String publicId, ProfileInput input) {
        return new MapSqlParameterSource().addValue("publicId", publicId).addValue("name", input.name())
                .addValue("includeKeywords", jsonCodec.writeStrings(input.includeKeywords()))
                .addValue("excludeKeywords", jsonCodec.writeStrings(input.excludeKeywords()))
                .addValue("buyers", jsonCodec.writeStrings(input.buyers())).addValue("regions", jsonCodec.writeStrings(input.regions()))
                .addValue("classificationCodes", jsonCodec.writeStrings(input.classificationCodes()))
                .addValue("minAmount", input.minAmount()).addValue("maxAmount", input.maxAmount())
                .addValue("closingFrom", input.closingFrom() == null ? null : Date.valueOf(input.closingFrom()))
                .addValue("closingTo", input.closingTo() == null ? null : Date.valueOf(input.closingTo()))
                .addValue("active", input.active());
    }

    private String processSql() {
        return """
                SELECT p.id, p.public_id, p.source_id, p.ocid, p.title, p.description, p.buyer_id,
                       p.buyer_name, p.region, p.classification_codes, p.procurement_method, p.status,
                       p.currency_code, p.amount, p.tender_start, p.tender_end, p.official_url,
                       p.latest_release_date, p.source_hash, p.version
                FROM tender_processes p
                """;
    }

    private String profileSql() {
        return """
                SELECT mp.id, mp.tenant_id, mp.public_id, mp.name, mp.include_keywords, mp.exclude_keywords, mp.buyers,
                       mp.regions, mp.classification_codes, mp.min_amount, mp.max_amount, mp.closing_from,
                       mp.closing_to, mp.active, mp.version
                FROM tender_match_profiles mp
                """;
    }

    private String matchSql() {
        return """
                SELECT m.id, m.public_id AS match_public_id, m.process_id, p.public_id AS process_public_id,
                       p.ocid, p.title, p.description, p.buyer_id, p.buyer_name, p.region,
                       p.classification_codes, p.procurement_method, p.status, p.currency_code, p.amount,
                       p.tender_start, p.tender_end, p.official_url, p.latest_release_date, p.source_hash,
                       mp.id AS profile_id, mp.public_id AS profile_public_id, mp.name AS profile_name,
                       m.owner_worker_id, m.score, m.reasons, m.state, m.matched_at, m.version
                FROM tender_matches m
                JOIN tender_processes p ON p.id=m.process_id
                JOIN tender_match_profiles mp ON mp.id=m.profile_id AND mp.tenant_id=m.tenant_id
                """;
    }

    private String matchWhere() {
        return """
                WHERE mp.active=TRUE
                  AND m.tenant_id=:tenantId
                  AND (:state IS NULL OR m.state=:state)
                  AND (:pattern IS NULL OR LOWER(CONCAT_WS(' ',p.ocid,p.title,p.description,p.buyer_name,p.region,mp.name)) LIKE :pattern ESCAPE '\\\\')
                  AND (:buyerPattern IS NULL OR LOWER(CONCAT_WS(' ',p.buyer_id,p.buyer_name)) LIKE :buyerPattern ESCAPE '\\\\')
                  AND (:regionPattern IS NULL OR LOWER(p.region) LIKE :regionPattern ESCAPE '\\\\')
                  AND (:fromAt IS NULL OR p.tender_end IS NULL OR p.tender_end >= :fromAt)
                  AND (:toExclusive IS NULL OR p.tender_end IS NULL OR p.tender_end < :toExclusive)
                  AND (:minScore IS NULL OR m.score >= :minScore)
                """;
    }

    private MatchRecord mapMatch(ResultSet rs) throws SQLException {
        long ownerId = rs.getLong("owner_worker_id");
        boolean ownerWasNull = rs.wasNull();
        return new MatchRecord(rs.getLong("id"), rs.getString("match_public_id"), rs.getLong("process_id"),
                rs.getString("process_public_id"), rs.getString("ocid"), rs.getString("title"),
                rs.getString("description"), rs.getString("buyer_id"), rs.getString("buyer_name"),
                rs.getString("region"), jsonCodec.readStrings(rs.getString("classification_codes")),
                rs.getString("procurement_method"), rs.getString("status"), rs.getString("currency_code"),
                rs.getBigDecimal("amount"), instant(rs, "tender_start"), instant(rs, "tender_end"),
                rs.getString("official_url"), instant(rs, "latest_release_date"), rs.getString("source_hash"),
                rs.getLong("profile_id"), rs.getString("profile_public_id"), rs.getString("profile_name"),
                ownerWasNull ? null : ownerId, rs.getInt("score"), jsonCodec.readStrings(rs.getString("reasons")),
                TenderMatchState.valueOf(rs.getString("state")), instant(rs, "matched_at"), rs.getLong("version"));
    }

    private ProcessRecord mapProcess(ResultSet rs) throws SQLException {
        return new ProcessRecord(rs.getLong("id"), rs.getString("public_id"), rs.getLong("source_id"),
                rs.getString("ocid"), rs.getString("title"), rs.getString("description"), rs.getString("buyer_id"),
                rs.getString("buyer_name"), rs.getString("region"), jsonCodec.readStrings(rs.getString("classification_codes")),
                rs.getString("procurement_method"), rs.getString("status"), rs.getString("currency_code"),
                rs.getBigDecimal("amount"), instant(rs, "tender_start"), instant(rs, "tender_end"),
                rs.getString("official_url"), instant(rs, "latest_release_date"), rs.getString("source_hash"),
                rs.getLong("version"));
    }

    private ProfileRecord mapProfile(ResultSet rs) throws SQLException {
        return new ProfileRecord(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("public_id"), rs.getString("name"),
                jsonCodec.readStrings(rs.getString("include_keywords")), jsonCodec.readStrings(rs.getString("exclude_keywords")),
                jsonCodec.readStrings(rs.getString("buyers")), jsonCodec.readStrings(rs.getString("regions")),
                jsonCodec.readStrings(rs.getString("classification_codes")), rs.getBigDecimal("min_amount"),
                rs.getBigDecimal("max_amount"), localDate(rs, "closing_from"), localDate(rs, "closing_to"),
                rs.getBoolean("active"), rs.getLong("version"));
    }

    private static SourceRecord mapSource(ResultSet rs) throws SQLException {
        return new SourceRecord(rs.getLong("id"), rs.getString("public_id"), rs.getString("code"),
                rs.getString("base_url"), rs.getBoolean("enabled"), instant(rs, "last_successful_at"),
                rs.getString("cursor_value"), rs.getLong("version"));
    }

    private static SyncRunRecord mapRun(ResultSet rs) throws SQLException {
        return new SyncRunRecord(rs.getString("public_id"), rs.getString("code"),
                TenderSyncTrigger.valueOf(rs.getString("trigger_type")), rs.getObject("requested_by_worker_id", Long.class),
                instant(rs, "started_at"), instant(rs, "finished_at"), TenderSyncStatus.valueOf(rs.getString("status")),
                rs.getString("cursor_before"), rs.getString("cursor_after"), rs.getInt("pages_count"),
                rs.getInt("releases_count"), rs.getInt("upserts_count"), rs.getInt("matches_count"),
                rs.getString("error_code"), rs.getString("error_message"));
    }

    private static String pattern(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    public record SourceRecord(long id, String publicId, String code, String baseUrl, boolean enabled,
                               Instant lastSuccessfulAt, String cursorValue, long version) {}

    public record SyncRunRecord(String publicId, String sourceCode, TenderSyncTrigger trigger, Long requestedBy,
                                Instant startedAt, Instant finishedAt, TenderSyncStatus status, String cursorBefore,
                                String cursorAfter, int pages, int releases, int upserts, int matches,
                                String errorCode, String errorMessage) {}

    public record ReleaseInput(String releaseId, String ocid, Instant publishedDate, String releaseTag,
                               String sourceUrl, String sourceHash, String rawJson) {}

    public record ProcessInput(String ocid, String title, String description, String buyerId, String buyerName,
                               String region, List<String> classificationCodes, String procurementMethod,
                               String status, String currencyCode, BigDecimal amount, Instant tenderStart,
                               Instant tenderEnd, String officialUrl, Instant latestReleaseDate, String sourceHash) {
        public TenderMatchEngine.Candidate candidate() {
            return new TenderMatchEngine.Candidate(title, description, buyerId, buyerName, region,
                    classificationCodes, amount, tenderEnd);
        }
    }

    public record ProfileInput(String name, List<String> includeKeywords, List<String> excludeKeywords,
                               List<String> buyers, List<String> regions, List<String> classificationCodes,
                               BigDecimal minAmount, BigDecimal maxAmount, LocalDate closingFrom,
                               LocalDate closingTo, boolean active) {}

    public record ProcessRecord(long id, String publicId, long sourceId, String ocid, String title, String description,
                                String buyerId, String buyerName, String region, List<String> classificationCodes,
                                String procurementMethod, String status, String currencyCode, BigDecimal amount,
                                Instant tenderStart, Instant tenderEnd, String officialUrl, Instant latestReleaseDate,
                                String sourceHash, long version) {
        public TenderMatchEngine.Candidate candidate() {
            return new TenderMatchEngine.Candidate(title, description, buyerId, buyerName, region,
                    classificationCodes, amount, tenderEnd);
        }
    }

    public record ProfileRecord(long id, long tenantId, String publicId, String name, List<String> includeKeywords,
                                List<String> excludeKeywords, List<String> buyers, List<String> regions,
                                List<String> classificationCodes, BigDecimal minAmount, BigDecimal maxAmount,
                                LocalDate closingFrom, LocalDate closingTo, boolean active, long version) {
        public TenderMatchEngine.Profile engineProfile() {
            return new TenderMatchEngine.Profile(includeKeywords, excludeKeywords, buyers, regions,
                    classificationCodes, minAmount, maxAmount, closingFrom, closingTo);
        }
    }

    public record MatchRecord(long id, String publicId, long processId, String processPublicId, String ocid,
                              String title, String description, String buyerId, String buyerName, String region,
                              List<String> classificationCodes, String procurementMethod, String status,
                              String currencyCode, BigDecimal amount, Instant tenderStart, Instant tenderEnd,
                              String officialUrl, Instant latestReleaseDate, String sourceHash, long profileId,
                              String profilePublicId, String profileName, Long ownerWorkerId, int score,
                              List<String> reasons, TenderMatchState state, Instant matchedAt, long version) {}

    public record ReleaseRecord(String publicId, String releaseId, String ocid, Instant publishedDate,
                                String releaseTag, String sourceUrl, String sourceHash, Instant firstSeenAt,
                                Instant lastSeenAt) {}

    public record PageData(List<MatchRecord> items, long totalItems, int page, int size, int totalPages) {}
}
