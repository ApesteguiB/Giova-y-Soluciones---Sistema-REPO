package pe.giova.site;

/** Datos públicos del sitio de una empresa. No expone identificadores internos. */
public final class PublicSiteDtos {
    private PublicSiteDtos() {}

    public record Colors(String primary, String secondary, String accent) {}

    public record Site(String tenantCode, String templateKey, String siteTitle, String brandName,
                       String legalName, String taxId, String tagline, String logoAsset, boolean uploadedLogo,
                       String monogramText, String phone, String whatsappNumber, String whatsappMessage,
                       String email, String address, String mapsUrl, String hours, String footerNote,
                       String facebookUrl, String instagramUrl, String linkedinUrl,
                       boolean claimsEnabled, boolean catalogEnabled, Colors colors) {}
}
