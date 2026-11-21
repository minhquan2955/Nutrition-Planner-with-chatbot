"use client";

import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { Notice } from "./components";

type Job = {
  jobId: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "TIMED_OUT";
  mealPlanId: string | null;
  errorCode: string | null;
};

const mealLabels = { BREAKFAST: "Bữa sáng", LUNCH: "Bữa trưa", DINNER: "Bữa tối" };
type Plan = {
  id: string;
  date: string;
  targetKcal: number;
  totalKcal: number;
  explanation: string;
  meals: {
    slot: keyof typeof mealLabels;
    items: { recipeId: string; recipeName: string; servings: number; kcal: number }[];
  }[];
};

type Recipe = {
  id: string;
  name: string;
  source: string;
  instructions: string;
  kcalPerServing: number;
  ingredients: { name: string; preparationState: string; gramsPerServing: number; kcalPer100g: number; source: string }[];
};

const errors: Record<string, string> = {
  MEAL_AI_INVALID_OUTPUT: "Gợi ý nhận được chưa hợp lệ. Bạn có thể thử lại.",
  MEAL_AI_UNAVAILABLE: "Dịch vụ tạo gợi ý đang bận. Vui lòng thử lại.",
  MEAL_AI_NOT_CONFIGURED: "Tính năng tạo gợi ý chưa được cấu hình trên máy chủ.",
  MEAL_PLAN_TIMEOUT: "Hết thời gian tạo gợi ý. Bạn có thể thử lại.",
  EMPTY_MEAL_CATALOG: "Chưa có món mẫu để tạo gợi ý.",
};
const kcal = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });
const quantity = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

function isPending(job: Job | undefined) {
  return job?.status === "QUEUED" || job?.status === "RUNNING";
}

