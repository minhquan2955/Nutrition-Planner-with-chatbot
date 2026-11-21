package vn.nutrition.mealplan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiMealPlannerTest {
    @Test void US06_TC05_sends_strict_schema_and_only_required_inputs() {
        var json = new ObjectMapper();
        UUID recipeId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        var http = RestClient.builder().requestInterceptor((request, body, execution) -> {
            assertThat(request.getURI().toString()).isEqualTo("https://api.openai.com/v1/chat/completions");
            var payload = json.readTree(body);
            assertThat(payload.path("model").asText()).isEqualTo("test-model");
            var format = payload.path("response_format");
            assertThat(format.path("type").asText()).isEqualTo("json_schema");
            assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
            var input = json.readTree(payload.path("messages").get(1).path("content").asText());
            assertThat(input.size()).isEqualTo(3);
            assertThat(input.path("targetKcal").decimalValue()).isEqualByComparingTo("1950");
            assertThat(input.path("goal").asText()).isEqualTo("MAINTAIN");
            var candidate = input.path("recipes").get(0);
            assertThat(candidate.size()).isEqualTo(3);
            assertThat(candidate.path("recipeId").asText()).isEqualTo(recipeId.toString());
            assertThat(candidate.path("kcalPerServing").decimalValue()).isEqualByComparingTo("77.5");
            var response = new MockClientHttpResponse("""
                {"id":"response","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"{}"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120}}
                """.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            return response;
        });
        var planner = new OpenAiMealPlanner(json, "test-key", "test-model", http);
        var input = new OpenAiMealPlanner.Request(new BigDecimal("1950"), "MAINTAIN",
            List.of(new OpenAiMealPlanner.Candidate(recipeId, "Trứng luộc chín", new BigDecimal("77.5"))));
        assertThat(planner.generate(input)).isEqualTo(new OpenAiMealPlanner.Answer("{}", 100, 20));
    }
}
