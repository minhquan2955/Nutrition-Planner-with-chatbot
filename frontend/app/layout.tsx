import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = { title: "NutriPlan · Dinh dưỡng của bạn", description: "Hồ sơ và chỉ số dinh dưỡng cá nhân hóa — đồ án nhóm 9." };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="vi"><body>{children}</body></html>;
}
