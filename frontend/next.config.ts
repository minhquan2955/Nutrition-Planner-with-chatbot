import type { NextConfig } from "next";
const config: NextConfig = {
  output: "standalone",
  experimental: { proxyClientMaxBodySize: "26mb" },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${process.env.API_ORIGIN || "http://localhost:8080"}/api/:path*` }];
  },
};
export default config;
