import { expect, test, type Page, type Route } from "@playwright/test";

async function mockApi(page: Page, role: "USER" | "ADMIN", handle: (route: Route, path: string) => Promise<void>) {
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (path === "/auth/me") {
      return route.fulfill({ json: { id: role, email: "demo@example.com", displayName: "Tài khoản mẫu", role, sessionContext: "session" } });
    }
    if (path === "/auth/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "csrf" } });
    if (path === "/me/profile") return route.fulfill({ status: 404, json: { code: "NOT_FOUND", message: "Chưa có hồ sơ." } });
    if (route.request().method() === "POST") {
      expect(route.request().headers()).toMatchObject({ "x-csrf-token": "csrf", "x-session-context": "session" });
    }
    return handle(route, path);
  });
}

test("US07 TC-11 TC-14 TC-15 retries a question and reopens the preserved source after revocation", async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, "randomUUID", { value: undefined }));
  const summary = { id: "conversation", title: "Hội thoại mới", createdAt: "2026-11-25T09:00:00Z" };
  const source = { id: "source", title: "<img src=x>", version: "2007", pageStart: 14, pageEnd: 14, quotedText: "Gạo nếp cái", available: true };
  const messages = [
    { id: "question", role: "USER", content: "Gạo nếp cái có gì?", createdAt: summary.createdAt, sources: [] },
    { id: "answer", role: "ASSISTANT", content: "Có trong <b>bảng thành phần</b>.", createdAt: summary.createdAt, sources: [source] },
  ];
  let created = false;
  let attempts = 0;
  let requestId: string;
  await mockApi(page, "USER", async (route, path) => {
    if (path === "/conversations" && route.request().method() === "POST") {
      created = true;
      return route.fulfill({ status: 201, json: summary });
    }
    if (path === "/conversations") return route.fulfill({ json: created ? [summary] : [] });
    if (path === "/conversations/conversation") return route.fulfill({ json: { ...summary, messages: attempts === 2 ? messages : [] } });
    if (path === "/conversations/conversation/messages") {
      const submitted = route.request().postDataJSON();
      expect(submitted).toEqual({ content: "Gạo nếp cái có gì?", requestId: expect.any(String) });
      expect(submitted.requestId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
      if (attempts === 0) requestId = submitted.requestId;
      else expect(submitted.requestId).toBe(requestId);
      if (++attempts === 1) return route.fulfill({ status: 504, json: { code: "CHAT_TIMEOUT", message: "Đã hết thời gian trả lời." } });
      return route.fulfill({ json: { ...summary, messages } });
    }
    return route.fulfill({ status: 404 });
  });

  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto("/");
  await page.getByRole("button", { name: "Chatbot", exact: true }).click();
  await expect(page.getByRole("button", { name: "Tài liệu RAG" })).toHaveCount(0);
  await page.getByRole("textbox", { name: "Câu hỏi", exact: true }).fill("Gạo nếp cái có gì?");
  await page.getByRole("button", { name: "Gửi câu hỏi" }).press("Enter");
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Đã hết thời gian trả lời.");
  await expect(page.getByRole("textbox", { name: "Câu hỏi", exact: true })).toHaveValue("Gạo nếp cái có gì?");
  await page.getByRole("button", { name: "Thử gửi lại" }).press("Enter");
  await expect(page.getByText("Có trong <b>bảng thành phần</b>.", { exact: true })).toBeVisible();
  await expect(page.getByText("<img src=x>", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: /Mở PDF/ })).toHaveAttribute("href", "/api/v1/conversations/conversation/sources/source/file#page=14");
  expect(await page.locator("body").evaluate(body => body.scrollWidth)).toBeLessThanOrEqual(360);

  source.available = false;
  await page.reload();
  await page.getByRole("button", { name: "Chatbot", exact: true }).click();
  await page.getByRole("button", { name: /Hội thoại mới/ }).click();
  await expect(page.getByText("Gạo nếp cái", { exact: true })).toBeVisible();
  await expect(page.getByText(/PDF không còn mở được/)).toBeVisible();
  await expect(page.getByRole("link", { name: /Mở PDF/ })).toHaveCount(0);
});

