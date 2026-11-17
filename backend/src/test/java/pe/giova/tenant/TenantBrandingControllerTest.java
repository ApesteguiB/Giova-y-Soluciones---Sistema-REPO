package pe.giova.tenant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.giova.admin.NotFoundException;
import pe.giova.api.ApiExceptionHandler;
import pe.giova.document.TenantLogoStorage;

class TenantBrandingControllerTest {
    private final TenantBrandingService service = mock(TenantBrandingService.class);
    private final TenantBrandingController controller = new TenantBrandingController(service);

    @Test
    void brandingResponseIsNotCached() {
        var branding = new TenantBrandingDtos.Response(2L, "Empresa B", "#f26b21", "#ffffff",
                "#ff9f1c", null, false);
        when(service.current()).thenReturn(branding);

        var response = controller.current();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(branding, response.getBody());
    }

    @Test
    void logoResponseIsPngAndBlocksContentSniffing() {
        byte[] png = new byte[]{1, 2, 3};
        when(service.currentLogo()).thenReturn(new TenantLogoStorage.Logo("image/png", png));

        var response = controller.logo();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertArrayEquals(png, response.getBody());
    }

    @Test
    void missingTenantLogoMapsToNotFoundAndInvalidColorMapsToBadRequest() {
        var handler = new ApiExceptionHandler();
        assertEquals(HttpStatus.NOT_FOUND, handler.notFound(new NotFoundException("Logo no encontrado")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.badRequest(
                new TenantBrandingValidationException("primaryColor inválido"),
                new org.springframework.mock.web.MockHttpServletRequest()).getStatusCode());
        when(service.currentLogo()).thenThrow(new NotFoundException("Logo no encontrado"));
        assertThrows(NotFoundException.class, controller::logo);
    }
}
