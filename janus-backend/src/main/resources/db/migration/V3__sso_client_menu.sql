-- ------------------------------------------------------------------------------ 注册管理后台菜单：系统管理 -> 客户端管理（SSO 客户端注册表只读列表）
INSERT INTO sys_menu (id, pid, name, type, path, component, redirect, status, sort, auth_code, meta) VALUES
('m0000000000000000000000000000111', 'm0000000000000000000000000000100', 'SsoClient', 'menu', 'sso-client', '/system/sso-client/index', NULL, 1, 11, NULL, '{"icon":"lucide:puzzle","order":11,"title":"客户端管理"}')
ON CONFLICT (id) DO NOTHING;

-- 授权 super 与 admin 角色拥有该菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
('r0000000000000000000000000000001', 'm0000000000000000000000000000111'),
('r0000000000000000000000000000002', 'm0000000000000000000000000000111')
ON CONFLICT DO NOTHING;
