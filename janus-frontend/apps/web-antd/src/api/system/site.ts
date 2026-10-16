import { baseRequestClient, requestClient } from '#/api/request';

export interface SiteConfigVO {
  installed: boolean;
  systemName: string;
  systemDesc?: string;
  logoUrl?: string;
  logoDarkUrl?: string;
  faviconUrl?: string;
  loginTheme?: string;
  copyright?: string;
  extraSettings?: string;
}

export interface InstallInitParams {
  systemName: string;
  systemDesc?: string;
  logoUrl?: string;
  logoDarkUrl?: string;
  faviconUrl?: string;
  loginTheme?: string;
  copyright?: string;
  adminPassword?: string;
  extraSettings?: string;
}

export interface SysSiteSetting {
  id?: string;
  systemName: string;
  systemDesc?: string;
  logoUrl?: string;
  logoDarkUrl?: string;
  faviconUrl?: string;
  loginTheme?: string;
  copyright?: string;
  installed?: boolean;
  installedTime?: string;
  extraSettings?: string;
}

/**
 * 获取公开站点配置与安装状态
 */
export async function getPublicSiteConfigApi(): Promise<SiteConfigVO> {
  return requestClient.get<SiteConfigVO>('/public/site/config');
}

/**
 * 提交首次安装向导配置
 */
export async function initInstallApi(data: InstallInitParams): Promise<void> {
  return requestClient.post('/public/install/init', data);
}

/**
 * 安装向导免登录上传 Logo
 */
export async function uploadLogoApi(file: File): Promise<{ url: string; name: string }> {
  return requestClient.upload('/public/install/upload-logo', { file });
}

/**
 * 管理端上传系统 Logo
 */
export async function uploadAdminLogoApi(file: File): Promise<{ url: string; name: string }> {
  return requestClient.upload('/admin/system/site-setting/upload-logo', { file });
}

/**
 * 管理端：获取完整站点与品牌设置
 */
export async function getAdminSiteSettingApi(): Promise<SysSiteSetting> {
  return requestClient.get<SysSiteSetting>('/admin/system/site-setting');
}

/**
 * 管理端：更新系统站点与品牌设置
 */
export async function updateAdminSiteSettingApi(data: SysSiteSetting): Promise<void> {
  return requestClient.put('/admin/system/site-setting', data);
}