test("US05 TC-10 TC-15 uploads a PDF for review, activates it and revokes it", async ({ page }) => {
  const doc = {
    id: "pdf", title: "Bảng thành phần", publisher: "Viện Dinh dưỡng", publicationYear: 2007,
    sourceUrl: null, version: "2007", status: "QUEUED", chunkCount: 0, errorCode: null, checksum: "a".repeat(64),
  };
  const chunks = [{ pageStart: 14, pageEnd: 14, content: "Gạo nếp cái · 1001", extractionMethod: "VERIFIED_TRANSCRIPTION" }];
  let uploaded = false;
  let detailReads = 0;
  await mockApi(page, "ADMIN", async (route, path) => {
    if (path === "/admin/rag-documents" && route.request().method() === "POST") {
      expect(route.request().headers()["content-type"]).toMatch(/^multipart\/form-data; boundary=/);
      expect(route.request().postData()).toContain('name="pages"\r\n\r\n14');
      expect(route.request().postData()).toContain('name="transcriptions"\r\n\r\n{"14":"Gạo nếp cái"}');
      uploaded = true;
      return route.fulfill({ status: 202, json: doc });
    }
    if (path === "/admin/rag-documents") return route.fulfill({ json: uploaded ? [doc] : [] });
    if (path === "/admin/rag-documents/pdf") {
      if (++detailReads === 1) return route.fulfill({ json: { ...doc, status: "PROCESSING", chunks: [] } });
      doc.status = doc.status === "QUEUED" ? "READY" : doc.status;
      doc.chunkCount = 1;
      return route.fulfill({ json: { ...doc, chunks } });
    }
    if (path.endsWith("/activate")) doc.status = "ACTIVE";
    if (path.endsWith("/revoke")) doc.status = "REVOKED";
    return route.fulfill({ json: doc });
  });

  await page.setViewportSize({ width: 768, height: 900 });
  await page.goto("/");
  await page.getByRole("button", { name: "Tài liệu RAG" }).click();
  await page.getByLabel("Tiêu đề").fill("Bảng thành phần");
  await page.getByLabel("Đơn vị phát hành").fill("Viện Dinh dưỡng");
  await page.getByLabel("Phiên bản").fill("2007");
  await page.getByLabel("Trang PDF").fill("14");
  await page.getByText("Bản chép đối chiếu (tùy chọn)", { exact: true }).click();
  await page.getByLabel("Bản chép theo trang").fill('{"14":"Gạo nếp cái"}');
  await page.getByLabel("Tệp PDF").setInputFiles({ name: "nutrition.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.7") });
  await page.getByRole("button", { name: "Nạp PDF" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Đang xử lý · 0 đoạn" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Kích hoạt" })).toHaveCount(0);
  await expect(page.getByText("Gạo nếp cái · 1001")).toBeVisible();
  await expect(page.getByRole("link", { name: /Mở PDF.*14/ })).toHaveAttribute("href", "/api/v1/admin/rag-documents/pdf/file#page=14");
  await page.getByRole("button", { name: "Kích hoạt" }).click();
  await expect(page.getByRole("button", { name: "Thu hồi" })).toBeVisible();
  page.once("dialog", dialog => void dialog.accept());
  await page.getByRole("button", { name: "Thu hồi" }).click();
  await expect(page.getByRole("heading", { name: "Bảng thành phần" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Kích hoạt" })).toHaveCount(0);
  await expect(page.getByText("Đã thu hồi").first()).toBeVisible();
  for (const width of [360, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.locator("body").evaluate(body => body.scrollWidth)).toBeLessThanOrEqual(width);
  }
});
