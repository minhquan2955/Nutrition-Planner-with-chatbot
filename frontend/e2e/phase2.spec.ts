import { expect, test } from "@playwright/test";

test("US06 TC05 creates a meal suggestion, shows kcal and restores it on reload", async ({ page }) => {
  let started = false;
  let complete = false;
  const jobId = "00000000-0000-0000-0000-000000000001";
  const planId = "00000000-0000-0000-0000-000000000002";
  const plan = {
    id: planId, date: "2026-11-21", targetKcal: 1900, totalKcal: 1875,
    explanation: "Chia đều các bữa.",
    meals: ["BREAKFAST", "LUNCH", "DINNER"].map(slot => ({
      slot, items: [{ recipeId: "recipe", recipeName: "Phở bò đơn giản", servings: 1.5, kcal: 625 }],
    })),
  };
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    const response = (json: unknown, status = 200) => route.fulfill({ status, json });
    if (path === "/auth/me") return response({ id: "user", displayName: "Người dùng", role: "USER", sessionContext: "session" });
    if (path === "/auth/csrf") return response({ headerName: "X-CSRF-TOKEN", token: "csrf" });
    if (path === "/me/profile") return response({
      id: "profile", version: 1, adult: true,
      profile: { goal: "MAINTAIN", activityLevel: "SEDENTARY", weightKg: 70 },
      metrics: { targetKcal: 1900, bmi: 23, estimatedBmr: 1580, tdee: 1900, proteinG: 95, fatG: 63, carbohydrateG: 237, waterMl: 2100 },
    });
    if (path === "/recipes") return response([]);
    if (path === "/meal-plan-jobs" && route.request().method() === "POST") {
      started = true;
      return response({ jobId, status: "QUEUED" }, 202);
    }
    if (path === "/meal-plan-jobs/latest" && !started) return route.fulfill({ status: 204 });
    if (path.startsWith("/meal-plan-jobs/")) {
      return response({ jobId, status: complete ? "SUCCEEDED" : "RUNNING", mealPlanId: complete ? planId : null });
    }
    if (path === "/meal-plans/latest" && !complete) return route.fulfill({ status: 204 });
    if (path.startsWith("/meal-plans/") && complete) return response(plan);
    return route.fulfill({ status: 404, json: { code: "NOT_FOUND", message: "Không tìm thấy." } });
  });

  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto("/");
  await page.getByRole("button", { name: "Thực đơn", exact: true }).click();
  const navRows = await page.getByRole("navigation", { name: "Điều hướng chính" }).getByRole("button")
    .evaluateAll(buttons => new Set(buttons.map(button => button.getBoundingClientRect().top)).size);
  expect(navRows).toBe(1);
  await page.getByRole("button", { name: "Tạo gợi ý" }).click();
  await expect(page.getByRole("status")).toContainText("Đang");
  complete = true;
  await expect(page.getByRole("heading", { name: "Bữa sáng" })).toBeVisible();
  await expect(page.getByText("1.875 kcal", { exact: true })).toBeVisible();
  await expect(page.getByText("1.900 kcal", { exact: true })).toBeVisible();
  await expect(page.getByText(/không kiểm tra dị ứng/)).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Thực đơn", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Bữa tối" })).toBeVisible();
  expect(await page.locator("body").evaluate(body => body.scrollWidth)).toBeLessThanOrEqual(360);
});
