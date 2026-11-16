"use client";
import { useState, type FormEvent, type ReactNode } from "react";
import { api, ApiError, type Account, type Ingredient, type Profile, type ProfileView } from "../lib/api";

export function Notice({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="notice error" role="alert"><strong>{error instanceof Error ? error.message : "Đã xảy ra lỗi. Vui lòng thử lại."}</strong>
    {error instanceof ApiError && error.traceId && <small>Mã tra cứu: {error.traceId}</small>}</div>;
}
function Field({ name, label, errors, children, hint }: { name: string; label: string; errors?: Record<string, string>; children: ReactNode; hint?: string }) {
  return <div className="field"><label htmlFor={name}>{label}</label>{children}{hint && <small>{hint}</small>}
    {errors?.[name] && <span className="field-error" id={`${name}-error`}>{errors[name]}</span>}</div>;
}
export function AuthForm({ onLogin, notice }: { onLogin: (account: Account) => Promise<void>; notice: string }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const fields = error instanceof ApiError ? error.fieldErrors : {};
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(null);
    const data = new FormData(event.currentTarget);
    const credentials = { email: String(data.get("email")), password: String(data.get("password")) };
    try {
      if (mode === "register") await api("/auth/register", "POST", { ...credentials, displayName: data.get("displayName") });
      await onLogin(await api<Account>("/auth/login", "POST", credentials));
    } catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <section className="auth-card"><div className="eyebrow">BẮT ĐẦU TỪ CHÍNH BẠN</div>
    <h1>{mode === "login" ? "Chào mừng trở lại" : "Tạo tài khoản của bạn"}</h1>
    <p className="muted">{mode === "login" ? "Đăng nhập để tiếp tục chăm sóc dinh dưỡng mỗi ngày." : "Một nơi riêng để hiểu thể trạng và lưu hồ sơ của bạn."}</p>
    {notice && <div className="notice" role="status">{notice}</div>}<Notice error={error} />
    <form onSubmit={submit}>
      {mode === "register" && <Field name="displayName" label="Tên hiển thị" errors={fields}><input id="displayName" name="displayName" autoComplete="name" required maxLength={100} /></Field>}
      <Field name="email" label="Email" errors={fields}><input id="email" name="email" type="email" autoComplete="email" required maxLength={255} placeholder="ban@example.com" /></Field>
      <Field name="password" label="Mật khẩu" errors={fields} hint={mode === "register" ? "Ít nhất 12 ký tự, tối đa 72 byte UTF-8." : undefined}>
        <input id="password" name="password" type="password" autoComplete={mode === "login" ? "current-password" : "new-password"} required minLength={mode === "register" ? 12 : 1} maxLength={72} /></Field>
      <button className="primary full" disabled={busy}>{busy ? "Đang xử lý…" : mode === "login" ? "Đăng nhập" : "Đăng ký"}<span aria-hidden="true">→</span></button>
    </form>
    <p className="auth-switch">{mode === "login" ? "Bạn chưa có tài khoản?" : "Bạn đã có tài khoản?"} <button className="text-button" onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(null); }} disabled={busy}>{mode === "login" ? "Tạo tài khoản" : "Đăng nhập"}</button></p>
    <p className="privacy-note">Hồ sơ thuộc về bạn. Quản trị viên không được tự do xem dữ liệu sức khỏe cá nhân.</p>
  </section>;
}

