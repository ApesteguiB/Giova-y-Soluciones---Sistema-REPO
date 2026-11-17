package pe.giova.quotation;

public enum QuotationStatus {
    DRAFT, ISSUED, ACCEPTED, REJECTED, EXPIRED, CANCELLED;

    public boolean terminal() {
        return this == ACCEPTED || this == REJECTED || this == EXPIRED || this == CANCELLED;
    }
}
