package vn.nutrition.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:${TEST_DB_PORT:5433}/nutrition?currentSchema=phase3_chat_test",
    "spring.flyway.schemas=phase3_chat_test", "app.demo-enabled=false", "app.rag-chat-timeout-ms=2000",
    "app.rag-storage=target/phase3-chat-pdfs"
})
@AutoConfigureMockMvc
class ChatApiTest {
    private static final String CHAT = "/api/v1/conversations";
    private static final String MODEL = "text-embedding-3-small";
    private static final Path PDF_STORAGE = Path.of("target/phase3-chat-pdfs");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockitoBean RagAi ai;

    record Session(Cookie cookie, String context, String email, String userId) {}
    record Document(UUID id, UUID chunk, String file) {}

    @BeforeEach void reset() {
        db.execute("TRUNCATE users, rag_documents CASCADE");
        when(ai.embeddingModel()).thenReturn(MODEL);
        when(ai.embed(anyList(), any())).thenReturn(List.of(vector(1)));
    }

    @Test void US07_TC02_owns_conversations_and_stores_insufficient_evidence_turn() throws Exception {
        Session user = login();
        Session other = login();
        String conversation = conversation(user);
        mvc.perform(get(CHAT).cookie(user.cookie())).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(conversation));
        mvc.perform(get(CHAT + "/" + conversation).cookie(other.cookie())).andExpect(status().isNotFound());
        mvc.perform(send(other, conversation, "Câu hỏi")).andExpect(status().isNotFound());
        mvc.perform(send(user, conversation, "Nguồn hiện có nói gì về bánh phở?")).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].role").value("USER"))
            .andExpect(jsonPath("$.messages[1].content").value("Chưa đủ căn cứ từ các tài liệu đang hoạt động để trả lời câu hỏi này."))
            .andExpect(jsonPath("$.messages[1].sources").isEmpty());
        mvc.perform(get(CHAT + "/" + conversation).cookie(user.cookie())).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2));
        verify(ai, never()).embed(anyList(), any());
    }

    @Test void US07_TC10_verified_source_uses_physical_page_and_revocation_blocks_new_access() throws Exception {
        Document document = document("ACTIVE", MODEL, 1);
        document("READY", MODEL, 1);
        document("ACTIVE", "old-model", 1);
        Session user = login();
        Session other = login();
        String conversation = conversation(user);
        when(ai.embed(anyList(), any())).thenAnswer(call -> {
            List<String> input = call.getArgument(0);
            assertThat(input.getFirst()).contains("bánh phở")
                .doesNotContain(user.email(), "PrivateKey188!", "Người dùng thử", user.userId(), user.context());
            return List.of(vector(1));
        });
        when(ai.answer(anyString(), anyList(), anyList(), any())).thenAnswer(call -> {
            assertThat(call.<String>getArgument(0))
                .doesNotContain(user.email(), "PrivateKey188!", "Người dùng thử", user.userId(), user.context());
            assertThat(call.<List<RagAi.Context>>getArgument(2)).extracting(RagAi.Context::id)
                .containsExactly(document.chunk());
            return new RagAi.Result(true, "Bánh phở tươi có 143 kcal trên 100 g.", List.of(document.chunk()),
                "fixture-chat", 16, 25);
        });
        Files.createDirectories(PDF_STORAGE);
        Path copy = PDF_STORAGE.resolve(document.file());
        Files.copy(Path.of("../docs/corpus/bangthanhphanthucphamVN.pdf"), copy);
        try {
            var response = mvc.perform(send(user, conversation,
                    "Người dùng thử, " + user.email() + ", bánh phở bao nhiêu kcal?\n"
                        + user.userId() + " / " + user.context() + "\nmật khẩu: PrivateKey188!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[1].sources[0].title").value("Bảng thực phẩm"))
                .andExpect(jsonPath("$.messages[1].sources[0].pageStart").value(26))
                .andExpect(jsonPath("$.messages[1].sources[0].available").value(true)).andReturn().getResponse();
            assertThat(db.queryForObject("SELECT trace_id FROM chat_messages WHERE role='ASSISTANT'", UUID.class))
                .isNotNull();
            String source = json.readTree(response.getContentAsString()).at("/messages/1/sources/0/id").asText();
            String file = CHAT + "/" + conversation + "/sources/" + source + "/file";
            mvc.perform(get(file).cookie(user.cookie())).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get(file).cookie(other.cookie())).andExpect(status().isNotFound());
            db.update("UPDATE rag_documents SET status='REVOKED' WHERE id=?", document.id());
            mvc.perform(get(file).cookie(user.cookie())).andExpect(status().isNotFound());
            mvc.perform(send(user, conversation, "Còn giá trị đó?")).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].sources[0].available").value(false))
                .andExpect(jsonPath("$.messages[1].sources[0].quotedText").value("Bánh phở tươi, 143 kcal/100 g."))
                .andExpect(jsonPath("$.messages[3].sources").isEmpty());
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    @Test void US07_TC11_fabricated_citation_contaminates_the_whole_generated_answer() throws Exception {
        var document = document("ACTIVE", MODEL, 1);
        Session user = login();
        String conversation = conversation(user);
        when(ai.answer(anyString(), anyList(), anyList(), any())).thenReturn(
            new RagAi.Result(true, "Nội dung thiếu căn cứ.", List.of(document.chunk(), UUID.randomUUID()), "fixture-chat", 16, 25));
        mvc.perform(send(user, conversation, "Bánh phở tươi bao nhiêu kcal?")).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[1].content").value("Chưa đủ căn cứ từ các tài liệu đang hoạt động để trả lời câu hỏi này."))
            .andExpect(jsonPath("$.messages[1].sources").isEmpty());
    }

    @Test void US07_TC10_does_not_generate_with_unrelated_chunks() throws Exception {
        document("ACTIVE", MODEL, -1);
        Session user = login();
        mvc.perform(send(user, conversation(user), "Chiếc ô đồng nghĩa với gì?")).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[1].sources").isEmpty());
        verify(ai, never()).answer(anyString(), anyList(), anyList(), any());
    }

    @Test void US07_TC14_discards_late_output_and_allows_retry() throws Exception {
        var document = document("ACTIVE", MODEL, 1);
        Session user = login();
        String conversation = conversation(user);
        var canceled = new CountDownLatch(1);
        var result = new RagAi.Result(true, "143 kcal trên 100 g.", List.of(document.chunk()), "fixture-chat", 16, 25);
        when(ai.answer(anyString(), anyList(), anyList(), any())).thenAnswer(call -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException stop) {
                canceled.countDown();
            }
            return result;
        }).thenReturn(result);
        mvc.perform(send(user, conversation, "Bánh phở tươi bao nhiêu kcal?")).andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.code").value("CHAT_TIMEOUT"));
        assertThat(canceled.await(3, TimeUnit.SECONDS)).isTrue();
        mvc.perform(get(CHAT + "/" + conversation).cookie(user.cookie())).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages").isEmpty());
        mvc.perform(send(user, conversation, "Bánh phở tươi bao nhiêu kcal?")).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2)).andExpect(jsonPath("$.messages[1].sources.length()").value(1));
    }

    @Test void US07_TC14_repeating_request_id_returns_previously_saved_turn() throws Exception {
        Session user = login();
        String conversation = conversation(user);
        String path = CHAT + "/" + conversation + "/messages";
        String requestId = UUID.randomUUID().toString();
        var question = Map.of("content", "Bánh phở có bao nhiêu năng lượng?", "requestId", requestId);
        mvc.perform(request(user, path, question)).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2));
        mvc.perform(request(user, path, question)).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2));
        mvc.perform(request(user, path, Map.of("content", "Một câu hỏi khác", "requestId", requestId)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHAT_REQUEST_CHANGED"));
    }

    @Test void US07_TC10_query_history_has_a_token_budget() throws Exception {
        document("ACTIVE", MODEL, 1);
        Session user = login();
        String conversation = conversation(user);
        var tokenizer = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        when(ai.embed(anyList(), any())).thenAnswer(call -> {
            List<String> input = call.getArgument(0);
            assertThat(input.getFirst()).startsWith("Câu hỏi hiện tại");
            assertThat(tokenizer.countTokensOrdinary(input.getFirst())).isLessThanOrEqualTo(8000);
            return List.of(vector(1));
        });
        when(ai.answer(anyString(), anyList(), anyList(), any())).thenReturn(
            new RagAi.Result(false, "", List.of(), "fixture-chat", 1, 1));
        for (int turn = 1; turn <= 3; turn++) {
            mvc.perform(send(user, conversation, "Câu hỏi hiện tại " + "😄".repeat(1950)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(turn * 2));
        }
    }

    private Document document(String state, String model, float element) {
        UUID id = UUID.randomUUID();
        UUID chunk = UUID.randomUUID();
        String file = id + ".pdf";
        db.update("""
            INSERT INTO rag_documents(id,title,publisher,version,checksum,file_path,status)
            VALUES (?,'Bảng thực phẩm','Viện Dinh dưỡng','2007',?,?,?)
            """, id, "a".repeat(64), file, state);
        db.update("""
            INSERT INTO rag_chunks(id,document_id,chunk_index,content,page_start,page_end,embedding,embedding_model)
            VALUES (?,?,0,'Bánh phở tươi, 143 kcal/100 g.',26,26,CAST(? AS public.vector),?)
            """, chunk, id, RagAi.vector(vector(element)), model);
        return new Document(id, chunk, file);
    }

    private static float[] vector(float element) {
        float[] vector = new float[1536];
        vector[0] = element;
        return vector;
    }

    private Session login() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String password = "SecurePassword1!";
        var credentials = Map.of("email", email, "password", password);
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", email, "password", password, "displayName", "Người dùng thử"))))
            .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(credentials))).andExpect(status().isOk()).andReturn().getResponse();
        var account = json.readTree(response.getContentAsString());
        return new Session(response.getCookie("NUTRITION_SESSION"), account.path("sessionContext").asText(),
            email, account.path("id").asText());
    }

    private String conversation(Session user) throws Exception {
        var result = mvc.perform(request(user, CHAT, Map.of("title", "Dinh dưỡng"))).andExpect(status().isCreated())
            .andReturn().getResponse();
        return json.readTree(result.getContentAsString()).path("id").asText();
    }

    private MockHttpServletRequestBuilder send(Session user, String conversation, String content) throws Exception {
        return request(user, CHAT + "/" + conversation + "/messages", Map.of("content", content));
    }

    private MockHttpServletRequestBuilder request(Session user, String path, Map<String, String> body) throws Exception {
        return post(path).cookie(user.cookie()).header("X-Session-Context", user.context()).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body));
    }
}
