package vn.nutrition.mealplan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealSuggestionTest {
    private static final UUID RECIPE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String VALID = """
        {"explanation":"Ba bữa với khẩu phần theo mục tiêu.",
         "meals":[
           {"slot":"BREAKFAST","items":[{"recipeId":"%s","servings":1.5}]},
           {"slot":"LUNCH","items":[{"recipeId":"%s","servings":1}]},
           {"slot":"DINNER","items":[{"recipeId":"%s","servings":0.5}]}
         ]}
        """.formatted(RECIPE, RECIPE, RECIPE);
    private final MealSuggestion validator = new MealSuggestion(new ObjectMapper());
    private final Map<UUID, BigDecimal> catalog = Map.of(RECIPE, new BigDecimal("321.250"));

    @Test void US06_TC05_totals_are_derived_from_catalog_and_portions() {
        var plan = validator.validate(VALID, catalog);
        assertThat(plan.totalKcal()).isEqualByComparingTo("963.750");
        assertThat(plan.meals().getFirst().items().getFirst().kcal()).isEqualByComparingTo("481.875");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "3.01", "1.1234", "\"1\"", "null"})
    void US06_TC06_rejects_invalid_servings(String value) {
        assertInvalid(VALID.replace("1.5", value));
    }

    @Test void US06_TC06_rejects_unknown_recipe_duplicate_meal_and_unexpected_fields() {
        assertInvalid(VALID.replace(RECIPE.toString(), "20000000-0000-0000-0000-000000000001"));
        assertInvalid(VALID.replace("DINNER", "LUNCH"));
        assertInvalid(VALID.replace("\"servings\":1.5", "\"servings\":1.5,\"kcal\":9"));
        assertInvalid(VALID + "{}");
        assertInvalid(null);
    }

    private void assertInvalid(String output) {
        assertThatThrownBy(() -> validator.validate(output, catalog)).isInstanceOf(IllegalArgumentException.class);
    }
}
