package vn.nutrition.mealplan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.nutrition.auth.Account;
import vn.nutrition.common.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class MealPlans {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PENDING = "status IN ('QUEUED', 'RUNNING')";
    private static final String EXPIRE = """
        UPDATE meal_plan_jobs SET status='TIMED_OUT', error_code='MEAL_PLAN_TIMEOUT', finished_at=clock_timestamp()
        WHERE deadline_at <= clock_timestamp() AND
        """ + PENDING;

    record Context(UUID id, LocalDate date, BigDecimal targetKcal, String goal, String modelName) {}
    record Job(UUID jobId, String status, UUID mealPlanId, String errorCode) {}
    record PlanHeader(UUID id, LocalDate date, BigDecimal targetKcal, String explanation) {}
    record Item(UUID recipeId, String recipeName, BigDecimal servings, BigDecimal kcal) {}
    record Meal(String slot, List<Item> items) {}
    record Plan(UUID id, LocalDate date, BigDecimal targetKcal, BigDecimal totalKcal, String explanation, List<Meal> meals) {}
    private record ProfileReference(UUID profileId, UUID calculationId) {}

    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final OpenAiMealPlanner planner;

    MealPlans(JdbcTemplate db, TransactionTemplate transaction, OpenAiMealPlanner planner) {
        this.db = db;
        this.transaction = transaction;
        this.planner = planner;
    }

    @Transactional
    public Job create(Account account) {
        UUID userId = account.id();
        // The profile writer also locks this user row before changing the current version.
        db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, userId);
        expire(userId);
        Job pending = findJob(userId, null, true);
        if (pending != null) return pending;
        var profile = db.query("""
            SELECT p.id AS profile_id, c.id AS calculation_id
            FROM nutrition_profiles p LEFT JOIN calculation_snapshots c ON c.profile_id=p.id
            WHERE p.user_id=? AND p.is_current
            """, (rs, row) -> new ProfileReference(rs.getObject("profile_id", UUID.class),
                rs.getObject("calculation_id", UUID.class)), userId).stream().findFirst().orElse(null);
        if (profile == null) throw new ApiException(409, "PROFILE_REQUIRED", "Hãy lưu hồ sơ dinh dưỡng trước khi tạo gợi ý.");
        if (profile.calculationId() == null)
            throw new ApiException(409, "METRICS_REQUIRED", "Cần có kcal mục tiêu từ hồ sơ người trưởng thành để tạo gợi ý.");
        String model = planner.modelName();
        if (model.isBlank())
            throw new ApiException(503, "MEAL_AI_NOT_CONFIGURED", "Tính năng tạo gợi ý chưa được cấu hình trên máy chủ.");

        UUID id = UUID.randomUUID();
        db.update("""
            INSERT INTO meal_plan_jobs (id, user_id, profile_id, calculation_id, start_date, status,
                                        deadline_at, model_name, prompt_version)
            VALUES (?, ?, ?, ?, ?, 'QUEUED', clock_timestamp() + interval '180 seconds', ?, ?)
            """, id, userId, profile.profileId(), profile.calculationId(), LocalDate.now(ZONE), model, OpenAiMealPlanner.PROMPT_VERSION);
        return new Job(id, "QUEUED", null, null);
    }

    Job job(UUID userId, UUID jobId) {
        expire(userId);
        return findJob(userId, jobId, false);
    }

    private Job findJob(UUID userId, UUID jobId, boolean pendingOnly) {
        String filter = pendingOnly ? " AND j.status IN ('QUEUED', 'RUNNING')" : jobId == null ? "" : " AND j.id=?";
        Object[] args = jobId == null ? new Object[]{userId} : new Object[]{userId, jobId};
        return db.query("""
            SELECT j.id, j.status, j.error_code, p.id AS plan_id FROM meal_plan_jobs j
            LEFT JOIN meal_plans p ON p.generation_job_id=j.id AND j.status='SUCCEEDED'
            WHERE j.user_id=?
            """ + filter + " ORDER BY j.requested_at DESC, j.id DESC LIMIT 1",
            (rs, row) -> new Job(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getObject("plan_id", UUID.class), rs.getString("error_code")), args).stream().findFirst().orElse(null);
    }

    Plan plan(UUID userId, UUID planId) {
        String filter = planId == null ? "" : " AND p.id=?";
        Object[] args = planId == null ? new Object[]{userId} : new Object[]{userId, planId};
        PlanHeader header = db.query("""
            SELECT p.id, p.explanation, d.date, d.target_kcal
            FROM meal_plans p JOIN meal_plan_jobs j ON j.id=p.generation_job_id
            JOIN meal_plan_days d ON d.meal_plan_id=p.id
            WHERE j.user_id=? AND j.status='SUCCEEDED'
            """ + filter + " ORDER BY p.created_at DESC, p.id DESC LIMIT 1",
            (rs, row) -> new PlanHeader(rs.getObject("id", UUID.class), rs.getObject("date", LocalDate.class),
                rs.getBigDecimal("target_kcal"), rs.getString("explanation")), args).stream().findFirst().orElse(null);
        if (header == null) return null;
        return db.query("""
            SELECT m.meal_slot, i.recipe_id, i.recipe_name_snapshot, i.serving_multiplier, i.kcal
            FROM meal_plan_days d JOIN meals m ON m.plan_day_id=d.id JOIN meal_plan_items i ON i.meal_id=m.id
            WHERE d.meal_plan_id=? ORDER BY m.sequence_number, i.sequence_number
            """, rs -> {
                var meals = new ArrayList<Meal>();
                BigDecimal total = BigDecimal.ZERO;
                while (rs.next()) {
                    String slot = rs.getString("meal_slot");
                    if (meals.isEmpty() || !meals.getLast().slot().equals(slot)) meals.add(new Meal(slot, new ArrayList<>()));
                    BigDecimal kcal = rs.getBigDecimal("kcal");
                    meals.getLast().items().add(new Item(rs.getObject("recipe_id", UUID.class), rs.getString("recipe_name_snapshot"),
                        rs.getBigDecimal("serving_multiplier"), kcal));
                    total = total.add(kcal);
                }
                return new Plan(header.id(), header.date(), header.targetKcal(), total, header.explanation(), meals);
            }, header.id());
    }

    void expire(UUID userId) {
        if (userId == null) db.update(EXPIRE);
        else db.update(EXPIRE + " AND user_id=?", userId);
    }

    Context claim() {
        return db.query("""
            WITH claimed AS (
                UPDATE meal_plan_jobs SET status='RUNNING', catalog_version=?
                WHERE id=(
                    SELECT id FROM meal_plan_jobs
                    WHERE status='QUEUED' AND deadline_at>clock_timestamp()
                    ORDER BY requested_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                RETURNING id, start_date, calculation_id, profile_id, model_name
            )
            SELECT j.id, j.start_date, j.model_name, c.target_kcal, p.goal FROM claimed j
            JOIN calculation_snapshots c ON c.id=j.calculation_id JOIN nutrition_profiles p ON p.id=j.profile_id
            """, (rs, row) -> new Context(rs.getObject("id", UUID.class), rs.getObject("start_date", LocalDate.class),
                rs.getBigDecimal("target_kcal"), rs.getString("goal"), rs.getString("model_name")),
            MealCatalog.VERSION).stream().findFirst().orElse(null);
    }

    Long startAttempt(UUID id) {
        return db.query("""
            UPDATE meal_plan_jobs SET attempt_count=attempt_count+1
            WHERE id=? AND status='RUNNING' AND attempt_count<3 AND deadline_at>clock_timestamp()
            RETURNING floor(extract(epoch FROM (deadline_at-clock_timestamp()))*1000)::bigint
            """, (rs, row) -> rs.getLong(1), id).stream().findFirst().orElse(null);
    }

    void recordUsage(UUID id, OpenAiMealPlanner.Answer answer) {
        db.update("UPDATE meal_plan_jobs SET input_tokens=input_tokens+?, output_tokens=output_tokens+? WHERE id=?",
            answer.inputTokens(), answer.outputTokens(), id);
    }

    void fail(UUID id, String code) {
        db.update("""
            UPDATE meal_plan_jobs SET status=CASE WHEN deadline_at<=statement_timestamp() THEN 'TIMED_OUT' ELSE 'FAILED' END,
                error_code=CASE WHEN deadline_at<=statement_timestamp() THEN 'MEAL_PLAN_TIMEOUT' ELSE ? END,
                finished_at=clock_timestamp()
            WHERE id=? AND status='RUNNING'
            """, code, id);
    }

    void save(Context ctx, MealSuggestion.Plan suggestion, Map<UUID, MealCatalog.Recipe> recipes) {
        transaction.executeWithoutResult(tx -> {
            var running = db.query("""
                SELECT id FROM meal_plan_jobs WHERE id=? AND status='RUNNING' AND deadline_at>clock_timestamp() FOR UPDATE
                """, (rs, row) -> rs.getObject("id", UUID.class), ctx.id());
            if (running.isEmpty()) return;
            UUID planId = UUID.randomUUID();
            UUID dayId = UUID.randomUUID();
            db.update("INSERT INTO meal_plans (id, generation_job_id, explanation) VALUES (?, ?, ?)",
                planId, ctx.id(), suggestion.explanation());
            db.update("INSERT INTO meal_plan_days (id, meal_plan_id, date, target_kcal) VALUES (?, ?, ?, ?)",
                dayId, planId, ctx.date(), ctx.targetKcal());
            for (var meal : suggestion.meals()) {
                UUID mealId = UUID.randomUUID();
                db.update("INSERT INTO meals (id, plan_day_id, meal_slot, sequence_number) VALUES (?, ?, ?, ?)",
                    mealId, dayId, meal.slot().name(), meal.slot().ordinal() + 1);
                int order = 0;
                for (var item : meal.items()) {
                    var recipe = recipes.get(item.recipeId());
                    db.update("""
                        INSERT INTO meal_plan_items (meal_id, recipe_id, sequence_number, serving_multiplier,
                                                     recipe_name_snapshot, ingredient_snapshot_json, kcal)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                        """, mealId, recipe.id(), ++order, item.servings(), recipe.name(), recipe.ingredients().toString(), item.kcal());
                }
            }
            if (db.update("""
                UPDATE meal_plan_jobs SET status='SUCCEEDED', finished_at=clock_timestamp()
                WHERE id=? AND deadline_at>clock_timestamp()
                """, ctx.id()) == 0) tx.setRollbackOnly();
        });
        // A response that crossed the deadline could not commit a plan.
        expire(null);
    }
}
