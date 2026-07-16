import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), "P2P_");
    const proxy = {
        "/api/simulator": {
            target: env.P2P_SIMULATOR_URL || "http://127.0.0.1:8081",
            changeOrigin: true,
        },
        "/api/troubleshooting": {
            target: env.P2P_INTELLIGENCE_URL || "http://127.0.0.1:8082",
            changeOrigin: true,
        },
        "/api/system": {
            target: env.P2P_INTELLIGENCE_URL || "http://127.0.0.1:8082",
            changeOrigin: true,
        },
    };

    return {
        server: {
            host: "0.0.0.0",
            port: 8080,
            strictPort: true,
            proxy,
        },
        preview: {
            host: "0.0.0.0",
            port: 8080,
            strictPort: true,
            proxy,
        },
    };
});
