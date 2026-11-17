package pe.giova.warranty;

public enum WarrantyStatus {
    ACTIVE,
    EXPIRED,
    CLAIMED,
    CANCELLED;

    public boolean terminal() {
        return this != ACTIVE;
    }
}
