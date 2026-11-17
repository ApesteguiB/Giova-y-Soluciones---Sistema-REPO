package pe.giova.task.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import pe.giova.task.TaskPriority;
import pe.giova.task.TaskStatus;

public final class TaskDtos {
    private TaskDtos() {}

    public record Input(
            @NotBlank @Size(max = 180) String title,
            @Size(max = 1500) String description,
            @Positive Long assignedToWorkerId,
            @Size(max = 60) String relatedEntityType,
            @Size(max = 80) String relatedEntityId,
            @NotNull TaskPriority priority,
            Instant dueAt) {}

    public record Update(
            @NotBlank @Size(max = 180) String title,
            @Size(max = 1500) String description,
            @Positive Long assignedToWorkerId,
            @Size(max = 60) String relatedEntityType,
            @Size(max = 80) String relatedEntityId,
            @NotNull TaskPriority priority,
            Instant dueAt,
            @NotNull Long version) {}

    public record StatusChange(@NotNull TaskStatus targetStatus, @NotNull Long version) {}

    public record View(String publicId, String title, String description,
                       long assignedToWorkerId, String assignedToDisplayName,
                       long createdByWorkerId, String createdByDisplayName,
                       String relatedEntityType, String relatedEntityId,
                       TaskPriority priority, TaskStatus status, Instant dueAt,
                       Instant completedAt, long version, Instant createdAt, Instant updatedAt) {}

    public record Page(List<View> items, long totalItems, int page, int size, int totalPages) {}

    public record Assignee(long id, String displayName, String roleCode) {}
}
