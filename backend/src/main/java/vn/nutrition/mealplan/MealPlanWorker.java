package vn.nutrition.mealplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class MealPlanWorker {
    private static final Logger LOG = LoggerFactory.getLogger(MealPlanWorker.class);
    private final MealPlans plans;
    private final MealCatalog catalog;
    private final OpenAiMealPlanner ai;
    private final MealSuggestion validator;

    MealPlanWorker(MealPlans plans, MealCatalog catalog, OpenAiMealPlanner ai, MealSuggestion validator) {
        this.plans = plans;
        this.catalog = catalog;
        this.ai = ai;
        this.validator = validator;
    }

    // ponytail: one job at a time per process. Additional instances can claim distinct jobs.
    @Scheduled(fixedDelay = 500)
    void runNext() {
        plans.expire(null);
        var job = plans.claim();
        if (job == null) return;
        try {
            process(job);
        } catch (RuntimeException e) {
            // Correlate by job ID without recording AI responses or profile details.
            LOG.warn("Meal plan job {} failed internally ({})", job.id(), e.getClass().getSimpleName());
            plans.fail(job.id(), "MEAL_PLAN_ERROR");
        }
    }

    private void process(MealPlans.Context job) {
        if (!job.modelName().equals(ai.modelName())) {
            plans.fail(job.id(), "MEAL_AI_NOT_CONFIGURED");
            return;
        }
        var recipes = catalog.recipes();
        if (recipes.isEmpty()) {
            plans.fail(job.id(), "EMPTY_MEAL_CATALOG");
            return;
        }
        var byId = recipes.stream().collect(Collectors.toMap(MealCatalog.Recipe::id, Function.identity()));
        var kcalById = recipes.stream().collect(Collectors.toMap(MealCatalog.Recipe::id, MealCatalog.Recipe::kcalPerServing));
        var candidates = recipes.stream()
            .map(r -> new OpenAiMealPlanner.Candidate(r.id(), r.name(), r.kcalPerServing())).toList();
        var input = new OpenAiMealPlanner.Request(job.targetKcal(), job.goal(), candidates);
        String error = "MEAL_AI_UNAVAILABLE";
        for (int attempt = 0; attempt < 3; attempt++) {
            Long remainingMillis = plans.startAttempt(job.id());
            if (remainingMillis == null || remainingMillis <= 0) break;
            var request = new FutureTask<>(() -> ai.generate(input));
            Thread.startVirtualThread(request);
            OpenAiMealPlanner.Answer answer;
            try {
                answer = request.get(Math.min(remainingMillis, 55_000), TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException e) {
                error = "MEAL_AI_UNAVAILABLE";
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                request.cancel(true);
            }
            plans.recordUsage(job.id(), answer);
            MealSuggestion.Plan suggestion;
            try {
                suggestion = validator.validate(answer.text(), kcalById);
            } catch (IllegalArgumentException e) {
                error = "MEAL_AI_INVALID_OUTPUT";
                continue;
            }
            plans.save(job, suggestion, byId);
            return;
        }
        plans.fail(job.id(), error);
    }
}
