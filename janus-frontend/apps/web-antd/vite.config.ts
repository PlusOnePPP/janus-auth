import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {
      injectAppLoading: false,
    },
    vite: {
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // 自建 Java 后端（Spring Boot 默认启用自签 HTTPS，secure: false 跳过证书校验）
            target: 'https://localhost:8899/api',
            secure: false,
            ws: true,
          },
        },
      },
    },
  };
});
