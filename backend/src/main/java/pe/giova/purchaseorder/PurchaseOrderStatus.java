package pe.giova.purchaseorder;

public enum PurchaseOrderStatus {
    REGISTERED,
    IN_PROGRESS,
    DELIVERED,
    CANCELLED;

    public boolean terminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
