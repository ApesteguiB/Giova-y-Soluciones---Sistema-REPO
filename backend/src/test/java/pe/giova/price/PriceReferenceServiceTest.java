package pe.giova.price;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceInput;
import pe.giova.price.api.PriceReferenceDtos.PriceReferenceView;

class PriceReferenceServiceTest {
    private final PriceReferenceRepository repository = mock(PriceReferenceRepository.class);
    private final PriceReferenceService service = new PriceReferenceService(repository);
    private final WorkerPrincipal seller = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
            8L, "seller@example.test", "Vendedor", "hash", true, RoleCode.SELLER));

    @Test
    void requiresExchangeRateForUsd() {
        PriceReferenceInput input = input("USD", null);

        assertThrows(IllegalArgumentException.class, () -> service.create(input, seller));
    }

    @Test
    void rejectsExchangeRateForPen() {
        PriceReferenceInput input = input("PEN", BigDecimal.ONE);

        assertThrows(IllegalArgumentException.class, () -> service.create(input, seller));
    }

    @Test
    void sellerCanCreateReferenceWithProductActorAndBigDecimal() {
        PriceReferenceInput input = input("USD", new BigDecimal("3.750000"));
        PriceReferenceView view = new PriceReferenceView(4L, 10L, "SKU-10", "Producto", 8L, "Vendedor", null,
                null, "USD", new BigDecimal("125.5000"), false, new BigDecimal("0.18"),
                new BigDecimal("3.750000"), input.effectiveAt(), null, input.effectiveAt());
        when(repository.productExists(10L)).thenReturn(true);
        when(repository.insert(eq(8L), any(PriceReferenceInput.class))).thenReturn(4L);
        when(repository.findById(4L)).thenReturn(Optional.of(view));

        service.create(input, seller);

        PriceReferenceInput normalized = new PriceReferenceInput(10L, "Proveedor", "cot-1", "USD",
                new BigDecimal("125.5000"), false, new BigDecimal("0.18"), new BigDecimal("3.750000"),
                input.effectiveAt(), "nota");
        org.mockito.Mockito.verify(repository).insert(eq(8L), eq(normalized));
    }

    @Test
    void sellerCannotUpdateHistoricalReference() {
        assertThrows(SecurityException.class, () -> service.update(4L, input("PEN", null), seller));
    }

    private static PriceReferenceInput input(String currency, BigDecimal exchange) {
        return new PriceReferenceInput(10L, " Proveedor ", " cot-1 ", currency, new BigDecimal("125.5000"),
                false, new BigDecimal("0.18"), exchange, Instant.now().minus(1, ChronoUnit.DAYS), " nota ");
    }
}
