"use client";
import { useEffect, useState } from "react";
import { api, ApiError, type Account, type ProfileView } from "../lib/api";
import { AuthForm, Dashboard, Notice, PasswordForm, ProfileForm } from "./components";
import { MealPlanScreen } from "./meal-plan";
import { Icon } from "./icons";

async function fetchProfile() {
  try { return await api<ProfileView>("/me/profile"); }
  catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error; }
}

const NAV_ITEMS = [
  { id: "dashboard", icon: "dashboard", label: "Tổng quan" },
  { id: "profile",   icon: "user",      label: "Hồ sơ"    },
  { id: "meals",     icon: "meals",     label: "Thực đơn"  },
  { id: "password",  icon: "lock",      label: "Đổi mật khẩu"  },
] as const;

type Screen = typeof NAV_ITEMS[number]["id"];

function BrandMark() {
  return (
    <div className="brand" aria-label="NutriPlan">
      <div className="brand-mark" aria-hidden="true">
        <Icon name="leaf" size={18} />
      </div>
      <span>NutriPlan</span>
    </div>
  );
}

export default function Page() {
  const [account, setAccount] = useState<Account | null>(null);
  const [profile, setProfile] = useState<ProfileView | null>(null);
  const [screen, setScreen] = useState<Screen>("dashboard");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    const onStorage = (event: StorageEvent) => { if (event.key === "nutrition-auth-change") window.location.reload(); };
    const onSessionChanged = () => { setAccount(null); setProfile(null); setError(null); setNotice("Phiên đăng nhập đã thay đổi hoặc hết hạn. Vui lòng đăng nhập lại."); };
    window.addEventListener("storage", onStorage);
    window.addEventListener("nutrition-session-changed", onSessionChanged);
    return () => { window.removeEventListener("storage", onStorage); window.removeEventListener("nutrition-session-changed", onSessionChanged); };
  }, []);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const user = await api<Account>("/auth/me");
        if (!active) return;
        setAccount(user);
        const p = await fetchProfile();
        if (active) { setProfile(p); setScreen(p ? "dashboard" : "profile"); }
      } catch (e) { if (active && !(e instanceof ApiError && e.status === 401)) setError(e); }
      finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, []);

  async function loadProfile() {
    const p = await fetchProfile();
    setProfile(p);
    return p;
  }
  async function login(user: Account) {
    const p = await loadProfile();
    setAccount(user); setScreen(p ? "dashboard" : "profile"); setError(null); setNotice("");
  }
  function signedOut(message = "") { setAccount(null); setProfile(null); setError(null); setNotice(message); }
  async function logout() {
    try { await api("/auth/logout", "POST"); signedOut("Bạn đã đăng xuất."); }
    catch (e) { if (e instanceof ApiError && e.status === 401) signedOut("Phiên đã hết hạn. Vui lòng đăng nhập lại."); else setError(e); }
  }

  if (loading) {
    return (
      <main className="loading" role="status">
        <BrandMark />
        <p>Đang tải hồ sơ của bạn…</p>
      </main>
    );
  }

  if (!account) {
    return (
      <main className="auth-layout">
        <section className="auth-intro" aria-label="Giới thiệu">
          <div>
            <BrandMark />
          </div>
          <div className="auth-intro-content">
            <h2>
              Hiểu cơ thể.<br />
              Chọn điều<br />
              <span className="accent">phù hợp.</span>
            </h2>
            <p>Mỗi người có một điểm bắt đầu khác nhau.<br />Hành trình của bạn bắt đầu từ một hồ sơ.</p>
            <div className="auth-intro-steps" role="list">
              {[["Hồ sơ", "Nhập thông tin cơ bản"], ["Chỉ số", "Xem kết quả ước tính"], ["Mục tiêu", "Theo dõi tiến trình"]].map(([step, desc], i) => (
                <div key={step} className="auth-step" role="listitem">
                  <div className="auth-step-num" aria-hidden="true">{i + 1}</div>
                  <div>
                    <strong style={{ color: "#e8ebe6", fontSize: 14 }}>{step}</strong>
                    <div style={{ color: "#616560", fontSize: 12, marginTop: 2 }}>{desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="auth-intro-footer">Dành cho người trưởng thành khỏe mạnh · Thông tin tham khảo</div>
        </section>

        <section className="auth-side">
          <div className="auth-card">
            <Notice error={error} />
            <AuthForm onLogin={login} notice={notice} />
          </div>
          <footer>Đồ án nhóm 9 · Giai đoạn 2</footer>
        </section>
      </main>
    );
  }

  const screenLabel: Record<Screen, string> = { dashboard: "Tổng quan", profile: "Hồ sơ", meals: "Thực đơn", password: "Bảo mật" };

  return (
    <div className="app-layout">
      <a className="skip-link" href="#content">Đến nội dung chính</a>

      <aside className="sidebar" aria-label="Điều hướng chính">
        <div className="sidebar-brand">
          <BrandMark />
        </div>

        <nav className="sidebar-nav" aria-label="Điều hướng">
          {NAV_ITEMS.map(({ id, icon, label }) => (
            <button
              key={id}
              className={screen === id ? "nav-item active" : "nav-item"}
              aria-current={screen === id ? "page" : undefined}
              onClick={() => { setScreen(id); setError(null); setNotice(""); }}
            >
              <span className="nav-icon"><Icon name={icon as Parameters<typeof Icon>[0]["name"]} size={18} /></span>
              {label}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <span>Đồ án nhóm 9 · Phase 02</span>
        </div>
      </aside>

      <div className="main-layout">
        <header className="topbar">
          <nav className="breadcrumb" aria-label="Vị trí hiện tại">
            <span>Cá nhân</span>
            <span className="breadcrumb-sep" aria-hidden="true">/</span>
            <span aria-current="page">{screenLabel[screen]}</span>
          </nav>

          <div className="account">
            <div className="avatar" aria-hidden="true">{account.displayName.slice(0, 1).toUpperCase()}</div>
            <div>
              <div className="account-name">{account.displayName}</div>
              <div className="account-role">{account.role === "ADMIN" ? "Quản trị viên" : "Tài khoản cá nhân"}</div>
            </div>
            <button className="logout-btn" onClick={logout} aria-label="Đăng xuất">
              <Icon name="logout" size={16} />
            </button>
          </div>
        </header>

        <main id="content" className="content">
          <Notice error={error} />
          {notice && <div className="notice" role="status">{notice}</div>}

          {screen === "dashboard" && (
            <Dashboard view={profile} edit={() => { setScreen("profile"); setNotice(""); }} />
          )}
          {screen === "profile" && (
            <ProfileForm
              key={profile?.version || 0}
              current={profile}
              onSave={p => { setProfile(p); setScreen("dashboard"); setNotice("Đã lưu hồ sơ."); window.scrollTo({ top: 0, behavior: "smooth" }); }}
              onDelete={() => { setProfile(null); setNotice("Đã xóa hồ sơ hiện tại."); }}
              onReload={async () => { await loadProfile(); }}
            />
          )}
          {screen === "meals" && (
            <MealPlanScreen
              targetKcal={profile?.metrics?.targetKcal}
              hasProfile={!!profile}
              editProfile={() => setScreen("profile")}
            />
          )}
          {screen === "password" && (
            <PasswordForm onChanged={() => signedOut("Đã đổi mật khẩu và đăng xuất tất cả phiên. Vui lòng đăng nhập lại.")} />
          )}

          <footer className="content-footer">
            NutriPlan · Thông tin tham khảo cho đồ án, không thay thế tư vấn chuyên môn.
          </footer>
        </main>
      </div>
    </div>
  );
}
