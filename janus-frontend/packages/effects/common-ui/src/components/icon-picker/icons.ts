import type { Recordable } from '@vben/types';

import { offlineCollections } from '@vben/icons';

/**
 * 一个缓存对象，在不刷新页面时，无需重复解析离线图标集数据
 */
export const ICONS_MAP: Recordable<string[]> = {};

/**
 * 从本地离线图标集中获取图标名称列表，不再请求 api.iconify.design。
 * 离线数据来自 @vben/icons 的 offlineCollections（setupOfflineIcons 注册时使用同一份数据），
 * 需要支持新的图标集时，在 @vben/icons 的 offline.ts 中追加对应 JSON 即可。
 * @param prefix 图标集名称
 * @returns 图标集中包含的所有图标名称
 */
export async function fetchIconsData(prefix: string): Promise<string[]> {
  if (ICONS_MAP[prefix]?.length) {
    return ICONS_MAP[prefix];
  }
  const collection = offlineCollections[prefix];
  if (!collection) {
    console.warn(
      `[IconPicker] 图标集 ${prefix} 未离线打包，请在 @vben/icons 的 offline.ts 中补充`,
    );
    return [];
  }
  const list: string[] = [];
  // 有分类的图标集：先收集各分类下的图标
  const categories = collection.categories;
  if (categories) {
    for (const names of Object.values(categories)) {
      list.push(...names);
    }
  }
  // 收集未归入任何分类的图标（排除已隐藏图标）
  const categorized = new Set(list);
  for (const [name, icon] of Object.entries(collection.icons)) {
    if (!categorized.has(name) && !icon.hidden) {
      list.push(name);
    }
  }
  ICONS_MAP[prefix] = list.map((name) => `${prefix}:${name}`);
  return ICONS_MAP[prefix];
}