export function MealPlanScreen({ targetKcal, hasProfile, editProfile }: {
  targetKcal?: number;
  hasProfile: boolean;
  editProfile: () => void;
}) {
  const [plan, setPlan] = useState<Plan>();
  const [job, setJob] = useState<Job>();
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [refresh, setRefresh] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);
  const pendingJobId = isPending(job) ? job?.jobId : undefined;

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const [latestJob, latestPlan] = await Promise.all([
          api<Job | undefined>("/meal-plan-jobs/latest"),
          api<Plan | undefined>("/meal-plans/latest"),
        ]);
        const saved = latestJob?.mealPlanId && latestJob.mealPlanId !== latestPlan?.id
          ? await api<Plan>(`/meal-plans/${latestJob.mealPlanId}`) : latestPlan;
        if (active) { setPlan(saved); setJob(latestJob); }
      } catch (e) {
        if (active) { setError(e); setLoadFailed(true); }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [refresh]);

  useEffect(() => {
    if (!pendingJobId || loadFailed || loading) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const current = await api<Job>(`/meal-plan-jobs/${pendingJobId}`);
        if (!active) return;
        if (isPending(current)) {
          setJob(current);
          timer = setTimeout(poll, 1000);
          return;
        }
        const saved = current.mealPlanId ? await api<Plan>(`/meal-plans/${current.mealPlanId}`) : undefined;
        if (active) {
          if (saved) setPlan(saved);
          setJob(current);
        }
      } catch (e) {
        if (active) { setError(e); setLoadFailed(true); }
      }
    }
    timer = setTimeout(poll, 1000);
    return () => { active = false; clearTimeout(timer); };
  }, [pendingJobId, loadFailed, loading]);

  async function create() {
    setCreating(true);
    setError(null);
    setLoadFailed(false);
    try { setJob(await api<Job>("/meal-plan-jobs", "POST")); }
    catch (e) { setError(e); }
    finally { setCreating(false); }
  }

  const failed = job?.status === "FAILED" || job?.status === "TIMED_OUT";
  return <>
    <header className="page-heading">
      <div><h1>Thực đơn một ngày</h1><p className="muted">Gợi ý bữa ăn theo kcal mục tiêu trong hồ sơ của bạn.</p></div>
    </header>
    <section className="panel meal-actions" aria-label="Tạo gợi ý thực đơn">
      <div className="meal-actions-row">
        <div>
          <h2>Gợi ý bữa ăn</h2>
          {targetKcal !== undefined ? <p className="muted">Mục tiêu hiện tại · {kcal.format(targetKcal)} kcal/ngày</p>
            : <p className="muted">{hasProfile ? "Cần chỉ số ước tính dành cho người trưởng thành." : "Hãy lưu hồ sơ để có mục tiêu năng lượng."}</p>}
        </div>
        {targetKcal !== undefined ? <button className="primary" onClick={create} disabled={loading || creating || !!pendingJobId || loadFailed}>
          {plan ? "Tạo gợi ý mới" : "Tạo gợi ý"}
        </button> : <button className="secondary" onClick={editProfile}>Xem hồ sơ</button>}
      </div>
      <p className="muted">Gợi ý mang tính tham khảo và không kiểm tra dị ứng, thực phẩm loại trừ hoặc ngân sách.</p>
      {loading && <p role="status">Đang tải gợi ý đã lưu…</p>}
      {!loading && !loadFailed && (creating || pendingJobId) && <p role="status">
        {job?.status === "QUEUED" ? "Đang chờ tạo gợi ý…" : "Đang tạo gợi ý…"}
      </p>}
      {failed && !creating && <div className="notice error" role="alert">
        {errors[job.errorCode ?? ""] ?? "Không thể tạo gợi ý. Vui lòng thử lại."}
        <small>Mã tra cứu {job.jobId}</small>
      </div>}
      <Notice error={error} />
      {loadFailed && <button className="secondary" onClick={() => {
        setError(null);
        setLoading(true);
        setLoadFailed(false);
        setRefresh(value => value + 1);
      }}>Tải lại gợi ý</button>}
    </section>
    {plan && <section className="panel meal-result" aria-labelledby="meal-result-heading">
      <div className="panel-heading"><h2 id="meal-result-heading">Gợi ý đã lưu</h2>
        <time dateTime={plan.date}>{plan.date.split("-").reverse().join("/")}</time>
      </div>
      <dl className="meal-totals">
        <div><dt>Kcal mục tiêu</dt><dd>{kcal.format(plan.targetKcal)} kcal</dd></div>
        <div><dt>Tổng ước tính</dt><dd>{kcal.format(plan.totalKcal)} kcal</dd></div>
      </dl>
      <p className="muted">{plan.explanation}</p>
      {plan.meals.map(meal => <section className="meal-group" key={meal.slot}>
        <h3>{mealLabels[meal.slot]}</h3>
        <ul>{meal.items.map(item => <li className="meal-item" key={item.recipeId}>
          <div><strong>{item.recipeName}</strong><small>{quantity.format(item.servings)} khẩu phần</small></div>
          <span>{kcal.format(item.kcal)} kcal</span>
        </li>)}</ul>
      </section>)}
    </section>}
    <RecipeList />
  </>;
}

function RecipeList() {
  const [recipes, setRecipes] = useState<Recipe[]>();
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  async function loadRecipes() {
    if (recipes || loading) return;
    setLoading(true);
    setError(null);
    try { setRecipes(await api<Recipe[]>("/recipes")); }
    catch (e) { setError(e); }
    finally { setLoading(false); }
  }

  return <details className="panel recipe-catalog" onToggle={event => {
    if (event.currentTarget.open) void loadRecipes();
  }}>
    <summary>Danh mục món mẫu</summary>
    <Notice error={error} />
    {error != null && <button className="secondary" onClick={loadRecipes}>Tải lại danh mục</button>}
    {loading && <p role="status">Đang tải danh mục…</p>}
    {recipes?.length === 0 && <p className="muted">Chưa có món mẫu.</p>}
    {recipes?.map(recipe => <details key={recipe.id} className="recipe">
      <summary>{recipe.name} · {kcal.format(recipe.kcalPerServing)} kcal/khẩu phần</summary>
      <p>{recipe.instructions}</p>
      <p className="muted">{recipe.source}</p>
      <ul>{recipe.ingredients.map(ingredient => <li key={ingredient.name}>
        <strong>{ingredient.name}</strong> · {quantity.format(ingredient.gramsPerServing)} g · {kcal.format(ingredient.kcalPer100g)} kcal/100 g
        <small>{ingredient.preparationState} · {ingredient.source}</small>
      </li>)}</ul>
    </details>)}
  </details>;
}
