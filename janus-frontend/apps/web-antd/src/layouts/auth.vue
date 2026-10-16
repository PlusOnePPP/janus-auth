<script lang="ts" setup>
import { computed } from 'vue';

import { AuthPageLayout } from '@vben/layouts';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';
import { useSiteStore } from '#/store';

const siteStore = useSiteStore();

const appName = computed(
  () => siteStore.siteConfig.systemName || preferences.app.name,
);
const logo = computed(
  () => siteStore.siteConfig.logoUrl || preferences.logo.source,
);
const logoSrc = computed(() => logo.value);
const pageDescription = computed(
  () => siteStore.siteConfig.systemDesc || $t('authentication.pageDesc'),
);
const copyrightText = computed(
  () =>
    siteStore.siteConfig.copyright ||
    `© ${new Date().getFullYear()} ${appName.value}`,
);
</script>

<template>
  <AuthPageLayout
    :app-name="appName"
    :logo="logo"
    :logo-dark="logo"
    :page-description="pageDescription"
    page-title=""
  >
    <!-- 品牌徽标：未配置 logo 时以主色图标占位，避免顶部只有一行裸文字 -->
    <template #logo>
      <!-- 结构与默认插槽一致：外层负责悬浮定位，内层负责间距 -->
      <div class="absolute top-0 left-0 z-10 flex flex-1">
        <div class="mt-4 ml-4 flex flex-1 items-center sm:top-6 sm:left-6">
          <div class="flex items-center gap-2.5">
            <img
              v-if="logoSrc"
              :alt="appName"
              :src="logoSrc"
              class="logo-mark"
              width="38"
            />
            <div v-else class="logo-mark logo-fallback">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1 1 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
            </div>
            <p v-if="appName" class="m-0 text-lg font-medium text-foreground">
              {{ appName }}
            </p>
          </div>
        </div>
      </div>
    </template>

    <!-- 自定义版权：Copyright 组件会拼成「Copyright © 2026 © 2026 …」，改为一行干净文案 -->
    <template #copyright>
      <p class="m-0 text-xs text-muted-foreground">{{ copyrightText }}</p>
    </template>
  </AuthPageLayout>
</template>

<style scoped>
/* ==========================================================================
   登录布局精修（仅作用于认证布局，不影响框架其他页面）
   基调：收紧宽度与间距、统一控件圆角、降噪不炫技
   ========================================================================== */

/* 品牌徽标 */
:deep(.logo-mark) {
  border-radius: 10px;
}

:deep(.logo-fallback) {
  width: 34px;
  height: 34px;
  padding: 7px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  background: hsl(var(--primary) / 8%);
  color: hsl(var(--primary));
}

/* 左侧介绍区：插画下方的定位文案弱化为次要色，主次更分明 */
:deep([class*='-enter-x'] [class*='dark:']) {
  color: hsl(var(--muted-foreground)) !important;
}

/* 背景光斑降噪：淡化为衬托，不与插画争视觉 */
:deep(.login-background) {
  opacity: 0.6;
}

/* 表单栏：收窄到 360px，视线更聚焦 */
:deep(.side-content) {
  max-width: 360px !important;
}

/* 标题：30px 粗体偏压迫，改为 22px 半粗 + 收紧间距 */
:deep(.side-content h2) {
  font-size: 22px !important;
  font-weight: 600 !important;
  letter-spacing: -0.3px !important;
  line-height: 1.4 !important;
}

:deep(.side-content h2.mb-3) {
  margin-bottom: 6px !important;
}

/* 控件圆角统一 10px */
:deep(.side-content input) {
  border-radius: 10px !important;
  /* 原生聚焦框（outline）在圆角输入框上会渲染成四个方角突出，
     必须彻底关闭，聚焦反馈改用下面的主色边框 + 圆角光晕表达 */
  outline: none !important;
}

/* 聚焦态：主色描边 + 柔和圆角光晕（:focus 与 :focus-visible 双覆盖，防浏览器差异） */
:deep(.side-content input:focus),
:deep(.side-content input:focus-visible) {
  outline: none !important;
  border-color: hsl(var(--primary) / 55%) !important;
  box-shadow: 0 0 0 3.5px hsl(var(--primary) / 14%) !important;
}

/* 登录主按钮 */
:deep(.side-content button[aria-label='login']) {
  height: 42px !important;
  border-radius: 10px !important;
  font-weight: 500 !important;
  letter-spacing: 1px;
}

/* 滑块验证条：与输入框同圆角，把手不越界 */
:deep(.side-content [class*='slider-captcha']) {
  border-radius: 10px !important;
  overflow: hidden;
}

:deep(.side-content [class*='slider-captcha'] [role='slider']) {
  border-radius: 8px !important;
}
</style>
