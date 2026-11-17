package pe.giova.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.giova.catalog.api.CatalogDtos.Problem;

@RestController
@RequestMapping("/api/v1")
public class UnknownApiController {

    @GetMapping("/{path}")
    public ResponseEntity<Problem> unknown(@PathVariable String path) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Problem("about:blank", "Recurso no encontrado", 404,
                        "La ruta solicitada no existe", "NOT_FOUND"));
    }
}
