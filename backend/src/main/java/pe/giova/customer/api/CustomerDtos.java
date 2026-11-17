package pe.giova.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record CustomerInput(
            @NotBlank @Size(max = 15) String customerType,
            @NotBlank @Size(max = 10) String documentType,
            @NotBlank @Size(max = 20) String documentNumber,
            @NotBlank @Size(max = 180) String businessName,
            @Size(max = 140) String contactName,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 300) String address,
            @Size(max = 1000) String notes) {
    }

    public record CustomerUpdate(
            @NotBlank @Size(max = 15) String customerType,
            @NotBlank @Size(max = 10) String documentType,
            @NotBlank @Size(max = 20) String documentNumber,
            @NotBlank @Size(max = 180) String businessName,
            @Size(max = 140) String contactName,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 300) String address,
            @Size(max = 1000) String notes,
            @Min(0) long version) {
    }

    public record CustomerStatusInput(@NotNull Boolean active, @Min(0) long version) {
    }

    public record CustomerView(
            String publicId,
            long ownerWorkerId,
            String ownerDisplayName,
            String customerType,
            String documentType,
            String documentNumber,
            String businessName,
            String contactName,
            String email,
            String phone,
            String address,
            String notes,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CustomerPage(List<CustomerView> items, long totalItems, int page, int size, int totalPages) {
    }
}