export function ProfileForm({ current, ingredients, onSave, onReload }: { current: ProfileView | null; ingredients: Ingredient[]; onSave: (p: ProfileView) => void; onReload: () => Promise<void> }) {
  const p = current?.profile;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const errors = error instanceof ApiError ? error.fieldErrors : {};
  const invalid = (name: string) => ({ "aria-invalid": !!errors[name], "aria-describedby": errors[name] ? `${name}-error` : undefined });
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(null);
    const d = new FormData(event.currentTarget);
    const str = (key: string) => String(d.get(key) || "");
    const payload: Profile = {
      expectedVersion: current?.version || 0, birthDate: str("birthDate"), sexForFormula: str("sexForFormula"),
      heightCm: Number(d.get("heightCm")), weightKg: Number(d.get("weightKg")), activityLevel: str("activityLevel"), goal: str("goal"),
      selfDeclaredHealthy: d.has("selfDeclaredHealthy"), pregnant: d.has("pregnant"), breastfeeding: d.has("breastfeeding"), requiresTherapeuticDiet: d.has("requiresTherapeuticDiet"),
      dietType: str("dietType"), dailyBudget: Number(d.get("dailyBudget")), mealsPerDay: Number(d.get("mealsPerDay")),
      allergyIngredientIds: d.getAll("allergyIngredientIds").map(String), dislikedIngredientIds: d.getAll("dislikedIngredientIds").map(String), unresolvedAllergies: str("unresolvedAllergies"),
    };
    try { onSave(await api<ProfileView>("/me/profile", "PUT", payload)); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <><header className="page-heading"><div><span className="eyebrow">HIỂU CƠ THỂ BẠN</span><h1>Hồ sơ dinh dưỡng</h1><p className="muted">Thông tin hôm nay là điểm bắt đầu cho mục tiêu của bạn.</p></div><span className="pill">{current ? `Phiên bản ${current.version}` : "Hồ sơ mới"}</span></header>
    <form onSubmit={submit} className="profile-form"><Notice error={error} />
      {error instanceof ApiError && error.code === "PROFILE_CONFLICT" && <button type="button" className="secondary" onClick={async () => { try { await onReload(); setError(null); } catch (e) { setError(e); } }}>Tải lại hồ sơ</button>}
      <section className="panel"><div className="section-title"><span className="step">01</span><div><h2>Thể trạng & mục tiêu</h2><p>Chỉ số được tính từ thông tin bạn tự khai báo.</p></div></div>
        <div className="form-grid">
          <Field name="birthDate" label="Ngày sinh" errors={errors}><input {...invalid("birthDate")} id="birthDate" name="birthDate" type="date" defaultValue={p?.birthDate} required /></Field>
          <Field name="sexForFormula" label="Nhóm dùng cho công thức" errors={errors} hint="Hệ số của công thức Mifflin–St Jeor."><select id="sexForFormula" name="sexForFormula" defaultValue={p?.sexForFormula || "MALE"}><option value="MALE">Nam (+5)</option><option value="FEMALE">Nữ (−161)</option></select></Field>
          <Field name="heightCm" label="Chiều cao (cm)" errors={errors}><input {...invalid("heightCm")} id="heightCm" name="heightCm" type="number" min="0.001" max="300" step="0.001" required defaultValue={p?.heightCm} placeholder="Ví dụ: 175" /></Field>
          <Field name="weightKg" label="Cân nặng (kg)" errors={errors}><input {...invalid("weightKg")} id="weightKg" name="weightKg" type="number" min="0.001" max="500" step="0.001" required defaultValue={p?.weightKg} placeholder="Ví dụ: 70" /></Field>
          <Field name="activityLevel" label="Mức độ vận động" errors={errors}><select id="activityLevel" name="activityLevel" defaultValue={p?.activityLevel || "SEDENTARY"}><option value="SEDENTARY">Ít vận động · 1,2</option><option value="LIGHT">Nhẹ, 1–3 buổi/tuần · 1,375</option><option value="MODERATE">Vừa, 3–5 buổi/tuần · 1,55</option><option value="ACTIVE">Nhiều, 6–7 buổi/tuần · 1,725</option><option value="VERY_ACTIVE">Rất nhiều, lao động nặng · 1,9</option></select></Field>
          <Field name="goal" label="Mục tiêu" errors={errors}><select id="goal" name="goal" defaultValue={p?.goal || "MAINTAIN"}><option value="MAINTAIN">Duy trì cân nặng</option><option value="LOSE">Giảm cân</option><option value="GAIN">Tăng cân</option><option value="MUSCLE">Tăng cơ</option></select></Field>
        </div>
      </section>
      <section className="panel"><div className="section-title"><span className="step">02</span><div><h2>Thói quen ăn uống</h2><p>Ghi nhận sở thích và những thực phẩm cần loại trừ.</p></div></div>
        <div className="form-grid">
          <Field name="dietType" label="Chế độ ăn" errors={errors}><select id="dietType" name="dietType" defaultValue={p?.dietType || "OMNIVORE"}><option value="OMNIVORE">Ăn thông thường</option><option value="VEGETARIAN">Chay có trứng / sữa</option><option value="VEGAN">Thuần chay</option></select></Field>
          <Field name="mealsPerDay" label="Số bữa mỗi ngày" errors={errors}><select id="mealsPerDay" name="mealsPerDay" defaultValue={p?.mealsPerDay || 3}><option value="3">3 bữa</option><option value="4">4 bữa</option><option value="5">5 bữa</option></select></Field>
          <Field name="dailyBudget" label="Ngân sách (VND/ngày)" errors={errors}><input {...invalid("dailyBudget")} id="dailyBudget" name="dailyBudget" type="number" min="0" max="999999999" step="1" required defaultValue={p?.dailyBudget ?? 100000} /></Field>
        </div>
        {([ ["allergyIngredientIds", "Dị ứng với nguyên liệu"], ["dislikedIngredientIds", "Thực phẩm muốn loại trừ"] ] as const).map(([name, title]) => <fieldset key={name} className="ingredient-field"><legend>{title}</legend>
          <div className="checks">{ingredients.map(i => <label className="check-chip" key={i.id}><input type="checkbox" name={name} value={i.id} defaultChecked={p?.[name].includes(i.id)} />{i.name}{i.status === "INACTIVE" && " (đã ngừng sử dụng)"}</label>)}</div>
          {!ingredients.length && <p className="muted">Danh mục nguyên liệu đang được chuẩn bị. Ghi dị ứng chưa rõ vào ô bên dưới.</p>}
          {errors[name] && <p className="field-error">{errors[name]}</p>}
        </fieldset>)}
        <Field name="unresolvedAllergies" label="Dị ứng khác hoặc chưa rõ nguyên liệu" errors={errors} hint="Thông tin này sẽ được giữ lại và cần làm rõ trước khi tạo thực đơn."><textarea id="unresolvedAllergies" name="unresolvedAllergies" rows={3} maxLength={1000} defaultValue={p?.unresolvedAllergies} placeholder="Ví dụ: từng dị ứng với một món nhưng chưa biết nguyên liệu…" /></Field>
      </section>
      <section className="panel"><div className="section-title"><span className="step">03</span><div><h2>Điều kiện cá nhân hóa</h2><p>Dành cho người từ 18 tuổi, khỏe mạnh và không cần chế độ ăn điều trị.</p></div></div>
        <div className="health-checks">{([
          ["selfDeclaredHealthy", "Tôi tự khai báo đang khỏe mạnh"], ["pregnant", "Tôi đang mang thai"], ["breastfeeding", "Tôi đang cho con bú"], ["requiresTherapeuticDiet", "Tôi cần chế độ ăn điều trị"],
        ] as const).map(([name, label]) => <label key={name}><input name={name} type="checkbox" defaultChecked={p?.[name] || false} />{label}</label>)}</div>
        <p className="helper-note">Nếu ngoài phạm vi, bạn vẫn có thể lưu hồ sơ và xem kiến thức chung; hệ thống không đặt mục tiêu dinh dưỡng cá nhân.</p>
      </section>
      <div className="form-actions"><p className="muted">Mỗi lần lưu tạo một phiên bản mới.</p><button className="primary" disabled={busy}>{busy ? "Đang lưu…" : "Lưu hồ sơ và tính chỉ số"}<span aria-hidden="true">→</span></button></div>
    </form></>;
}

const fmt = (n: number, digits = 0) => n.toLocaleString("vi-VN", { maximumFractionDigits: digits });
export function Dashboard({ view, edit }: { view: ProfileView | null; edit: () => void }) {
  const m = view?.metrics;
  return <><header className="page-heading"><div><span className="eyebrow">MỘT ĐIỂM BẮT ĐẦU RÕ RÀNG</span><h1>Chỉ số của bạn</h1><p className="muted">Hiểu nhu cầu của cơ thể, từng bước một.</p></div><button className="secondary" onClick={edit}>Cập nhật hồ sơ ↗</button></header>
    {!view ? <section className="panel empty"><span className="empty-icon" aria-hidden="true">◌</span><h2>Bắt đầu bằng hồ sơ của bạn</h2><p>Chỉ cần vài thông tin để xem các chỉ số ước tính.</p><button className="primary" onClick={edit}>Tạo hồ sơ</button></section> : <>
      {!view.eligible && <section className="notice"><h2>Chỉ xem kiến thức chung</h2><p>Hồ sơ được lưu, nhưng chưa thuộc phạm vi cá nhân hóa.</p><ul>{view.eligibilityReasons.map(r => <li key={r}>{r}</li>)}</ul></section>}
      {view.needsAllergyClarification && <div className="notice warning" role="status"><strong>Cần làm rõ dị ứng</strong><p>{view.profile.unresolvedAllergies}</p><p>Chưa thể tạo thực đơn khi nguyên liệu gây dị ứng chưa được xác định.</p></div>}
      {m ? <>
        <div className="metrics-grid"><section className="target-card"><span className="eyebrow">NĂNG LƯỢNG MỤC TIÊU · ƯỚC TÍNH</span><div className="target-number">{fmt(m.targetKcal)}<span>kcal / ngày</span></div><div className="target-bottom"><span>Mục tiêu hiện tại</span><strong>{{ MAINTAIN: "Duy trì cân nặng", LOSE: "Giảm cân", GAIN: "Tăng cân", MUSCLE: "Tăng cơ" }[view.profile.goal]}</strong></div></section>
          <section className="metric-card" data-testid="metric-bmi"><span className="metric-icon" aria-hidden="true">◫</span><span>BMI</span><strong>{fmt(m.bmi, 2)}</strong><small>kg/m² · Chỉ số khối cơ thể</small></section>
          <section className="metric-card"><span className="metric-icon blue" aria-hidden="true">≈</span><span>Nước tham khảo</span><strong>{fmt(m.waterMl)} <em>ml</em></strong><small>Mỗi ngày · Ước tính đơn giản</small></section></div>
        <div className="dashboard-grid"><section className="panel"><div className="panel-heading"><h2>Dinh dưỡng đa lượng</h2><span className="pill">g / ngày</span></div><p className="muted">Phân bổ tham khảo từ mục tiêu năng lượng.</p><div className="macro-list">
          {[["Protein", m.proteinG, "protein"], ["Carbohydrate", m.carbohydrateG, "carb"], ["Chất béo", m.fatG, "fat"]].map(([label, value, color]) => <div className="macro-row" key={String(label)}><span><i className={String(color)} />{label}</span><strong>{fmt(Number(value), 1)} <small>g</small></strong></div>)}</div></section>
          <section className="panel"><h2>Năng lượng ước tính</h2><div className="energy-row"><div><strong>Năng lượng nghỉ</strong><small>REE/RMR · Mifflin–St Jeor</small></div><b>{fmt(m.estimatedBmr)} <small>kcal</small></b></div><div className="energy-row"><div><strong>Tổng tiêu hao</strong><small>TDEE · Đã tính vận động</small></div><b>{fmt(m.tdee)} <small>kcal</small></b></div><p className="helper-note">Đây là ước tính từ công thức, không phải phép đo chuyển hóa.</p></section></div>
        <section className="source-panel"><span className="source-mark" aria-hidden="true">i</span><div><h2>Biết các con số đến từ đâu</h2><p>Công thức năng lượng nghỉ: <a href="https://pubmed.ncbi.nlm.nih.gov/2305711/" target="_blank" rel="noreferrer">Mifflin–St Jeor (1990) ↗</a>. Hệ số vận động, kcal mục tiêu, macro và nước là tham số nhóm chọn cho đồ án, không phải khuyến nghị y khoa.</p><small>Quy tắc {view.ruleVersion} · Hồ sơ v{view.version} · {view.calculatedAt && new Date(view.calculatedAt).toLocaleString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" })}</small></div></section>
      </> : view.eligible && <section className="panel empty"><h2>Chưa có kết quả tính</h2><p>Lưu lại hồ sơ để tính chỉ số theo thông tin hiện tại.</p><button className="primary" onClick={edit}>Mở hồ sơ</button></section>}
    </>}
  </>;
}

export function PasswordForm({ onChanged }: { onChanged: () => void }) {
  const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(null);
  const fields = error instanceof ApiError ? error.fieldErrors : {};
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null); const d = new FormData(event.currentTarget);
    if (d.get("newPassword") !== d.get("confirm")) { setError(new Error("Mật khẩu nhập lại chưa khớp.")); return; }
    setBusy(true);
    try { await api("/auth/change-password", "POST", { currentPassword: d.get("currentPassword"), newPassword: d.get("newPassword") }); onChanged(); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <><header className="page-heading"><div><span className="eyebrow">TÀI KHOẢN CỦA BẠN</span><h1>Bảo mật tài khoản</h1><p className="muted">Đổi mật khẩu sẽ đăng xuất tất cả phiên đang mở.</p></div></header><form className="panel password-panel" onSubmit={submit}><Notice error={error} />
    <Field name="currentPassword" label="Mật khẩu hiện tại" errors={fields}><input id="currentPassword" name="currentPassword" type="password" required autoComplete="current-password" maxLength={72} /></Field>
    <Field name="newPassword" label="Mật khẩu mới" errors={fields} hint="Ít nhất 12 ký tự, tối đa 72 byte UTF-8."><input id="newPassword" name="newPassword" type="password" required autoComplete="new-password" minLength={12} maxLength={72} /></Field>
    <Field name="confirm" label="Nhập lại mật khẩu mới"><input id="confirm" name="confirm" type="password" required autoComplete="new-password" minLength={12} maxLength={72} /></Field>
    <button className="primary" disabled={busy}>{busy ? "Đang đổi…" : "Đổi mật khẩu"}</button></form></>;
}
