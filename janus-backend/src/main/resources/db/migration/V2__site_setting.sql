-- ------------------------------------------------------------------------------ 站点基础与主题配置（写入通用系统参数 KV 表 sys_config）
INSERT INTO sys_config (id, name, key, value, type, remark) VALUES
('c0000000000000000000000000000010', '系统安装状态',     'sys.installed',      'false',         'boolean', '是否已完成首次安装向导(true/false)'),
('c0000000000000000000000000000011', '系统安装时间',     'sys.installed.time', '',              'string',  '系统完成安装向导的时间'),
('c0000000000000000000000000000012', '系统描述/Slogan', 'sys.desc',           '企业级独立单点登录与权限管理中心', 'string',  '系统宣传标语/Slogan'),
('c0000000000000000000000000000013', '浅色模式Logo',     'sys.logo',           '',              'string',  '浅色背景下的 Logo URL'),
('c0000000000000000000000000000014', '深色模式Logo',     'sys.logo.dark',      '',              'string',  '深色背景下的 Logo URL'),
('c0000000000000000000000000000015', '浏览器Favicon',   'sys.favicon',        '',              'string',  '浏览器标签页 Favicon URL'),
('c0000000000000000000000000000016', '登录页主题',       'sys.login.theme',    'classic-split', 'string',  '登录页主题: classic-split/center-card/tech-futuristic/gradient-mesh'),
('c0000000000000000000000000000017', '版权声明',         'sys.copyright',      '© 2026 统一认证中心. All Rights Reserved.', 'string', '页脚版权所有声明'),
('c0000000000000000000000000000018', '系统扩展参数',     'sys.extra-settings', '{}',            'json',    '后续业务扩展参数(JSON)')
ON CONFLICT (key) DO NOTHING;

-- 注册管理后台菜单：系统管理 -> 系统配置（站点品牌与主题设置）
INSERT INTO sys_menu (id, pid, name, type, path, component, redirect, status, sort, auth_code, meta) VALUES
('m0000000000000000000000000000110', 'm0000000000000000000000000000100', 'SiteSetting', 'menu', 'site-setting', '/system/site-setting/index', NULL, 1, 9, NULL, '{"icon":"lucide:palette","order":9,"title":"系统配置"}')
ON CONFLICT (id) DO NOTHING;

-- 授权 super 与 admin 角色拥有该菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
('r0000000000000000000000000000001', 'm0000000000000000000000000000110'),
('r0000000000000000000000000000002', 'm0000000000000000000000000000110')
ON CONFLICT DO NOTHING;
