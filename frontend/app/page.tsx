"use client";
import { useEffect, useState } from "react";
import { api, ApiError, type Account, type Ingredient, type ProfileView } from "../lib/api";
import { AuthForm, Dashboard, Notice, PasswordForm, ProfileForm } from "./components";

export default function Page() {
  const [account, setAccount] = useState<Account | null>(null);
  const [profile, setProfile] = useState<ProfileView | null>(null);
  const [ingredients, setIngredients] = useState<Ingredient[]>([]);
  const [screen, setScreen] = useState<"dashboard" | "profile" | "password">("dashboard");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    const onStorage = (event: StorageEvent) => { if (event.key === "nutrition-auth-change") window.location.reload(); };
    const onSessionChanged = () => { setAccount(null); setProfile(null); setIngredients([]); setError(null); setNotice("Phiên đăng nhập đã thay đổi hoặc hết hạn. Vui lòng đăng nhập lại."); };
    window.addEventListener("storage", onStorage); window.addEventListener("nutrition-session-changed", onSessionChanged);
    return () => { window.removeEventListener("storage", onStorage); window.removeEventListener("nutrition-session-changed", onSessionChanged); };
  }, []);
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const user = await api<Account>("/auth/me");
        if (!active) return;
        setAccount(user);
        const [p, choices] = await Promise.all([
          api<ProfileView>("/me/profile").catch(e => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
          api<Ingredient[]>("/ingredients"),
        ]);
        if (active) { setProfile(p); setIngredients(choices); setScreen(p ? "dashboard" : "profile"); }
      } catch (e) { if (active && !(e instanceof ApiError && e.status === 401)) setError(e); }
      finally { if (active) setLoading(false); }
    }
    void load(); return () => { active = false; };
  }, []);
  async function loadProfile() {
    const [p, choices] = await Promise.all([
      api<ProfileView>("/me/profile").catch(e => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
      api<Ingredient[]>("/ingredients"),
    ]);
    setProfile(p); setIngredients(choices); return p;
  }
  async function login(user: Account) {
    const p = await loadProfile();
    setAccount(user); setScreen(p ? "dashboard" : "profile"); setError(null); setNotice("");
  }
  function signedOut(message = "") { setAccount(null); setProfile(null); setIngredients([]); setError(null); setNotice(message); }
  async function logout() {
    try { await api("/auth/logout", "POST"); signedOut("Bạn đã đăng xuất."); }
    catch (e) { if (e instanceof ApiError && e.status === 401) signedOut("Phiên đã hết hạn. Vui lòng đăng nhập lại."); else setError(e); }
  }
  const brand = <div className="brand"><span className="brand-icon" aria-hidden="true">↗</span><span>Nutri<span className="brand-light">Plan</span></span></div>;
  if (loading) return <main className="loading" role="status">{brand}<p>Đang tải hồ sơ của bạn…</p></main>;
  if (!account) return <main className="auth-layout"><section className="intro">{brand}<div className="intro-copy"><span className="eyebrow">DINH DƯỠNG, THEO CÁCH CỦA BẠN</span><h2>Hiểu cơ thể.<br />Chọn điều<br /><span>phù hợp.</span></h2><p>Mỗi người có một điểm bắt đầu khác nhau.<br />Hành trình của bạn bắt đầu từ một hồ sơ.</p><div className="intro-steps"><span>01 <b>Hồ sơ</b></span><i /><span>02 <b>Chỉ số</b></span><i /><span>03 <b>Mục tiêu</b></span></div></div><div className="intro-footer"><span className="small-dot" /> Dành cho người trưởng thành khỏe mạnh</div></section><section className="auth-area"><Notice error={error} /><AuthForm onLogin={login} notice={notice} /><footer>Đồ án nhóm 9 · Giai đoạn 1</footer></section></main>;
  return <div className="app-layout"><a className="skip-link" href="#content">Đến nội dung chính</a><aside className="sidebar">{brand}<div className="workspace-label">KHÔNG GIAN CỦA BẠN</div><nav aria-label="Điều hướng chính">{([
    ["dashboard", "◫", "Tổng quan"], ["profile", "◉", "Hồ sơ"], ["password", "⌑", "Mật khẩu"],
  ] as const).map(([id, icon, label]) => <button key={id} className={screen === id ? "nav-item active" : "nav-item"} aria-current={screen === id ? "page" : undefined} onClick={() => { setScreen(id); setError(null); setNotice(""); }}><span aria-hidden="true">{icon}</span>{label}{screen === id && <i />}</button>)}</nav><div className="sidebar-note"><span aria-hidden="true">✳</span><strong>Từng bước, mỗi ngày.</strong><p>Bắt đầu từ việc hiểu cơ thể và thói quen của chính mình.</p></div><div className="sidebar-footer">ĐỒ ÁN NHÓM 9 <span>PHASE 01</span></div></aside>
    <div className="main-layout"><header className="topbar"><span className="breadcrumb">Cá nhân <span>/</span> {{ dashboard: "Tổng quan", profile: "Hồ sơ", password: "Bảo mật" }[screen]}</span><div className="account"><span className="avatar" aria-hidden="true">{account.displayName.slice(0, 1).toUpperCase()}</span><div><strong>{account.displayName}</strong><small>{account.role === "ADMIN" ? "Quản trị viên" : "Tài khoản cá nhân"}</small></div><button className="text-button logout" onClick={logout}>Đăng xuất</button></div></header>
      <main id="content" className="content"><Notice error={error} />{notice && <div className="notice" role="status">{notice}</div>}
        {screen === "dashboard" && <Dashboard view={profile} edit={() => { setScreen("profile"); setNotice(""); }} />}
        {screen === "profile" && <ProfileForm key={profile?.version || 0} current={profile} ingredients={ingredients} onSave={p => { setProfile(p); setScreen("dashboard"); setNotice("Đã lưu hồ sơ. Bạn có thể tải lại trang để xem dữ liệu đã lưu."); window.scrollTo({ top: 0, behavior: "smooth" }); }} onReload={async () => { await loadProfile(); }} />}
        {screen === "password" && <PasswordForm onChanged={() => signedOut("Đã đổi mật khẩu và đăng xuất tất cả phiên. Vui lòng đăng nhập lại.")} />}
        <footer className="content-footer">NutriPlan · Thông tin tham khảo cho đồ án, không thay thế tư vấn chuyên môn.</footer>
      </main>
    </div></div>;
}
