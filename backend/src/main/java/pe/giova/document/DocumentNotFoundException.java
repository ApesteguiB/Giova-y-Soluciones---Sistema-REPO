package pe.giova.document;

import pe.giova.admin.NotFoundException;

public class DocumentNotFoundException extends NotFoundException {
    public DocumentNotFoundException(String message) {
        super(message);
    }
}
