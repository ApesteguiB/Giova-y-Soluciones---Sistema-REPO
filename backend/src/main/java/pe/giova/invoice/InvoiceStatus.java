package pe.giova.invoice;

public enum InvoiceStatus {
    DRAFT, ISSUED, CANCELLED;

    public boolean terminal() {
        return this != DRAFT;
    }
}
