package pe.giova.goal;

import java.util.Locale;

public enum GoalType {
    QUOTATIONS_ISSUED,
    QUOTATION_AMOUNT,
    TENDERS_FOLLOWED,
    TASKS_COMPLETED;

    public static GoalType fromInput(GoalType value) {
        if (value == null) throw new IllegalArgumentException("goalType es obligatorio");
        return value;
    }

    public static GoalType fromInput(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("goalType no permitido");
        }
    }
}
