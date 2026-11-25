package vn.nutrition.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:${TEST_DB_PORT:5433}/nutrition?currentSchema=rag_admin_test",
    "spring.flyway.schemas=rag_admin_test", "app.demo-enabled=false", "app.rag-storage=target/rag-admin-test"
})
@AutoConfigureMockMvc
class RagDocumentApiTest {
    private static final String DOCUMENTS = "/api/v1/admin/rag-documents";
    private static final String FDA_CHECKSUM = "6b73da78a2397b31683e7bad11fa8fea057a8973695d639237de2c428d14409a";
    private static final Path FDA = Path.of("..", "docs", "fixtures", "fda-food-allergies-2024.pdf");
    private static final Path STORAGE = Path.of("target", "rag-admin-test");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockitoBean RagAi ai;

    @BeforeEach void reset() {
        db.execute("TRUNCATE TABLE users, rag_documents CASCADE");
        when(ai.embeddingModel()).thenReturn("test-embedding");
        when(ai.embed(anyList(), any(Duration.class))).thenAnswer(invocation -> {
            List<String> input = invocation.getArgument(0);
            float[] vector = new float[1536];
            vector[0] = 1;
            return java.util.Collections.nCopies(input.size(), vector);
        });
    }

    @Test void US05_TC10_corrupt_PDF_fails_with_no_reviewable_chunks() throws Exception {
        var admin = login("reviewer@example.com", true);
        var file = new MockMultipartFile("file", "invalid.pdf", "application/pdf", new byte[]{1, 2, 3});
        String title = "Reference " + UUID.randomUUID();
        String id = upload(admin, file, "", title);

        JsonNode failed = finished(admin, id);
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("errorCode").asText()).isEqualTo("PDF_INVALID");
        assertThat(failed.path("chunks")).isEmpty();
        mvc.perform(post(DOCUMENTS + "/" + id + "/activate").cookie(admin.cookie)
                .header("X-Session-Context", admin.context).with(csrf()))
            .andExpect(status().isConflict());
        String corrected = upload(admin, fda(), "2", title);
        assertThat(corrected).isNotEqualTo(id);
        assertThat(finished(admin, corrected).path("status").asText()).isEqualTo("READY");
    }

    @Test void US05_TC01_user_cannot_list_admin_PDFs() throws Exception {
        var user = login("reader@example.com", false);
        mvc.perform(get(DOCUMENTS).cookie(user.cookie)).andExpect(status().isForbidden());
    }

    @Test void US05_TC10_admin_reviews_FDA_page_and_revokes_the_activated_document() throws Exception {
        var admin = login("reviewer@example.com", true);
        String id = upload(admin, fda(), "2");
        JsonNode ready = finished(admin, id);
        assertThat(ready.path("status").asText()).isEqualTo("READY");
        assertThat(ready.path("checksum").asText()).isEqualTo(FDA_CHECKSUM);
        assertThat(ready.path("chunkCount").asInt()).isPositive();
        assertThat(ready.path("chunks")).allSatisfy(chunk -> {
            assertThat(chunk.path("pageStart").asInt()).isEqualTo(2);
            assertThat(chunk.path("pageEnd").asInt()).isEqualTo(2);
        }).anySatisfy(chunk -> assertThat(chunk.path("content").asText()).contains("Contains"));

        mvc.perform(get(DOCUMENTS + "/" + id + "/file").cookie(admin.cookie).header("Range", "bytes=0-4"))
            .andExpect(status().isPartialContent()).andExpect(header().string("Cache-Control", "no-store"));
        lifecycle(admin, id, "activate", "ACTIVE");
        lifecycle(admin, id, "revoke", "REVOKED");
        mvc.perform(post(DOCUMENTS + "/" + id + "/activate").cookie(admin.cookie)
                .header("X-Session-Context", admin.context).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("RAG_DOCUMENT_STATUS"));
        mvc.perform(get(DOCUMENTS + "/" + id + "/file").cookie(admin.cookie)).andExpect(status().isOk());
    }

    @Test void US05_TC10_invalid_embedding_does_not_publish_any_chunk() throws Exception {
        when(ai.embed(anyList(), any(Duration.class))).thenAnswer(invocation -> {
            List<String> input = invocation.getArgument(0);
            return java.util.Collections.nCopies(input.size(), new float[1536]);
        });
        var admin = login("reviewer@example.com", true);
        JsonNode failed = finished(admin, upload(admin, fda(), "1-2"));
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("errorCode").asText()).isEqualTo("RAG_EMBEDDING_INVALID");
        assertThat(failed.path("chunkCount").asInt()).isZero();
        assertThat(failed.path("chunks")).isEmpty();
    }

    @Test void US05_TC14_worker_recovers_an_expired_claim_from_the_original_PDF() throws Exception {
        var admin = login("reviewer@example.com", true);
        UUID id = UUID.randomUUID();
        String fileName = id + ".pdf";
        Files.createDirectories(STORAGE);
        Files.copy(FDA, STORAGE.resolve(fileName));
        db.update("""
            INSERT INTO rag_documents(id,title,publisher,version,checksum,file_path,status,selected_pages,
                processing_token,processing_until) VALUES (?,?,?,?,?,?,'PROCESSING','1',?,clock_timestamp()-interval '1 second')
            """, id, "FDA Reference", "FDA", "2024", FDA_CHECKSUM, fileName, UUID.randomUUID());
        JsonNode ready = finished(admin, id.toString());
        assertThat(ready.path("status").asText()).isEqualTo("READY");
        assertThat(ready.path("chunks")).anySatisfy(chunk -> {
            assertThat(chunk.path("pageStart").asInt()).isEqualTo(1);
            assertThat(chunk.path("content").asText()).containsIgnoringCase("sesame");
        });
    }

    private MockMultipartFile fda() throws Exception {
        return new MockMultipartFile("file", "../../fda.pdf", "application/pdf", Files.readAllBytes(FDA));
    }

    private void lifecycle(Session admin, String id, String action, String expected) throws Exception {
        mvc.perform(post(DOCUMENTS + "/" + id + "/" + action).cookie(admin.cookie)
                .header("X-Session-Context", admin.context).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value(expected));
    }

    private String upload(Session admin, MockMultipartFile file, String pages) throws Exception {
        return upload(admin, file, pages, "Reference " + UUID.randomUUID());
    }

    private String upload(Session admin, MockMultipartFile file, String pages, String title) throws Exception {
        var response = mvc.perform(multipart(DOCUMENTS).file(file).param("title", title)
                .param("publisher", "FDA").param("version", "2024").param("pages", pages)
                .cookie(admin.cookie).header("X-Session-Context", admin.context).with(csrf()))
            .andExpect(status().isAccepted()).andExpect(jsonPath("status").value("QUEUED"))
            .andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("id").asText();
    }

    private JsonNode finished(Session admin, String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            var response = mvc.perform(get(DOCUMENTS + "/" + id).cookie(admin.cookie))
                .andExpect(status().isOk()).andReturn().getResponse();
            var document = json.readTree(response.getContentAsString());
            if (!Set.of("QUEUED", "PROCESSING").contains(document.path("status").asText())) return document;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("PDF processing did not finish");
    }

    private Session login(String email, boolean admin) throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "SecurePassword1!",
                    "displayName", "Reviewer"))))
            .andExpect(status().isCreated());
        if (admin) db.update("UPDATE users SET role='ADMIN' WHERE email=?", email);
        var response = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "SecurePassword1!"))))
            .andExpect(status().isOk()).andReturn().getResponse();
        return new Session(response.getCookie("NUTRITION_SESSION"),
            json.readTree(response.getContentAsString()).path("sessionContext").asText());
    }

    private record Session(Cookie cookie, String context) {}
}
