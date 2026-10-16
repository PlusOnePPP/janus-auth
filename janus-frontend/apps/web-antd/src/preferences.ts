import {
  defineOverridesPreferences,
  definePreferencesExtension,
} from '@vben/preferences';

interface WebAntdPreferencesExtension {
  defaultTableSize: number;
  enableFormFullscreen: boolean;
  reportTitle: string;
  tenantMode: 'multi' | 'single';
}

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    name: import.meta.env.VITE_APP_TITLE,
    // 开启无感刷新（默认 false）
    enableRefreshToken: true,
    // 后端菜单模式：前端从 /menu/all 拉取动态路由
    accessMode: 'backend',
    // ↓↓↓ 不开放偏好设置给用户：关闭总开关后，header 齿轮 / 用户下拉“设置” / 固定按钮全部消失
    enablePreferences: false,
    preferencesButtonPosition: 'none',
  },
  // 版权文案：品牌统一为「统一认证中心」（登录页底部与页脚）
  copyright: {
    companyName: '统一认证中心',
  },
  // 关闭“打开偏好设置”的快捷键，避免用户仍能调出设置抽屉
  shortcutKeys: {
    globalPreferences: false,
  },
  // ↓↓↓ 在此写入你想固化的预设值（会覆盖 defaultPreferences，对所有用户生效），例如：
  // theme: { mode: 'dark', builtinType: 'default' },
  // app: { layout: 'sidebar-nav', watermark: false, colorWeakMode: false },
  // header: { hidden: false },
  // tabbar: { enable: true },

  "theme": {
    "mode": "auto"
  },
  "widget": {
    "globalSearch": false,
    "languageToggle": false,
    "timezone": false
  }

});

export const preferencesExtension =
  definePreferencesExtension<WebAntdPreferencesExtension>({
    tabLabel: 'preferences.antd.tabLabel',
    title: 'preferences.antd.title',
    fields: [
      {
        component: 'switch',
        defaultValue: true,
        key: 'enableFormFullscreen',
        label: 'preferences.antd.fields.enableFormFullscreen.label',
        tip: 'preferences.antd.fields.enableFormFullscreen.tip',
      },
      {
        component: 'select',
        defaultValue: 'single',
        key: 'tenantMode',
        label: 'preferences.antd.fields.tenantMode.label',
        options: [
          {
            label: 'preferences.antd.fields.tenantMode.options.single.label',
            value: 'single',
          },
          {
            label: 'preferences.antd.fields.tenantMode.options.multi.label',
            value: 'multi',
          },
        ],
      },
      {
        component: 'number',
        componentProps: {
          max: 200,
          min: 10,
          step: 10,
        },
        defaultValue: 20,
        key: 'defaultTableSize',
        label: 'preferences.antd.fields.defaultTableSize.label',
      },
      {
        component: 'input',
        defaultValue: '',
        key: 'reportTitle',
        label: 'preferences.antd.fields.reportTitle.label',
        placeholder: 'preferences.antd.fields.reportTitle.placeholder',
      },
    ],
  });
