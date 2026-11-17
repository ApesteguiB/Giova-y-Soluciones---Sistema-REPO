package pe.giova.tenant;

public final class TenantBrandingDtos {
    private TenantBrandingDtos() {}

    public record Response(long tenantId, String displayName, String primaryColor, String secondaryColor,
                           String accentColor, String pdfHeaderText, boolean hasLogo) {}
}
