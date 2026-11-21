import { expect, test } from "@playwright/test";

test("US01_US02_US04: đăng ký, hồ sơ, chỉ số, tải lại, đổi mật khẩu", async ({ page }) => {
  const email = `e2e-${Date.now()}@example.com`;
  await page.goto("/");
  await page.getByRole("button", { name: "Tạo tài khoản", exact: true }).click();
  await page.getByLabel("Tên hiển thị").fill("Người dùng kiểm thử");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("SecurePassword1!");
  await page.getByRole("button", { name: "Đăng ký", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Hồ sơ dinh dưỡng" })).toBeVisible();
  await page.getByLabel("Ngày sinh").fill("1996-01-01");
  await page.getByLabel("Chiều cao (cm)").fill("175");
  await page.getByLabel("Cân nặng (kg)").fill("70");
  for (const heading of ["Thói quen ăn uống", "Điều kiện cá nhân hóa"]) {
    await expect(page.getByRole("heading", { name: heading })).toHaveCount(0);
  }
  await expect(page.getByRole("checkbox")).toHaveCount(0);
  await page.getByRole("button", { name: "Lưu hồ sơ và tính chỉ số" }).click();
  await expect(page.getByRole("heading", { name: "Chỉ số của bạn" })).toBeVisible();
  await expect(page.getByTestId("metric-bmi")).toContainText("22,86");
  for (const width of [360, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/dashboard-${width}.png`, fullPage: true });
    await page.getByRole("button", { name: "Hồ sơ", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Hồ sơ dinh dưỡng" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/profile-${width}.png`, fullPage: true });
    await page.getByRole("button", { name: "Mật khẩu", exact: true }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByRole("button", { name: "Tổng quan", exact: true }).click();
  }
  await page.reload();
  await expect(page.getByTestId("metric-bmi")).toContainText("22,86");
  await page.getByRole("button", { name: "Mật khẩu", exact: true }).click();
  await page.getByLabel("Mật khẩu hiện tại").fill("SecurePassword1!");
  await page.getByLabel("Mật khẩu mới", { exact: true }).fill("ChangedPassword2!");
  await page.getByLabel("Nhập lại mật khẩu mới").fill("ChangedPassword2!");
  await page.getByRole("button", { name: "Đổi mật khẩu", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Chào mừng trở lại" })).toBeVisible();
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("ChangedPassword2!");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByTestId("metric-bmi")).toContainText("22,86");
  await page.getByRole("button", { name: "Hồ sơ", exact: true }).click();
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Xóa hồ sơ hiện tại" }).click();
  await expect(page.getByRole("status")).toContainText("Đã xóa hồ sơ hiện tại.");
  await page.reload();
  await expect(page.getByLabel("Cân nặng (kg)")).toBeEmpty();
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Chào mừng trở lại" })).toBeVisible();
});

for (const width of [360, 768, 1440]) {
  test(`NFR08: hiển thị ở ${width}px và dùng bàn phím`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Chào mừng trở lại" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByLabel("Email", { exact: true }).focus();
    await page.keyboard.press("Tab");
    await expect(page.getByLabel("Mật khẩu", { exact: true })).toBeFocused();
    await page.screenshot({ path: `test-results/auth-${width}.png`, fullPage: true });
  });
}

test("FR02_TC02: form cũ bị chặn khi cookie đổi tài khoản", async ({ page }) => {
  const suffix = Date.now();
  await page.goto("/");
  await page.getByRole("button", { name: "Tạo tài khoản", exact: true }).click();
  await page.getByLabel("Tên hiển thị").fill("Tài khoản A");
  await page.getByLabel("Email", { exact: true }).fill(`stale-a-${suffix}@example.com`);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("SecurePassword1!");
  await page.getByRole("button", { name: "Đăng ký", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Hồ sơ dinh dưỡng" })).toBeVisible();
  await page.getByLabel("Ngày sinh").fill("1996-01-01");
  await page.getByLabel("Chiều cao (cm)").fill("175");
  await page.getByLabel("Cân nặng (kg)").fill("70");
  // Simulate another tab replacing the shared cookie, even without its storage notification.
  async function csrfHeaders() {
    const csrf = await (await page.request.get("/api/v1/auth/csrf")).json();
    return { [csrf.headerName]: csrf.token };
  }
  const credentials = { email: `stale-b-${suffix}@example.com`, password: "SecurePassword1!" };
  expect((await page.request.post("/api/v1/auth/register", { headers: await csrfHeaders(), data: { ...credentials, displayName: "Tài khoản B" } })).status()).toBe(201);
  expect((await page.request.post("/api/v1/auth/login", { headers: await csrfHeaders(), data: credentials })).status()).toBe(200);
  await page.getByRole("button", { name: "Lưu hồ sơ và tính chỉ số" }).click();
  await expect(page.getByRole("heading", { name: "Chào mừng trở lại" })).toBeVisible();
  expect((await page.request.get("/api/v1/me/profile")).status()).toBe(404);
});
