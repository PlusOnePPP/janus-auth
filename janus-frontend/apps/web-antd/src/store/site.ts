import { computed, ref } from 'vue';
import { updatePreferences } from '@vben/preferences';
import { defineStore } from 'pinia';
import { getPublicSiteConfigApi, type SiteConfigVO } from '#/api/system/site';

const CACHE_KEY = 'JANUS_SITE_CONFIG_CACHE';

function loadInitialConfig(): { config: SiteConfigVO; loaded: boolean } {
  const defaultConfig: SiteConfigVO = {
    installed: true,
    systemName: '统一认证中心',
    systemDesc: '企业级独立单点登录与权限管理中心',
    logoUrl: '',
    logoDarkUrl: '',
    faviconUrl: '',
    loginTheme: 'classic-split',
    copyright: '© 2026 统一认证中心. All Rights Reserved.',
    extraSettings: '{}',
  };

  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      return {
        config: { ...defaultConfig, ...parsed, installed: !!parsed.installed },
        loaded: true,
      };
    }
  } catch {
    // 忽略解析失败
  }
  return { config: defaultConfig, loaded: false };
}

export const useSiteStore = defineStore('site', () => {
  const initial = loadInitialConfig();
  const siteConfig = ref<SiteConfigVO>(initial.config);
  const isLoaded = ref(initial.loaded);

  const isInstalled = computed(() => siteConfig.value.installed);
  const currentLoginTheme = computed(() => siteConfig.value.loginTheme || 'classic-split');

  let activeFetchPromise: Promise<SiteConfigVO> | null = null;

  // 若本地已有缓存，立即应用偏好配置，并在后台静默同步最新配置
  if (initial.loaded) {
    applyPreferences(initial.config);
    // 异步微任务静默同步
    setTimeout(() => {
      fetchSiteConfig(true);
    }, 100);
  }

  /**
   * 拉取公开站点配置并同步到全站偏好设置（带并发去重与本地持久化）
   */
  async function fetchSiteConfig(force = false) {
    if (isLoaded.value && !force) {
      return siteConfig.value;
    }
    if (activeFetchPromise && !force) {
      return activeFetchPromise;
    }

    activeFetchPromise = (async () => {
      try {
        const res: any = await getPublicSiteConfigApi();
        // 兼容 requestClient 解包后的对象，以及直接返回的 { code: 0, data: { ... } }
        const config: SiteConfigVO =
          res && res.data && typeof res.data === 'object' && !('installed' in res)
            ? res.data
            : res;

        if (config && typeof config === 'object') {
          siteConfig.value = {
            ...siteConfig.value,
            ...config,
            installed: !!config.installed,
          };
          try {
            localStorage.setItem(CACHE_KEY, JSON.stringify(siteConfig.value));
          } catch {}
          applyPreferences(siteConfig.value);
        }
      } catch (err) {
        console.warn('[SiteStore] 获取站点配置失败，使用默认配置:', err);
      } finally {
        isLoaded.value = true;
        activeFetchPromise = null;
      }
      return siteConfig.value;
    })();

    return activeFetchPromise;
  }

  function applyPreferences(config: SiteConfigVO) {
    if (config.systemName) {
      updatePreferences({
        app: {
          name: config.systemName,
        },
        copyright: {
          companyName: config.copyright || config.systemName,
        },
        logo: {
          source: config.logoUrl || '',
          sourceDark: config.logoUrl || '',
        },
      });
    }
    if (config.faviconUrl) {
      updateFavicon(config.faviconUrl);
    }
  }

  function updateFavicon(url: string) {
    let link = document.querySelector("link[rel~='icon']") as HTMLLinkElement;
    if (!link) {
      link = document.createElement('link');
      link.rel = 'icon';
      document.getElementsByTagName('head')[0]?.appendChild(link);
    }
    link.href = url;
  }

  /**
   * Pinia $reset 契约实现（登出清空 store 时被 resetAllStores 调用）
   * 站点与品牌配置为系统全局配置，用户注销退出时保留当前站点外观即可
   */
  function $reset() {
    // 保留当前站点配置
  }

  return {
    siteConfig,
    isLoaded,
    isInstalled,
    currentLoginTheme,
    fetchSiteConfig,
    applyPreferences,
    $reset,
  };
});
