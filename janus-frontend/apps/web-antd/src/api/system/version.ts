import { requestClient } from '#/api/request';

/** 单个版本的更新日志条目（对应后端 SystemVersionController.ChangelogEntry） */
export interface ChangelogEntry {
  content: string;
  date: string;
  title: string;
  version: string;
}

/** 系统版本信息（当前版本 + 完整更新日志列表） */
export interface VersionInfo {
  date: string;
  entries: ChangelogEntry[];
  title: string;
  version: string;
}

/**
 * 获取系统版本信息与更新日志
 * 数据来源：后端 classpath 下的 CHANGELOG.md
 */
async function getVersionInfoApi() {
  return requestClient.get<VersionInfo>('/admin/system/version');
}

export { getVersionInfoApi };
