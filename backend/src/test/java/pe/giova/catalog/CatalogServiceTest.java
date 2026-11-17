package pe.giova.catalog;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.giova.catalog.api.CatalogDtos.Page;
import pe.giova.catalog.api.CatalogDtos.Product;

class CatalogServiceTest {
    private final CatalogRepository repository = org.mockito.Mockito.mock(CatalogRepository.class);
    private final CatalogService service = new CatalogService(repository);

    @Test
    void escapesLikeWildcardsAndMapsAllCategoryToNoFilter() {
        Page<Product> expected = new Page<>(List.of(), 0, 0, 60, 0);
        when(repository.findProducts(eq("%100\\%%"), eq(null), eq(0), eq(60))).thenReturn(expected);

        service.products("100%", "all", 0, 60);

        verify(repository).findProducts("%100\\%%", null, 0, 60);
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> service.products(null, null, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.products(null, null, -1, 10));
    }
}
