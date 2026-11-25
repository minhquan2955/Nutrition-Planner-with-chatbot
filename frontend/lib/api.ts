export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fieldErrors: Record<string, string> = {}, public traceId = "") { super(message); }
}
let sessionContext = "";
export async function api<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const multipart = body instanceof FormData;
  if (body !== undefined && !multipart) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    if (sessionContext) headers["X-Session-Context"] = sessionContext;
    const response = await fetch("/api/v1/auth/csrf", { credentials: "same-origin", cache: "no-store" });
    if (!response.ok) throw new Error("Không thể lấy mã bảo vệ. Vui lòng tải lại trang.");
    const csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(`/api/v1${path}`, { method, headers, credentials: "same-origin", cache: "no-store", body: multipart ? body : JSON.stringify(body) });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: "Không kết nối được máy chủ. Vui lòng thử lại." }));
    if (error.code === "SESSION_CHANGED" || (response.status === 401 && path !== "/auth/login" && sessionContext)) {
      sessionContext = "";
      window.dispatchEvent(new Event("nutrition-session-changed"));
    }
    throw new ApiError(response.status, error.code, error.message, error.fieldErrors, error.traceId);
  }
  const result = response.status === 204 ? undefined : await response.json();
  if (path === "/auth/login" || path === "/auth/me") sessionContext = result.sessionContext;
  if (["/auth/logout", "/auth/change-password", "/auth/login"].includes(path)) {
    if (path !== "/auth/login") sessionContext = "";
    // Notification only; credentials and personal data never enter browser storage.
    try { localStorage.setItem("nutrition-auth-change", crypto.randomUUID()); } catch { /* Server context guard still applies when storage is disabled. */ }
  }
  return result as T;
}
export type Account = { id: string; email: string; displayName: string; role: "USER" | "ADMIN" };
export type Profile = {
  expectedVersion: number; birthDate: string; sexForFormula: string; heightCm: number; weightKg: number;
  activityLevel: string; goal: string;
};
export type Metrics = { bmi: number; estimatedBmr: number; tdee: number; targetKcal: number; proteinG: number; carbohydrateG: number; fatG: number; waterMl: number };
export type ProfileView = { id: string; version: number; profile: Profile; adult: boolean; metrics: Metrics | null; ruleVersion: string | null; formulaName: string | null; sourceReference: string | null; calculatedAt: string | null };
