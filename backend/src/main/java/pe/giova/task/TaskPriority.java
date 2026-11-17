package pe.giova.task;

import java.util.Locale;

public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH;

    public static TaskPriority fromInput(TaskPriority value) {
        if (value == null) throw new IllegalArgumentException("priority es obligatorio");
        return value;
    }

    public static TaskPriority fromInput(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("priority es obligatorio");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("priority no permitido");
        }
    }
}
