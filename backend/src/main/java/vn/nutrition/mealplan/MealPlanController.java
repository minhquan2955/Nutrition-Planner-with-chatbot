package vn.nutrition.mealplan;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.nutrition.auth.Account;
import vn.nutrition.common.ApiException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MealPlanController {
    private final MealPlans plans;
    private final MealCatalog catalog;

    MealPlanController(MealPlans plans, MealCatalog catalog) {
        this.plans = plans;
        this.catalog = catalog;
    }

    @GetMapping("/foods")
    public List<MealCatalog.Food> foods() {
        return catalog.foods();
    }

    @GetMapping("/recipes")
    public List<MealCatalog.Recipe> recipes() {
        return catalog.recipes();
    }

    @PostMapping("/meal-plan-jobs")
    public ResponseEntity<MealPlans.Job> create(@AuthenticationPrincipal Account account) {
        var job = plans.create(account);
        return ResponseEntity.accepted().location(URI.create("/api/v1/meal-plan-jobs/" + job.jobId())).body(job);
    }

    @GetMapping("/meal-plan-jobs/latest")
    public ResponseEntity<MealPlans.Job> latestJob(@AuthenticationPrincipal Account account) {
        var job = plans.job(account.id(), null);
        return job == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(job);
    }

    @GetMapping("/meal-plan-jobs/{id}")
    public MealPlans.Job job(@AuthenticationPrincipal Account account, @PathVariable UUID id) {
        var job = plans.job(account.id(), id);
        if (job == null) throw new ApiException(404, "MEAL_JOB_NOT_FOUND", "Không tìm thấy tác vụ tạo gợi ý.");
        return job;
    }

    @GetMapping("/meal-plans/latest")
    public ResponseEntity<MealPlans.Plan> latestPlan(@AuthenticationPrincipal Account account) {
        var plan = plans.plan(account.id(), null);
        return plan == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(plan);
    }

    @GetMapping("/meal-plans/{id}")
    public MealPlans.Plan plan(@AuthenticationPrincipal Account account, @PathVariable UUID id) {
        var plan = plans.plan(account.id(), id);
        if (plan == null) throw new ApiException(404, "MEAL_PLAN_NOT_FOUND", "Không tìm thấy gợi ý đã lưu.");
        return plan;
    }
}
