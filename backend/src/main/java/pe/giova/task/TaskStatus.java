package pe.giova.task;

import java.util.Locale;

public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static TaskStatus fromInput(TaskStatus value) {
        if (value == null) throw new IllegalArgumentException("status es obligatorio");
        return value;
    }

    public static TaskStatus fromInput(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status no permitido");
        }
    }
}
