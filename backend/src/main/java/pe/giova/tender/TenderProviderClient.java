package pe.giova.tender;

import java.net.URI;
import pe.giova.tender.contract.OcdsReleasePackageParser;

public interface TenderProviderClient {
    URI initialUri(String cursor, java.time.Instant lastSuccessfulAt);

    ProviderPage fetch(URI uri);

    record ProviderPage(OcdsReleasePackageParser.ReleasePackage releasePackage, URI nextUri) {}
}
