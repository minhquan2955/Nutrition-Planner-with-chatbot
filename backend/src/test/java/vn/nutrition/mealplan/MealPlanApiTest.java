package vn.nutrition.mealplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:${TEST_DB_PORT:5433}/nutrition?currentSchema=phase2_test",
    "spring.flyway.schemas=phase2_test", "app.demo-enabled=false"
})
@AutoConfigureMockMvc
class MealPlanApiTest {
    private static final String JOBS = "/api/v1/meal-plan-jobs";
    private static final String PLANS = "/api/v1/meal-plans";
    private static final String VALID = """
        {"explanation":"Ba bữa với các món mẫu.",
         "meals":[
           {"slot":"BREAKFAST","items":[{"recipeId":"40000000-0000-0000-0000-000000000001","servings":1.5}]},
           {"slot":"LUNCH","items":[{"recipeId":"40000000-0000-0000-0000-000000000002","servings":1}]},
           {"slot":"DINNER","items":[{"recipeId":"40000000-0000-0000-0000-000000000003","servings":2}]}
         ]}
        """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockitoBean OpenAiMealPlanner ai;

    record Session(Cookie cookie, String context) {}

    @BeforeEach void reset() {
        db.execute("TRUNCATE TABLE users CASCADE");
        when(ai.modelName()).thenReturn("test-model");
    }

