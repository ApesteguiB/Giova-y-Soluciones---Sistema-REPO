package pe.giova.goal.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pe.giova.goal.GoalType;

public final class GoalDtos {
    private GoalDtos() {}

    public record Input(@NotNull @Positive Long workerId, @NotNull GoalType goalType,
                        @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
                        @NotNull @DecimalMin("0.0001") BigDecimal targetValue,
                        @Size(max = 1000) String notes, boolean active) {}

    public record Update(@NotNull @Positive Long workerId, @NotNull GoalType goalType,
                         @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
                         @NotNull @DecimalMin("0.0001") BigDecimal targetValue,
                         @Size(max = 1000) String notes, boolean active,
                         @NotNull Long version) {}

    public record View(String publicId, long workerId, String workerDisplayName, GoalType goalType,
                       LocalDate periodStart, LocalDate periodEnd, BigDecimal targetValue,
                       BigDecimal currentValue, BigDecimal progressPercent, String notes,
                       boolean active, long version, Instant createdAt, Instant updatedAt) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}
}
