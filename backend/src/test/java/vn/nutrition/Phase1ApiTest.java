package vn.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5433/nutrition?currentSchema=phase1_test",
    "spring.flyway.schemas=phase1_test", "app.demo-enabled=false"
})
@AutoConfigureMockMvc
class Phase1ApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    private final Map<String,String> sessionContexts=new HashMap<>();

    @BeforeEach void clean() {
        // Only the isolated phase1_test schema is cleared; never the application schema.
        db.execute("TRUNCATE TABLE users CASCADE");
    }

    void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", email, "password", "SecurePassword1!", "displayName", "Người dùng thử"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("role").value("USER"))
            .andExpect(jsonPath("passwordHash").doesNotExist());
    }
    Cookie login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk()).andReturn();
        Cookie cookie = r.getResponse().getCookie("NUTRITION_SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        sessionContexts.put(cookie.getValue(),json.readTree(r.getResponse().getContentAsString()).path("sessionContext").asText());
        return cookie;
    }
    Map<String, Object> profile() {
        Map<String, Object> p = new HashMap<>();
        p.put("expectedVersion", 0); p.put("birthDate", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(30).toString());
        p.put("sexForFormula", "MALE"); p.put("heightCm", 175); p.put("weightKg", 70);
        p.put("activityLevel", "SEDENTARY"); p.put("goal", "MAINTAIN");
        return p;
    }
    MvcResult save(Cookie session, Map<String, Object> profile, int expectedStatus) throws Exception {
        return mvc.perform(put("/api/v1/me/profile").cookie(session).header("X-Session-Context",sessionContexts.get(session.getValue())).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(profile))).andExpect(status().is(expectedStatus)).andReturn();
    }

    @Test void US01_TC01_registration_normalizes_email_and_rejects_duplicate() throws Exception {
        register(" Person@Example.com ");
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", "person@example.com", "password", "SecurePassword1!", "displayName", "Trùng"))))
            .andExpect(status().isConflict());
        Cookie c = login("PERSON@example.com", "SecurePassword1!");
        mvc.perform(get("/api/v1/auth/me").cookie(c)).andExpect(status().isOk()).andExpect(jsonPath("email").value("person@example.com"));
        assertThat(db.queryForObject("SELECT password_hash FROM users", String.class)).startsWith("$2");
    }
    @Test void FR01_TC01_password_change_revokes_all_sessions_and_logout_revokes_session() throws Exception {
        register("a@example.com"); Cookie a=login("a@example.com", "SecurePassword1!"), b=login("a@example.com", "SecurePassword1!");
        mvc.perform(post("/api/v1/auth/change-password").cookie(a).header("X-Session-Context",sessionContexts.get(a.getValue())).with(csrf()).contentType("application/json")
            .content("{\"currentPassword\":\"WrongPassword!\",\"newPassword\":\"ChangedPassword2!\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/change-password").cookie(a).header("X-Session-Context",sessionContexts.get(a.getValue())).with(csrf()).contentType("application/json")
            .content("{\"currentPassword\":\"SecurePassword1!\",\"newPassword\":\"ChangedPassword2!\"}")).andExpect(status().isNoContent());
        for (Cookie c : new Cookie[]{a,b}) mvc.perform(get("/api/v1/auth/me").cookie(c)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
            .content("{\"email\":\"a@example.com\",\"password\":\"SecurePassword1!\"}")).andExpect(status().isUnauthorized());
        Cookie c=login("a@example.com","ChangedPassword2!");
        mvc.perform(post("/api/v1/auth/logout").cookie(c).header("X-Session-Context",sessionContexts.get(c.getValue())).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(c)).andExpect(status().isUnauthorized());
    }
    @Test void NFR04_TC01_csrf_required_and_locked_or_expired_session_rejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("traceId").isNotEmpty());
        register("a@example.com"); Cookie c=login("a@example.com","SecurePassword1!");
        db.update("UPDATE sessions SET expires_at = now() - interval '1 second'");
        mvc.perform(get("/api/v1/auth/me").cookie(c)).andExpect(status().isUnauthorized());
        c=login("a@example.com","SecurePassword1!"); db.update("UPDATE users SET status='LOCKED'");
        mvc.perform(get("/api/v1/auth/me").cookie(c)).andExpect(status().isUnauthorized());
    }
    @Test void US02_TC02_private_profile_isolated_and_optimistic_version_preserves_history() throws Exception {
        register("a@example.com"); register("b@example.com"); Cookie a=login("a@example.com","SecurePassword1!"), b=login("b@example.com","SecurePassword1!");
        Map<String,Object> p=profile(); save(a,p,200);
        mvc.perform(get("/api/v1/me/profile").cookie(b)).andExpect(status().isNotFound());
        String owner=db.queryForObject("SELECT id FROM users WHERE email='a@example.com'", String.class);
        mvc.perform(get("/api/v1/users/"+owner+"/profile").cookie(b)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/accounts").cookie(b)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
        p.put("userId",owner); save(b,p,400); p.remove("userId");
        p.put("expectedVersion",1); p.put("weightKg",75); save(a,p,200); save(a,p,409);
        assertThat(db.queryForObject("SELECT count(*) FROM nutrition_profiles",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT weight_kg FROM nutrition_profiles WHERE version=1",Double.class)).isEqualTo(70);
        assertThat(db.queryForObject("SELECT count(*) FROM calculation_snapshots",Integer.class)).isEqualTo(2);
        mvc.perform(get("/api/v1/me/profile").cookie(a)).andExpect(jsonPath("version").value(2)).andExpect(jsonPath("profile.weightKg").value(75));
    }
    @Test void US04_TC04_metrics_match_hand_calculation_and_rule_version() throws Exception {
        register("a@example.com"); Cookie a=login("a@example.com","SecurePassword1!"); save(a,profile(),200);
        mvc.perform(get("/api/v1/me/metrics").cookie(a)).andExpect(status().isOk())
            .andExpect(jsonPath("metrics.bmi").value(22.857)).andExpect(jsonPath("metrics.estimatedBmr").value(1648.75))
            .andExpect(jsonPath("metrics.tdee").value(1978.5)).andExpect(jsonPath("metrics.targetKcal").value(1978.5))
            .andExpect(jsonPath("metrics.proteinG").value(98.925)).andExpect(jsonPath("metrics.carbohydrateG").value(247.313))
            .andExpect(jsonPath("metrics.fatG").value(65.95)).andExpect(jsonPath("metrics.waterMl").value(2100))
            .andExpect(jsonPath("ruleVersion").value("phase1-v1"));
    }
    @Test void US02_TC03_underage_profile_can_be_saved_without_adult_estimates() throws Exception {
        register("a@example.com"); Cookie a=login("a@example.com","SecurePassword1!");
        var p=profile();
        p.put("birthDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(18).plusDays(1).toString());
        save(a,p,200);
        mvc.perform(get("/api/v1/me/metrics").cookie(a)).andExpect(status().isOk())
            .andExpect(jsonPath("adult").value(false)).andExpect(jsonPath("metrics").isEmpty());
        assertThat(db.queryForObject("SELECT count(*) FROM calculation_snapshots",Integer.class)).isZero();
        var adult=profile();adult.put("expectedVersion",1);adult.put("birthDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(18).toString());
        save(a,adult,200);
        mvc.perform(get("/api/v1/me/metrics").cookie(a)).andExpect(jsonPath("adult").value(true)).andExpect(jsonPath("metrics").isNotEmpty());
    }
    @Test void US02_TC03_validation_rejects_invalid_measurements_and_future_birth_date() throws Exception {
        register("a@example.com"); Cookie a=login("a@example.com","SecurePassword1!"); Map<String,Object> p=profile();
        p.put("heightCm",0); p.put("weightKg",-1);
        JsonNode errors=json.readTree(save(a,p,400).getResponse().getContentAsString()).get("fieldErrors");
        assertThat(errors.has("heightCm")).isTrue(); assertThat(errors.has("weightKg")).isTrue();
        p=profile(); p.put("birthDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).toString()); save(a,p,400);
    }
    @Test void FR02_TC02_stale_tab_cannot_save_into_new_login_session() throws Exception {
        register("a@example.com"); register("b@example.com");
        Cookie a=login("a@example.com","SecurePassword1!"),b=login("b@example.com","SecurePassword1!");
        mvc.perform(put("/api/v1/me/profile").cookie(b).header("X-Session-Context",sessionContexts.get(a.getValue())).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(profile())))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SESSION_CHANGED"));
        assertThat(db.queryForObject("SELECT count(*) FROM nutrition_profiles",Integer.class)).isZero();
    }
    @Test void US02_TC03_extreme_measurement_is_field_error_not_database_overflow() throws Exception {
        register("a@example.com"); Cookie a=login("a@example.com","SecurePassword1!");
        var p=profile();p.put("heightCm",0.001);
        var result=json.readTree(save(a,p,400).getResponse().getContentAsString());
        assertThat(result.get("fieldErrors").has("heightCm")).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM nutrition_profiles",Integer.class)).isZero();
    }
    @Test void US02_TC02_delete_current_profile_is_versioned_and_allows_recreation() throws Exception {
        register("a@example.com");Cookie a=login("a@example.com","SecurePassword1!");
        var p=profile();save(a,p,200);
        p.put("expectedVersion",1);p.put("weightKg",71);save(a,p,200);
        String context=sessionContexts.get(a.getValue());
        mvc.perform(delete("/api/v1/me/profile").param("expectedVersion","1").cookie(a)
            .header("X-Session-Context",context).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/me/profile").param("expectedVersion","2").cookie(a)
            .header("X-Session-Context",context).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/profile").cookie(a)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/metrics").cookie(a)).andExpect(status().isNotFound());
        var recreated=save(a,profile(),200);
        assertThat(json.readTree(recreated.getResponse().getContentAsString()).path("version").asInt()).isEqualTo(3);
        assertThat(db.queryForObject("SELECT count(*) FROM nutrition_profiles",Integer.class)).isEqualTo(3);
    }
    @Test void NFR09_TC02_concurrent_profile_saves_have_exactly_one_winner() throws Exception {
        register("a@example.com");Cookie a=login("a@example.com","SecurePassword1!");save(a,profile(),200);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> task=()->{
                var p=profile();p.put("expectedVersion",1);start.await();
                return mvc.perform(put("/api/v1/me/profile").cookie(a).header("X-Session-Context",sessionContexts.get(a.getValue())).with(csrf())
                    .contentType("application/json").content(json.writeValueAsString(p))).andReturn().getResponse().getStatus();
            };
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();
            assertThat(java.util.List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM nutrition_profiles",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM nutrition_profiles WHERE is_current",Integer.class)).isEqualTo(1);
    }
    @Test void FR01_TC01_concurrent_login_cannot_survive_password_change_with_old_password() throws Exception {
        register("a@example.com");Cookie a=login("a@example.com","SecurePassword1!");
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var login=pool.submit(()->{start.await();return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"a@example.com\",\"password\":\"SecurePassword1!\"}")).andReturn();});
            var change=pool.submit(()->{start.await();return mvc.perform(post("/api/v1/auth/change-password").cookie(a)
                .header("X-Session-Context",sessionContexts.get(a.getValue())).with(csrf()).contentType("application/json")
                .content("{\"currentPassword\":\"SecurePassword1!\",\"newPassword\":\"ChangedPassword2!\"}")).andReturn();});
            start.countDown();assertThat(change.get().getResponse().getStatus()).isEqualTo(204);
            var result=login.get().getResponse();assertThat(result.getStatus()).isIn(200,401);
            if(result.getStatus()==200) mvc.perform(get("/api/v1/auth/me").cookie(result.getCookie("NUTRITION_SESSION"))).andExpect(status().isUnauthorized());
        }
        assertThat(db.queryForObject("SELECT count(*) FROM sessions WHERE revoked_at IS NULL",Integer.class)).isZero();
    }
}
