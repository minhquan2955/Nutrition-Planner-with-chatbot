package vn.nutrition.mealplan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
class OpenAiMealPlanner {
    static final String PROMPT_VERSION = "meal-day-v1";
    private static final String SYSTEM_PROMPT = """
        Đề xuất thực đơn tham khảo một ngày bằng tiếng Việt từ dữ liệu JSON được cung cấp.
        Dùng đúng ba bữa BREAKFAST, LUNCH, DINNER. Mỗi bữa chọn 1 đến 4 món khác nhau
        từ danh mục, dùng nguyên recipeId. Mỗi món từ 0,25 đến 3 khẩu phần, tối đa ba
        chữ số thập phân. Cân đối các bữa và chọn tổng kcal gần targetKcal nhất có thể.
        goal là mục tiêu dinh dưỡng: MAINTAIN (duy trì), LOSE (giảm cân), GAIN (tăng cân),
        MUSCLE (tăng cơ). Giải thích cách bố trí bữa, tối đa 280 ký tự, không ghi số kcal,
        không đưa lời khuyên y khoa, không tuyên bố phù hợp với dị ứng hay ngân sách.
        """;
    private static final String SCHEMA = """
        {"type":"object","additionalProperties":false,"required":["explanation","meals"],"properties":{
          "explanation":{"type":"string"},
          "meals":{"type":"array","items":{
            "type":"object","additionalProperties":false,"required":["slot","items"],"properties":{
              "slot":{"type":"string","enum":["BREAKFAST","LUNCH","DINNER"]},
              "items":{"type":"array","items":{
                "type":"object","additionalProperties":false,"required":["recipeId","servings"],"properties":{
                  "recipeId":{"type":"string"},"servings":{"type":"number"}
                }
              }}
            }
          }}
        }}
        """;

    record Candidate(UUID recipeId, String name, BigDecimal kcalPerServing) {}
    record Request(BigDecimal targetKcal, String goal, List<Candidate> recipes) {}
    record Answer(String text, long inputTokens, long outputTokens) {}

    private final ObjectMapper json;
    private final String modelName;
    private final OpenAiChatModel model;

    OpenAiMealPlanner(ObjectMapper json, @Value("${app.openai-api-key}") String apiKey,
                      @Value("${app.openai-model}") String modelName, RestClient.Builder restClient) {
        this.json = json;
        this.modelName = modelName.strip();
        if (apiKey.isBlank() || this.modelName.isBlank()) {
            model = null;
            return;
        }
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(Duration.ofSeconds(50));
        var api = OpenAiApi.builder().apiKey(apiKey.strip())
            .restClientBuilder(restClient.requestFactory(requestFactory)).build();
        var options = OpenAiChatOptions.builder().model(this.modelName)
            .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, SCHEMA))
            .maxCompletionTokens(2400).build();
        model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options)
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
    }

    String modelName() {
        return model == null ? "" : modelName;
    }

    Answer generate(Request input) {
        String payload;
        try {
            payload = json.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize meal suggestion input", e);
        }
        var response = model.call(new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(payload))));
        var result = response.getResult();
        var usage = response.getMetadata().getUsage();
        var inputTokens = usage == null ? null : usage.getPromptTokens();
        var outputTokens = usage == null ? null : usage.getCompletionTokens();
        return new Answer(result == null ? null : result.getOutput().getText(),
            inputTokens == null ? 0 : inputTokens, outputTokens == null ? 0 : outputTokens);
    }
}
