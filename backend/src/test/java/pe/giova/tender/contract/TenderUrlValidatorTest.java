package pe.giova.tender.contract;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import org.junit.jupiter.api.Test;

class TenderUrlValidatorTest {
    @Test
    void rejectsCredentialsInOfficialUrls() {
        TenderUrlValidator validator = new TenderUrlValidator(URI.create("https://oece.example/api"));

        assertNull(validator.officialUrlOrNull("https://user:secret@oece.example/tender/1"));
    }
}
