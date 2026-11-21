package vn.nutrition.mealplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
class MealCatalog {
    static final String VERSION = "phase2-v1";

    record Food(UUID id, String name, String preparationState, BigDecimal kcalPer100g, String source) {}
    record Recipe(UUID id, String name, BigDecimal kcalPerServing, String source, String instructions, JsonNode ingredients) {}

    private final JdbcTemplate db;
    private final ObjectMapper json;

    MealCatalog(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public List<Food> foods() {
        return db.query("""
            SELECT i.id, i.name, i.preparation_state, n.kcal, s.title || ' · ' || n.source_locator AS source
            FROM ingredients i JOIN nutrient_values n ON n.ingredient_id=i.id
            JOIN data_sources s ON s.id=i.source_id
            WHERE i.status='ACTIVE' ORDER BY i.name, i.id
            """, (rs, row) -> new Food(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("preparation_state"), rs.getBigDecimal("kcal"), rs.getString("source")));
    }

    public List<Recipe> recipes() {
        return db.query("""
            SELECT r.id, r.name, r.instructions, round(sum(n.kcal * ri.quantity_g / n.basis_grams) / r.standard_servings, 3) AS kcal,
                   s.title || ' · ' || r.source_locator AS source,
                   jsonb_agg(jsonb_build_object(
                       'name', i.name, 'preparationState', i.preparation_state,
                       'gramsPerServing', round(ri.quantity_g / r.standard_servings, 3),
                       'kcalPer100g', n.kcal, 'source', ns.title || ' · ' || n.source_locator
                   ) ORDER BY i.name, i.id) AS ingredients
            FROM recipes r JOIN data_sources s ON s.id=r.source_id
            JOIN recipe_ingredients ri ON ri.recipe_id=r.id
            JOIN ingredients i ON i.id=ri.ingredient_id
            JOIN nutrient_values n ON n.ingredient_id=i.id
            JOIN data_sources ns ON ns.id=i.source_id
            WHERE r.status='ACTIVE' GROUP BY r.id, s.title
            HAVING bool_and(i.status='ACTIVE') AND sum(n.kcal * ri.quantity_g) > 0
            ORDER BY r.name, r.id
            """, (rs, row) -> new Recipe(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getBigDecimal("kcal"), rs.getString("source"), rs.getString("instructions"), readJson(rs.getString("ingredients"))));
    }

    private JsonNode readJson(String value) {
        try {
            return json.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid catalog snapshot", e);
        }
    }
}
