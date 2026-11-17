package pe.giova.delivery;

public enum DeliveryStatus {
    DRAFT,
    SIGNED,
    CANCELLED;

    public boolean terminal() {
        return this == SIGNED || this == CANCELLED;
    }
}
