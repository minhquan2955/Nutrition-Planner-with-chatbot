package vn.nutrition.mealplan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class MealSuggestion {
    enum Slot { BREAKFAST, LUNCH, DINNER }

    record Item(UUID recipeId, BigDecimal servings, BigDecimal kcal) {}
    record Meal(Slot slot, List<Item> items) {}
    record Plan(String explanation, List<Meal> meals, BigDecimal totalKcal) {}

    private final ObjectReader reader;

    MealSuggestion(ObjectMapper json) {
        reader = json.reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    Plan validate(String output, Map<UUID, BigDecimal> catalog) {
        require(output != null && output.length() <= 12_000);
        try {
            JsonNode root = reader.readTree(output);
            fields(root, "explanation", "meals");
            String explanation = root.path("explanation").textValue();
            require(explanation != null && !explanation.isBlank() && explanation.length() <= 280);
            var meals = new EnumMap<Slot, Meal>(Slot.class);
            var entries = root.path("meals");
            require(entries.isArray() && entries.size() == Slot.values().length);
            BigDecimal total = BigDecimal.ZERO;
            for (JsonNode entry : entries) {
                fields(entry, "slot", "items");
                require(entry.path("slot").isTextual());
                Slot slot = Slot.valueOf(entry.path("slot").textValue());
                require(!meals.containsKey(slot));
                var items = entry.path("items");
                require(items.isArray() && !items.isEmpty() && items.size() <= 4);
                var used = new HashSet<UUID>();
                var validated = new ArrayList<Item>();
                for (JsonNode item : items) {
                    fields(item, "recipeId", "servings");
                    var idNode = item.path("recipeId");
                    require(idNode.isTextual() && idNode.textValue().length() == 36);
                    UUID id = UUID.fromString(idNode.textValue());
                    BigDecimal perServing = catalog.get(id);
                    require(perServing != null && used.add(id));
                    var portion = item.path("servings");
                    require(portion.isNumber());
                    BigDecimal servings = portion.decimalValue();
                    require(servings.compareTo(new BigDecimal("0.25")) >= 0
                        && servings.compareTo(BigDecimal.valueOf(3)) <= 0 && servings.scale() <= 3);
                    BigDecimal kcal = perServing.multiply(servings).setScale(3, RoundingMode.HALF_UP);
                    validated.add(new Item(id, servings, kcal));
                    total = total.add(kcal);
                }
                meals.put(slot, new Meal(slot, List.copyOf(validated)));
            }
            return new Plan(explanation.trim(), List.copyOf(meals.values()), total);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed meal suggestion", e);
        }
    }

    private static void fields(JsonNode object, String first, String second) {
        require(object.isObject() && object.size() == 2 && object.has(first) && object.has(second));
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid meal suggestion");
    }
}
