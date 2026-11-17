package pe.giova.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.admin.ConflictException;
import pe.giova.admin.NotFoundException;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.customer.api.CustomerDtos.CustomerInput;
import pe.giova.customer.api.CustomerDtos.CustomerPage;
import pe.giova.customer.api.CustomerDtos.CustomerUpdate;
import pe.giova.customer.api.CustomerDtos.CustomerView;

class CustomerServiceTest {
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(repository);
    private final WorkerPrincipal seller = principal(8L, RoleCode.SELLER);
    private final WorkerPrincipal admin = principal(2L, RoleCode.ADMIN);

    @Test
    void sellerCreateNormalizesEmailAndOwnsTheCustomer() {
        CustomerInput input = new CustomerInput("person", "dni", "12345678", "  Cliente  ", " Contacto ",
                " CLIENTE@EXAMPLE.COM ", " 999 ", " Lima ", " Nota ");
        CustomerView created = view("customer-1", 8L, true, 0L);
        when(repository.existsDocument("DNI", "12345678", null)).thenReturn(false);
        when(repository.insert(eq(8L), any(CustomerInput.class))).thenReturn("customer-1");
        when(repository.findByPublicId("customer-1", 8L)).thenReturn(Optional.of(created));

        CustomerView result = service.create(input, seller);

        assertEquals("customer-1", result.publicId());
        verify(repository).insert(eq(8L), eq(new CustomerInput("PERSON", "DNI", "12345678", "Cliente", "Contacto",
                "cliente@example.com", "999", "Lima", "Nota")));
    }

    @Test
    void rejectsDuplicateDocumentWithStableCode() {
        when(repository.existsDocument("RUC", "20123456789", null)).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.create(new CustomerInput("COMPANY", "RUC", "20123456789", "Empresa", null, null,
                        null, null, null), admin));

        assertEquals("CUSTOMER_DOCUMENT_EXISTS", exception.code());
    }

    @Test
    void sellerCannotUpdateCustomerOwnedByAnotherWorker() {
        when(repository.existsDocument("DNI", "12345678", "customer-2")).thenReturn(false);
        when(repository.update(eq("customer-2"), eq(8L), any(CustomerUpdate.class))).thenReturn(0);
        when(repository.findByPublicId("customer-2", 8L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.update("customer-2",
                new CustomerUpdate("PERSON", "DNI", "12345678", "Empresa", null, null, null, null, null, 0), seller));
    }

    @Test
    void validatesDocumentFormatBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new CustomerInput("PERSON", "DNI", "123", "Cliente", null, null, null, null, null), seller));
    }

    @Test
    void sellerCannotDeactivateCustomer() {
        assertThrows(SecurityException.class, () -> service.setActive("customer-1", false, 0L, seller));
    }

    private static WorkerPrincipal principal(long id, RoleCode role) {
        return new WorkerPrincipal(new WorkerRepository.WorkerRecord(id, role.name().toLowerCase() + "@example.test",
                role.label(), "hash", true, role));
    }

    private static CustomerView view(String publicId, long ownerId, boolean active, long version) {
        return new CustomerView(publicId, ownerId, "Vendedor", "PERSON", "DNI", "12345678", "Cliente", null,
                null, null, null, null, active, version, java.time.Instant.now(), java.time.Instant.now());
    }
}