    @Test void US03_TC05_recipe_kcal_matches_documented_ingredients() throws Exception {
        Session user = login("catalog@example.com");
        mvc.perform(get("/api/v1/recipes").cookie(user.cookie()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(13))
            .andExpect(jsonPath("$[?(@.name == 'Phở bò đơn giản')].kcalPerServing").value(391.5))
            .andExpect(jsonPath("$[?(@.name == 'Phở bò đơn giản')].ingredients[?(@.name == 'Bánh phở tươi')].kcalPer100g").value(143.0));
    }

    @Test void US06_TC05_job_retries_and_saves_only_a_complete_private_suggestion() throws Exception {
        Session user = login("plan@example.com");
        Session other = login("other@example.com");
        saveProfile(user, 30);
        when(ai.generate(any())).thenReturn(new OpenAiMealPlanner.Answer(null, 12, 1)).thenAnswer(invocation -> {
            var request = json.valueToTree(invocation.getArgument(0, OpenAiMealPlanner.Request.class));
            assertThat(request.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("targetKcal", "goal", "recipes");
            assertThat(request.path("targetKcal").decimalValue()).isEqualByComparingTo("1978.5");
            assertThat(request.path("goal").textValue()).isEqualTo("MAINTAIN");
            assertThat(request.path("recipes").size()).isEqualTo(13);
            return new OpenAiMealPlanner.Answer(VALID, 15, 30);
        });
        mvc.perform(get(JOBS + "/latest").cookie(user.cookie())).andExpect(status().isNoContent());
        String id = enqueue(user);
        mvc.perform(get(JOBS + "/" + id).cookie(other.cookie())).andExpect(status().isNotFound());
        var job = finished(user, id);
        assertThat(job.path("status").asText()).isEqualTo("SUCCEEDED");
        String planId = job.path("mealPlanId").asText();
        mvc.perform(get(PLANS + "/" + planId).cookie(other.cookie())).andExpect(status().isNotFound());
        mvc.perform(get(PLANS + "/latest").cookie(user.cookie())).andExpect(status().isOk())
            .andExpect(jsonPath("id").value(planId)).andExpect(jsonPath("targetKcal").value(1978.5))
            .andExpect(jsonPath("totalKcal").value(680.25)).andExpect(jsonPath("meals.length()").value(3))
            .andExpect(jsonPath("meals[0].items[0].kcal").value(116.25));
        assertThat(db.queryForMap("SELECT attempt_count, input_tokens, output_tokens FROM meal_plan_jobs WHERE id=?", UUID.fromString(id)))
            .containsAllEntriesOf(Map.of("attempt_count", 2, "input_tokens", 27L, "output_tokens", 31L));
    }

    @Test void US06_TC06_retries_at_most_three_times_without_saving_invalid_suggestion() throws Exception {
        Session user = login("invalid@example.com");
        saveProfile(user, 30);
        when(ai.generate(any())).thenReturn(new OpenAiMealPlanner.Answer(VALID.replace("1.5", "6"), 1, 1));
        String id = enqueue(user);
        var job = finished(user, id);
        assertThat(job.path("status").asText()).isEqualTo("FAILED");
        assertThat(job.path("mealPlanId").isNull()).isTrue();
        assertThat(db.queryForObject("SELECT attempt_count FROM meal_plan_jobs WHERE id=?", Integer.class, UUID.fromString(id))).isEqualTo(3);
        assertThat(db.queryForObject("SELECT count(*) FROM meal_plans", Integer.class)).isZero();
    }

    @Test void US06_TC14_discards_valid_result_after_deadline() throws Exception {
        Session user = login("timeout@example.com");
        saveProfile(user, 30);
        when(ai.generate(any())).thenAnswer(invocation -> {
            db.update("UPDATE meal_plan_jobs SET deadline_at=clock_timestamp() WHERE status='RUNNING'");
            return new OpenAiMealPlanner.Answer(VALID, 1, 1);
        });
        String id = enqueue(user);
        var job = finished(user, id);
        assertThat(job.path("status").asText()).isEqualTo("TIMED_OUT");
        assertThat(job.path("mealPlanId").isNull()).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM meal_plans", Integer.class)).isZero();
    }

    @Test void US06_TC03_requires_adult_metrics_and_configured_ai() throws Exception {
        Session user = login("profile@example.com");
        mvc.perform(post(JOBS).cookie(user.cookie()).header("X-Session-Context", user.context()).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("PROFILE_REQUIRED"));
        saveProfile(user, 17);
        mvc.perform(post(JOBS).cookie(user.cookie()).header("X-Session-Context", user.context()).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("METRICS_REQUIRED"));
        Session adult = login("unconfigured@example.com");
        saveProfile(adult, 30);
        when(ai.modelName()).thenReturn("");
        mvc.perform(post(JOBS).cookie(adult.cookie()).header("X-Session-Context", adult.context()).with(csrf()))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("MEAL_AI_NOT_CONFIGURED"));
    }

    private Session login(String email) throws Exception {
        var credentials = Map.of("email", email, "password", "SecurePassword1!");
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", email, "password", credentials.get("password"), "displayName", "Người dùng thử"))))
            .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(credentials))).andExpect(status().isOk()).andReturn().getResponse();
        return new Session(response.getCookie("NUTRITION_SESSION"), json.readTree(response.getContentAsString()).path("sessionContext").asText());
    }

    private void saveProfile(Session user, int age) throws Exception {
        var profile = Map.of("expectedVersion", 0, "birthDate", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(age),
            "sexForFormula", "MALE", "heightCm", 175, "weightKg", 70, "activityLevel", "SEDENTARY", "goal", "MAINTAIN");
        mvc.perform(put("/api/v1/me/profile").cookie(user.cookie()).header("X-Session-Context", user.context()).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(profile))).andExpect(status().isOk());
    }

    private String enqueue(Session user) throws Exception {
        var response = mvc.perform(post(JOBS).cookie(user.cookie()).header("X-Session-Context", user.context()).with(csrf()))
            .andExpect(status().isAccepted()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("jobId").asText();
    }

    private JsonNode finished(Session user, String id) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        do {
            var response = mvc.perform(get(JOBS + "/" + id).cookie(user.cookie())).andExpect(status().isOk()).andReturn().getResponse();
            var job = json.readTree(response.getContentAsString());
            if (!java.util.Set.of("QUEUED", "RUNNING").contains(job.path("status").asText())) return job;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Meal plan job did not finish");
    }
}
