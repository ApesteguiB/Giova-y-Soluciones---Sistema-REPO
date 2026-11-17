package pe.giova.tender.contract;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class TenderJsonCodec {
    private final JsonMapper objectMapper;

    public TenderJsonCodec(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeStrings(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("No se pudo serializar la configuración de coincidencia", exception);
        }
    }

    public List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asString().isBlank()) {
                    values.add(item.asString());
                }
            }
            return List.copyOf(values);
        } catch (JacksonException exception) {
            return List.of();
        }
    }
}
