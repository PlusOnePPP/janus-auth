import type { IconifyJSON } from '@iconify/vue';

import { addCollection, addIcon } from '@vben-core/icons';

import antDesign from '@iconify/json/json/ant-design.json';
import elementPlus from '@iconify/json/json/ep.json';
import lucide from '@iconify/json/json/lucide.json';

/**
 * 离线打包的图标集数据（来自 @iconify/json）。
 * 内网环境无法访问 api.iconify.design，图标数据随构建产物本地化，
 * 图标渲染与图标选择器的列表均直接读取这里的数据。
 * 需要支持新的图标集时，在此处追加对应 JSON 并重新构建即可。
 */
const offlineCollections: Record<string, IconifyJSON> = {
  'ant-design': antDesign,
  ep: elementPlus,
  lucide,
};

/**
 * 将离线图标集注册到 @iconify/vue。
 * 必须在应用启动时（创建应用实例前）调用，
 * 调用后所有图标渲染均使用本地数据，不再向 api.iconify.design 发起请求。
 */
function setupOfflineIcons() {
  for (const collection of Object.values(offlineCollections)) {
    addCollection(collection);
  }
  // mdi 图标集体积过大（约3.4MB），只离线注册实际用到的单个图标
  addIcon('mdi:keyboard-esc', {
    body: '<path fill="currentColor" d="M1 7h6v2H3v2h4v2H3v2h4v2H1zm10 0h4v2h-4v2h2a2 2 0 0 1 2 2v2c0 1.11-.89 2-2 2H9v-2h4v-2h-2a2 2 0 0 1-2-2V9c0-1.1.9-2 2-2m8 0h2a2 2 0 0 1 2 2v1h-2V9h-2v6h2v-1h2v1c0 1.11-.89 2-2 2h-2a2 2 0 0 1-2-2V9c0-1.1.9-2 2-2"/>',
    height: 24,
    width: 24,
  });
}

export { offlineCollections, setupOfflineIcons };
