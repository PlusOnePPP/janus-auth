-- ==============================================================================
-- V1__init.sql —— 初始库表 + 演示数据（源自原 sql/init.sql 手工脚本）
-- 数据库: PostgreSQL 12+（建议 16），默认 public schema。
-- 说明: 本脚本由 Flyway 在应用启动时自动执行；已手工初始化过的存量库经
--       baseline（baseline-version=1）跳过本版本，从 V2 起继续。
-- 主键规范: 全部主键/外键为 32 位 UUID 字符串（VARCHAR(32)），由应用侧
--       MyBatis-Plus ASSIGN_UUID 生成；树形 pid 根节点哨兵值为 '0'。
-- 初始化账号: janus / janus@123（超级管理员）。
-- ==============================================================================

-- ------------------------------------------------------------------------------ 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id            VARCHAR(32)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    real_name     VARCHAR(64)  DEFAULT NULL,
    avatar        VARCHAR(255) DEFAULT NULL,
    home_path     VARCHAR(255) DEFAULT NULL,
    phone         VARCHAR(32)  DEFAULT NULL,
    email         VARCHAR(128) DEFAULT NULL,
    dept_id       VARCHAR(32)  DEFAULT NULL,
    status        SMALLINT     NOT NULL DEFAULT 1,
    remark        VARCHAR(500) DEFAULT NULL,
    intro         VARCHAR(500) DEFAULT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_user PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT uk_sys_user_phone UNIQUE (phone),
    CONSTRAINT uk_sys_user_email UNIQUE (email)
);
COMMENT ON TABLE  sys_user IS '用户表';
COMMENT ON COLUMN sys_user.id IS '用户ID(32位UUID)';
COMMENT ON COLUMN sys_user.username IS '登录名';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希(BCrypt)';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.avatar IS '头像URL';
COMMENT ON COLUMN sys_user.home_path IS '登录后首页路径';
COMMENT ON COLUMN sys_user.phone IS '手机号(手机号登录用)';
COMMENT ON COLUMN sys_user.email IS '邮箱(忘记密码用)';
COMMENT ON COLUMN sys_user.dept_id IS '部门ID';
COMMENT ON COLUMN sys_user.status IS '状态:0停用/1启用';
COMMENT ON COLUMN sys_user.remark IS '备注';
COMMENT ON COLUMN sys_user.intro IS '个人简介';

-- ------------------------------------------------------------------------------ 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          VARCHAR(32) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    code        VARCHAR(64) NOT NULL,
    status      SMALLINT    NOT NULL DEFAULT 1,
    remark      VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_role PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (code)
);
COMMENT ON TABLE  sys_role IS '角色表';
COMMENT ON COLUMN sys_role.id IS '角色ID(32位UUID)';
COMMENT ON COLUMN sys_role.name IS '角色名称';
COMMENT ON COLUMN sys_role.code IS '角色编码(super/admin/user)';
COMMENT ON COLUMN sys_role.status IS '状态:0停用/1启用';
COMMENT ON COLUMN sys_role.remark IS '备注';

-- ------------------------------------------------------------------------------ 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id VARCHAR(32) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE sys_user_role IS '用户角色关联表';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID';

-- ------------------------------------------------------------------------------ 菜单表(catalog目录/menu页面/button按钮/embedded内嵌/link外链)
CREATE TABLE IF NOT EXISTS sys_menu (
    id          VARCHAR(32) NOT NULL,
    pid         VARCHAR(32) NOT NULL DEFAULT '0',
    name        VARCHAR(64) NOT NULL,
    type        VARCHAR(16) NOT NULL DEFAULT 'menu',
    path        VARCHAR(255) DEFAULT NULL,
    component   VARCHAR(255) DEFAULT NULL,
    redirect    VARCHAR(255) DEFAULT NULL,
    auth_code   VARCHAR(128) DEFAULT NULL,
    icon        VARCHAR(128) DEFAULT NULL,
    status      SMALLINT    NOT NULL DEFAULT 1,
    sort        INT         NOT NULL DEFAULT 0,
    meta        TEXT        DEFAULT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_menu PRIMARY KEY (id),
    CONSTRAINT uk_sys_menu_name UNIQUE (name)
);
COMMENT ON TABLE  sys_menu IS '菜单表';
COMMENT ON COLUMN sys_menu.id IS '菜单ID(32位UUID)';
COMMENT ON COLUMN sys_menu.pid IS '父ID(根为''0'')';
COMMENT ON COLUMN sys_menu.name IS '路由名(唯一)';
COMMENT ON COLUMN sys_menu.type IS '类型';
COMMENT ON COLUMN sys_menu.path IS '路由路径';
COMMENT ON COLUMN sys_menu.component IS '组件:BasicLayout/IFrameView/views相对路径';
COMMENT ON COLUMN sys_menu.redirect IS '目录节点重定向目标';
COMMENT ON COLUMN sys_menu.auth_code IS '权限码(按钮型菜单使用,对应 /auth/codes)';
COMMENT ON COLUMN sys_menu.icon IS '图标(iconify名)';
COMMENT ON COLUMN sys_menu.status IS '状态:0停用/1启用';
COMMENT ON COLUMN sys_menu.sort IS '排序(小在前)';
COMMENT ON COLUMN sys_menu.meta IS '前端meta JSON对象字符串(标题/角标/affixTab等)';
COMMENT ON COLUMN sys_menu.update_time IS '更新时间';

-- ------------------------------------------------------------------------------ 角色-菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id VARCHAR(32) NOT NULL,
    menu_id VARCHAR(32) NOT NULL,
    CONSTRAINT pk_sys_role_menu PRIMARY KEY (role_id, menu_id)
);
COMMENT ON TABLE sys_role_menu IS '角色菜单关联表';
COMMENT ON COLUMN sys_role_menu.role_id IS '角色ID';
COMMENT ON COLUMN sys_role_menu.menu_id IS '菜单ID';

-- ------------------------------------------------------------------------------ 部门表
CREATE TABLE IF NOT EXISTS sys_dept (
    id          VARCHAR(32) NOT NULL,
    pid         VARCHAR(32) NOT NULL DEFAULT '0',
    name        VARCHAR(64) NOT NULL,
    status      SMALLINT    NOT NULL DEFAULT 1,
    remark      VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_dept PRIMARY KEY (id)
);
COMMENT ON TABLE  sys_dept IS '部门表';
COMMENT ON COLUMN sys_dept.id IS '部门ID(32位UUID)';
COMMENT ON COLUMN sys_dept.pid IS '父ID(根为''0'')';
COMMENT ON COLUMN sys_dept.name IS '部门名称';
COMMENT ON COLUMN sys_dept.status IS '状态:0停用/1启用';
COMMENT ON COLUMN sys_dept.remark IS '备注';

-- ------------------------------------------------------------------------------ 刷新令牌表(服务端侧refresh token登记,支持轮换与吊销)
CREATE TABLE IF NOT EXISTS sys_refresh_token (
    id         VARCHAR(32)  NOT NULL,
    user_id    VARCHAR(32)  NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    SMALLINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_sys_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS idx_sys_refresh_token_user_id ON sys_refresh_token (user_id);
COMMENT ON TABLE  sys_refresh_token IS '刷新令牌表';
COMMENT ON COLUMN sys_refresh_token.id IS '主键ID(32位UUID)';
COMMENT ON COLUMN sys_refresh_token.user_id IS '用户ID';
COMMENT ON COLUMN sys_refresh_token.token_hash IS 'token哈希(SHA-256,不存原文)';
COMMENT ON COLUMN sys_refresh_token.expires_at IS '过期时间';
COMMENT ON COLUMN sys_refresh_token.revoked IS '是否已作废:0否/1是';
COMMENT ON COLUMN sys_refresh_token.created_at IS '创建时间';

-- ------------------------------------------------------------------------------ 通知消息表
CREATE TABLE IF NOT EXISTS sys_notice (
    id          VARCHAR(32)  NOT NULL,
    title       VARCHAR(128) NOT NULL,
    message     TEXT         DEFAULT NULL,
    avatar      VARCHAR(255) DEFAULT NULL,
    link        VARCHAR(255) DEFAULT NULL,
    is_read     SMALLINT     NOT NULL DEFAULT 0,
    user_id     VARCHAR(32)  NOT NULL,
    role_id     VARCHAR(32)  DEFAULT NULL,
    type        VARCHAR(32)  DEFAULT 'info',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_notice PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_notice_user_id ON sys_notice (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_notice_role_id ON sys_notice (role_id);
COMMENT ON TABLE  sys_notice IS '通知消息表';
COMMENT ON COLUMN sys_notice.id IS '通知ID(32位UUID)';
COMMENT ON COLUMN sys_notice.title IS '标题';
COMMENT ON COLUMN sys_notice.message IS '消息内容';
COMMENT ON COLUMN sys_notice.avatar IS '头像URL';
COMMENT ON COLUMN sys_notice.link IS '跳转链接';
COMMENT ON COLUMN sys_notice.is_read IS '是否已读:0否/1是';
COMMENT ON COLUMN sys_notice.user_id IS '接收用户ID';
COMMENT ON COLUMN sys_notice.role_id IS '目标角色ID(广播用,NULL为直接发送)';
COMMENT ON COLUMN sys_notice.type IS '通知类型:info/warning/success/error';

-- ------------------------------------------------------------------------------ 系统参数配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id          VARCHAR(32)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    key         VARCHAR(100) NOT NULL,
    value       TEXT         NOT NULL,
    type        VARCHAR(20)  DEFAULT 'string',
    remark      VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_config PRIMARY KEY (id),
    CONSTRAINT uk_sys_config_key UNIQUE (key)
);
COMMENT ON TABLE  sys_config IS '系统参数配置表';
COMMENT ON COLUMN sys_config.id IS '主键ID(32位UUID)';
COMMENT ON COLUMN sys_config.name IS '参数名称';
COMMENT ON COLUMN sys_config.key IS '参数键';
COMMENT ON COLUMN sys_config.value IS '参数值';
COMMENT ON COLUMN sys_config.type IS '类型:string/number/boolean/json';
COMMENT ON COLUMN sys_config.remark IS '备注';
COMMENT ON COLUMN sys_config.update_time IS '更新时间';

-- ------------------------------------------------------------------------------ 定时任务表
CREATE TABLE IF NOT EXISTS sys_job (
    id            VARCHAR(32)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    group_name    VARCHAR(50)  DEFAULT 'DEFAULT',
    invoke_target VARCHAR(255) NOT NULL,
    cron          VARCHAR(100) NOT NULL,
    status        SMALLINT     NOT NULL DEFAULT 0,
    remark        VARCHAR(500) DEFAULT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_job PRIMARY KEY (id)
);
COMMENT ON TABLE  sys_job IS '定时任务表';
COMMENT ON COLUMN sys_job.id IS '任务ID(32位UUID)';
COMMENT ON COLUMN sys_job.name IS '任务名称';
COMMENT ON COLUMN sys_job.group_name IS '任务分组';
COMMENT ON COLUMN sys_job.invoke_target IS '调用目标(Bean.方法)';
COMMENT ON COLUMN sys_job.cron IS 'cron表达式';
COMMENT ON COLUMN sys_job.status IS '0暂停/1运行';
COMMENT ON COLUMN sys_job.remark IS '备注';

-- ------------------------------------------------------------------------------ 数据字典类型表
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id          VARCHAR(32)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(100) NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    remark      VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_dict_type PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_type_code UNIQUE (code)
);
COMMENT ON TABLE  sys_dict_type IS '字典类型表';
COMMENT ON COLUMN sys_dict_type.id IS '字典ID(32位UUID)';
COMMENT ON COLUMN sys_dict_type.name IS '字典名称';
COMMENT ON COLUMN sys_dict_type.code IS '字典编码';
COMMENT ON COLUMN sys_dict_type.status IS '状态:0停用/1启用';
COMMENT ON COLUMN sys_dict_type.remark IS '备注';
COMMENT ON COLUMN sys_dict_type.update_time IS '更新时间';

-- ------------------------------------------------------------------------------ 数据字典数据表
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id          VARCHAR(32)  NOT NULL,
    type_id     VARCHAR(32)  NOT NULL,
    label       VARCHAR(100) NOT NULL,
    value       VARCHAR(100) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    css_class   VARCHAR(100) DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_dict_data PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_dict_data_type_id ON sys_dict_data (type_id);
COMMENT ON TABLE  sys_dict_data IS '字典数据表';
COMMENT ON COLUMN sys_dict_data.id IS '数据ID(32位UUID)';
COMMENT ON COLUMN sys_dict_data.type_id IS '字典类型ID';
COMMENT ON COLUMN sys_dict_data.label IS '字典标签';
COMMENT ON COLUMN sys_dict_data.value IS '字典值';
COMMENT ON COLUMN sys_dict_data.sort IS '排序(小在前)';
COMMENT ON COLUMN sys_dict_data.status IS '状态:0停用/1启用';
COMMENT ON COLUMN sys_dict_data.css_class IS 'CSS样式';
COMMENT ON COLUMN sys_dict_data.remark IS '备注';

-- ------------------------------------------------------------------------------ 操作日志表
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id             VARCHAR(32)  NOT NULL,
    user_id        VARCHAR(32)  DEFAULT NULL,
    username       VARCHAR(64)  DEFAULT NULL,
    module         VARCHAR(64)  DEFAULT NULL,
    description    VARCHAR(255) DEFAULT NULL,
    method         VARCHAR(255) DEFAULT NULL,
    request_url    VARCHAR(255) DEFAULT NULL,
    request_method VARCHAR(16)  DEFAULT NULL,
    request_params TEXT         DEFAULT NULL,
    ip             VARCHAR(64)  DEFAULT NULL,
    status         SMALLINT     NOT NULL DEFAULT 1,
    error_msg      TEXT         DEFAULT NULL,
    cost_time      BIGINT       DEFAULT NULL,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_operation_log PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_user_id ON sys_operation_log (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_create_time ON sys_operation_log (create_time);
COMMENT ON TABLE  sys_operation_log IS '操作日志表';
COMMENT ON COLUMN sys_operation_log.id IS '日志ID(32位UUID)';
COMMENT ON COLUMN sys_operation_log.user_id IS '操作用户ID';
COMMENT ON COLUMN sys_operation_log.username IS '操作用户名';
COMMENT ON COLUMN sys_operation_log.module IS '操作模块';
COMMENT ON COLUMN sys_operation_log.description IS '操作描述';
COMMENT ON COLUMN sys_operation_log.method IS '请求方法';
COMMENT ON COLUMN sys_operation_log.request_url IS '请求URL';
COMMENT ON COLUMN sys_operation_log.request_method IS 'HTTP方法';
COMMENT ON COLUMN sys_operation_log.request_params IS '请求参数';
COMMENT ON COLUMN sys_operation_log.ip IS 'IP地址';
COMMENT ON COLUMN sys_operation_log.status IS '状态:0失败/1成功';
COMMENT ON COLUMN sys_operation_log.error_msg IS '错误信息';
COMMENT ON COLUMN sys_operation_log.cost_time IS '耗时(ms)';

-- ------------------------------------------------------------------------------ 登录日志表
CREATE TABLE IF NOT EXISTS sys_login_log (
    id          VARCHAR(32)  NOT NULL,
    user_id     VARCHAR(32)  DEFAULT NULL,
    username    VARCHAR(64)  DEFAULT NULL,
    ip          VARCHAR(64)  DEFAULT NULL,
    location    VARCHAR(255) DEFAULT NULL,
    browser     VARCHAR(128) DEFAULT NULL,
    os          VARCHAR(128) DEFAULT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    message     VARCHAR(255) DEFAULT NULL,
    login_type  VARCHAR(32)  DEFAULT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_login_log PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_user_id ON sys_login_log (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_create_time ON sys_login_log (create_time);
COMMENT ON TABLE  sys_login_log IS '登录日志表';
COMMENT ON COLUMN sys_login_log.id IS '日志ID(32位UUID)';
COMMENT ON COLUMN sys_login_log.user_id IS '登录用户ID';
COMMENT ON COLUMN sys_login_log.username IS '登录用户名';
COMMENT ON COLUMN sys_login_log.ip IS 'IP地址';
COMMENT ON COLUMN sys_login_log.location IS '登录地点';
COMMENT ON COLUMN sys_login_log.browser IS '浏览器';
COMMENT ON COLUMN sys_login_log.os IS '操作系统';
COMMENT ON COLUMN sys_login_log.status IS '状态:0失败/1成功';
COMMENT ON COLUMN sys_login_log.message IS '提示消息';
COMMENT ON COLUMN sys_login_log.login_type IS '登录方式:account/phone/qrcode/oauth';

-- ==============================================================================
-- update_time 自动刷新触发器（等价 MySQL 的 ON UPDATE CURRENT_TIMESTAMP；
-- 应用侧多数路径已手工 setUpdateTime，此处兜底保证语义一致）
-- ==============================================================================
CREATE OR REPLACE FUNCTION sys_update_timestamp() RETURNS trigger AS $$
BEGIN
    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sys_user_set_update_time ON sys_user;
CREATE TRIGGER trg_sys_user_set_update_time
    BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION sys_update_timestamp();

DROP TRIGGER IF EXISTS trg_sys_menu_set_update_time ON sys_menu;
CREATE TRIGGER trg_sys_menu_set_update_time
    BEFORE UPDATE ON sys_menu
    FOR EACH ROW EXECUTE FUNCTION sys_update_timestamp();

DROP TRIGGER IF EXISTS trg_sys_config_set_update_time ON sys_config;
CREATE TRIGGER trg_sys_config_set_update_time
    BEFORE UPDATE ON sys_config
    FOR EACH ROW EXECUTE FUNCTION sys_update_timestamp();

DROP TRIGGER IF EXISTS trg_sys_dict_type_set_update_time ON sys_dict_type;
CREATE TRIGGER trg_sys_dict_type_set_update_time
    BEFORE UPDATE ON sys_dict_type
    FOR EACH ROW EXECUTE FUNCTION sys_update_timestamp();

-- ==============================================================================
-- 演示数据（ID 为固定的 32 位种子串：<表前缀字母> + 31 位数字；
-- 业务新增数据由应用侧 ASSIGN_UUID 生成 32 位随机串，与种子互不冲突）
-- ==============================================================================

-- 角色
INSERT INTO sys_role (id, name, code, status) VALUES
('r0000000000000000000000000000001', '超级管理员', 'super', 1),
('r0000000000000000000000000000002', '管理员',     'admin', 1),
('r0000000000000000000000000000003', '普通用户',   'user',  1);

-- 用户（password_hash = BCrypt('janus@123')，超级管理员；email/phone 留空避免唯一约束占位）
INSERT INTO sys_user (id, username, password_hash, real_name, home_path, avatar, status) VALUES
('u0000000000000000000000000000001', 'janus', '$2a$10$HqNcGkvEhkWOIeNygGMwouPz/49eYBbO/gy.sv4t4LCKGH9cJqK0e', 'Janus', '/workspace', '/api/admin/avatar/janus.svg', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
('u0000000000000000000000000000001', 'r0000000000000000000000000000001');

-- 菜单（与前端实际视图对齐：各 app 通用的 Dashboard / 系统管理目录
-- （用户/角色/部门/菜单管理）+ 通知管理。组件路径对应 src/views/**/index.vue）
INSERT INTO sys_menu (id, pid, name, type, path, component, redirect, status, sort, auth_code, meta) VALUES
('m0000000000000000000000000000001', '0', 'Dashboard',  'catalog', '/dashboard',                NULL,                          '/analytics',  1, 0,  NULL,         '{"order":-1,"title":"page.dashboard.title"}'),
('m0000000000000000000000000000002', 'm0000000000000000000000000000001', 'Analytics', 'menu', '/analytics',  '/dashboard/analytics/index',  NULL,          1, 0,  NULL,         '{"affixTab":true,"title":"page.dashboard.analytics"}'),
('m0000000000000000000000000000003', 'm0000000000000000000000000000001', 'Workspace', 'menu', '/workspace',  '/dashboard/workspace/index',  NULL,          1, 1,  NULL,         '{"title":"page.dashboard.workspace"}'),
('m0000000000000000000000000000021', '0', 'Profile',    'menu',    '/profile',                  '_core/profile/index',          NULL,          1, 3,  NULL,         '{"hideInMenu":true,"icon":"lucide:user","title":"page.auth.profile"}'),
('m0000000000000000000000000000100', '0', 'System',     'catalog', '/system',                   NULL,                          '/system/user',1, 1,  NULL,         '{"icon":"lucide:settings","order":1,"title":"page.system.title"}'),
('m0000000000000000000000000000101', 'm0000000000000000000000000000100', 'SystemUser', 'menu', 'user',       '/system/user/index',          NULL,          1, 0,  NULL,         '{"icon":"lucide:user","order":0,"title":"page.system.user"}'),
('m0000000000000000000000000000102', 'm0000000000000000000000000000100', 'SystemRole', 'menu', 'role',       '/system/role/index',          NULL,          1, 1,  NULL,         '{"icon":"lucide:users","order":1,"title":"page.system.role"}'),
('m0000000000000000000000000000103', 'm0000000000000000000000000000100', 'SystemDept', 'menu', 'dept',       '/system/dept/index',          NULL,          1, 2,  NULL,         '{"icon":"lucide:building-2","order":2,"title":"page.system.dept"}'),
('m0000000000000000000000000000104', 'm0000000000000000000000000000100', 'SystemMenu', 'menu', 'menu',       '/system/menu/index',          NULL,          1, 3,  NULL,         '{"icon":"lucide:menu","order":3,"title":"page.system.menu"}'),
('m0000000000000000000000000000105', 'm0000000000000000000000000000100', 'Notice',     'menu', 'notice',     '/system/notice/index',        NULL,          1, 10, NULL,         '{"icon":"lucide:bell","order":10,"title":"page.system.notice"}'),
('m0000000000000000000000000000106', 'm0000000000000000000000000000100', 'OperationLog', 'menu', 'operation-log', '/system/operation-log/index', NULL,     1, 4,  NULL,         '{"icon":"lucide:file-text","order":4,"title":"page.system.operationLog"}'),
('m0000000000000000000000000000107', 'm0000000000000000000000000000100', 'LoginLog',   'menu', 'login-log',  '/system/login-log/index',     NULL,          1, 5,  NULL,         '{"icon":"lucide:log-in","order":5,"title":"page.system.loginLog"}'),
('m0000000000000000000000000000108', 'm0000000000000000000000000000100', 'Dict',       'menu', 'dict',       '/system/dict/index',          NULL,          1, 6,  NULL,         '{"icon":"lucide:book-open","order":6,"title":"page.system.dict"}'),
('m0000000000000000000000000000109', 'm0000000000000000000000000000108', 'DictData',   'menu', 'data/:typeId', '/system/dict/data',         NULL,          1, 0,  NULL,         '{"hideInMenu":true,"title":"字典数据"}'),
('m0000000000000000000000000001001', 'm0000000000000000000000000000101', 'SystemUserCreate', 'button', NULL, NULL,                     NULL,          1, 0,  'AC_100010',  '{"title":"新增用户"}'),
('m0000000000000000000000000001002', 'm0000000000000000000000000000101', 'SystemUserUpdate', 'button', NULL, NULL,                     NULL,          1, 1,  'AC_100020',  '{"title":"编辑用户"}'),
('m0000000000000000000000000001003', 'm0000000000000000000000000000101', 'SystemUserDelete', 'button', NULL, NULL,                     NULL,          1, 2,  'AC_100030',  '{"title":"删除用户"}'),
('m0000000000000000000000000001006', 'm0000000000000000000000000000101', 'SystemUserView',   'button', NULL, NULL,                     NULL,          1, 3,  'AC_1000000', '{"title":"查看用户"}'),
('m0000000000000000000000000001004', 'm0000000000000000000000000000102', 'SystemRoleView',   'button', NULL, NULL,                     NULL,          1, 0,  'AC_1000001', '{"title":"查看角色"}'),
('m0000000000000000000000000001005', 'm0000000000000000000000000000102', 'SystemRoleEdit',   'button', NULL, NULL,                     NULL,          1, 1,  'AC_1000002', '{"title":"编辑角色"}');

-- button 型权限码节点：业务按钮权限码请在自己的后台管理（/system/menu）中维护，对应 GET /auth/codes。
-- 前端按钮显隐（CellOperation accessCode / hasAccessByCodes）与后端 @SaCheckPermission 共用同一套码。

-- 授权关系（super/admin: 全部码；user: 用户管理只读 + 角色管理可编辑——用于演示按钮级权限）
-- user 角色持有「系统管理目录 + 用户管理页 + 查看用户码」：页面可见，但新增/编辑/删除按钮隐藏、对应接口 403；
-- 同时持有「角色管理页 + 查看/编辑角色码」：页面可见且按钮齐全。
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
-- 公共菜单：三角色一致（Dashboard 目录 + Analytics + Profile）
-- 注意：admin 的 home_path 为 /workspace，故 admin 角色必须包含工作台菜单，否则登录后 404
('r0000000000000000000000000000001','m0000000000000000000000000000001'),
('r0000000000000000000000000000001','m0000000000000000000000000000002'),
('r0000000000000000000000000000001','m0000000000000000000000000000003'),
('r0000000000000000000000000000001','m0000000000000000000000000000021'),
('r0000000000000000000000000000002','m0000000000000000000000000000001'),
('r0000000000000000000000000000002','m0000000000000000000000000000002'),
('r0000000000000000000000000000002','m0000000000000000000000000000003'),
('r0000000000000000000000000000002','m0000000000000000000000000000021'),
('r0000000000000000000000000000003','m0000000000000000000000000000001'),
('r0000000000000000000000000000003','m0000000000000000000000000000002'),
('r0000000000000000000000000000003','m0000000000000000000000000000021'),
-- 系统管理 + 通知管理：super / admin 全部页面；user 仅系统管理目录 + 用户/角色管理页
('r0000000000000000000000000000001','m0000000000000000000000000000100'),
('r0000000000000000000000000000001','m0000000000000000000000000000101'),
('r0000000000000000000000000000001','m0000000000000000000000000000102'),
('r0000000000000000000000000000001','m0000000000000000000000000000103'),
('r0000000000000000000000000000001','m0000000000000000000000000000104'),
('r0000000000000000000000000000001','m0000000000000000000000000000105'),
('r0000000000000000000000000000001','m0000000000000000000000000000106'),
('r0000000000000000000000000000001','m0000000000000000000000000000107'),
('r0000000000000000000000000000001','m0000000000000000000000000000108'),
('r0000000000000000000000000000001','m0000000000000000000000000000109'),
('r0000000000000000000000000000002','m0000000000000000000000000000100'),
('r0000000000000000000000000000002','m0000000000000000000000000000101'),
('r0000000000000000000000000000002','m0000000000000000000000000000102'),
('r0000000000000000000000000000002','m0000000000000000000000000000103'),
('r0000000000000000000000000000002','m0000000000000000000000000000104'),
('r0000000000000000000000000000002','m0000000000000000000000000000105'),
('r0000000000000000000000000000002','m0000000000000000000000000000106'),
('r0000000000000000000000000000002','m0000000000000000000000000000107'),
('r0000000000000000000000000000002','m0000000000000000000000000000108'),
('r0000000000000000000000000000002','m0000000000000000000000000000109'),
('r0000000000000000000000000000003','m0000000000000000000000000000100'),
('r0000000000000000000000000000003','m0000000000000000000000000000101'),
('r0000000000000000000000000000003','m0000000000000000000000000000102'),
-- 按钮权限：super/admin 全部；user = 用户只读(1006) + 角色查看/编辑(1004,1005)，无用户增删改码
('r0000000000000000000000000000001','m0000000000000000000000000001001'),
('r0000000000000000000000000000001','m0000000000000000000000000001002'),
('r0000000000000000000000000000001','m0000000000000000000000000001003'),
('r0000000000000000000000000000001','m0000000000000000000000000001006'),
('r0000000000000000000000000000001','m0000000000000000000000000001004'),
('r0000000000000000000000000000001','m0000000000000000000000000001005'),
('r0000000000000000000000000000002','m0000000000000000000000000001001'),
('r0000000000000000000000000000002','m0000000000000000000000000001002'),
('r0000000000000000000000000000002','m0000000000000000000000000001003'),
('r0000000000000000000000000000002','m0000000000000000000000000001006'),
('r0000000000000000000000000000002','m0000000000000000000000000001004'),
('r0000000000000000000000000000002','m0000000000000000000000000001005'),
('r0000000000000000000000000000003','m0000000000000000000000000001006'),
('r0000000000000000000000000000003','m0000000000000000000000000001004'),
('r0000000000000000000000000000003','m0000000000000000000000000001005');

-- 部门（用户管理页左侧部门树过滤器）
INSERT INTO sys_dept (id, pid, name, status, remark) VALUES
('d0000000000000000000000000000001', '0', '总公司', 1, '顶级部门');

-- 通知消息（按用户发送演示数据）
INSERT INTO sys_notice (id, title, message, avatar, link, is_read, user_id, type) VALUES
('n0000000000000000000000000000001', '欢迎使用系统', '您已成功登录系统，开始您的工作吧！', 'https://avatar.vercel.sh/vercel.svg?text=VB', '/analytics', 1, 'u0000000000000000000000000000001', 'info'),
('n0000000000000000000000000000004', '欢迎使用系统', '您已成功登录系统（管理员）。', 'https://avatar.vercel.sh/vercel.svg?text=VB', '/analytics', 0, 'u0000000000000000000000000000002', 'info'),
('n0000000000000000000000000000005', '权限变更通知', '您的管理员权限已更新，请重新登录以刷新权限。', 'https://avatar.vercel.sh/1', NULL, 0, 'u0000000000000000000000000000002', 'warning'),
('n0000000000000000000000000000006', '欢迎使用系统', '您已成功登录系统（普通用户）。', 'https://avatar.vercel.sh/vercel.svg?text=VB', '/analytics', 0, 'u0000000000000000000000000000003', 'info');

-- 系统参数配置
INSERT INTO sys_config (id, name, key, value, type, remark) VALUES
('c0000000000000000000000000000001', '系统名称', 'sys.name', '统一认证中心', 'string', '系统显示名称'),
('c0000000000000000000000000000002', '默认密码', 'sys.default-password', '123456', 'string', '新增用户/重置密码时的默认密码'),
('c0000000000000000000000000000003', '登录失败次数', 'sys.login.max-fail-count', '5', 'number', '连续登录失败次数限制'),
('c0000000000000000000000000000004', '账号锁定时长(分钟)', 'sys.login.lock-minutes', '30', 'number', '账号锁定时长'),
('c0000000000000000000000000000005', 'Token有效期(秒)', 'sys.token.timeout', '7200', 'number', 'accessToken有效期');

-- 定时任务
INSERT INTO sys_job (id, name, group_name, invoke_target, cron, status, remark) VALUES
('j0000000000000000000000000000001', '系统状态检查', 'SYSTEM', 'systemMonitorTask.checkStatus', '0 0 * * * ?', 0, '每小时检查系统状态'),
('j0000000000000000000000000000002', '清理过期Token', 'SYSTEM', 'authCleanupTask.cleanExpiredTokens', '0 30 * * * ?', 0, '每小时清理过期Token');

-- 数据字典类型
INSERT INTO sys_dict_type (id, name, code, status, remark) VALUES
('t0000000000000000000000000000001', '用户性别', 'sys_user_gender', 1, '用户性别列表'),
('t0000000000000000000000000000002', '菜单状态', 'sys_menu_status', 1, '菜单状态列表'),
('t0000000000000000000000000000003', '通知类型', 'sys_notice_type', 1, '通知类型列表');

-- 数据字典数据
INSERT INTO sys_dict_data (id, type_id, label, value, sort, status, css_class) VALUES
('o0000000000000000000000000000001', 't0000000000000000000000000000001', '男', '1', 1, 1, 'primary'),
('o0000000000000000000000000000002', 't0000000000000000000000000000001', '女', '0', 2, 1, 'danger'),
('o0000000000000000000000000000003', 't0000000000000000000000000000001', '未知', '2', 3, 1, 'info'),
('o0000000000000000000000000000004', 't0000000000000000000000000000002', '启用', '1', 1, 1, 'success'),
('o0000000000000000000000000000005', 't0000000000000000000000000000002', '停用', '0', 2, 1, 'error'),
('o0000000000000000000000000000006', 't0000000000000000000000000000003', '通知', 'info', 1, 1, 'primary'),
('o0000000000000000000000000000007', 't0000000000000000000000000000003', '成功', 'success', 2, 1, 'success'),
('o0000000000000000000000000000008', 't0000000000000000000000000000003', '警告', 'warning', 3, 1, 'warning'),
('o0000000000000000000000000000009', 't0000000000000000000000000000003', '错误', 'error', 4, 1, 'danger');

-- ==============================================================================
-- SSO 客户端注册表（客户端启动自助注册，替代认证中心 yml 静态 clients 配置）
-- 语义：先到先得 + 持有者可更新（重复注册出示相同 secret-key 才允许覆盖更新）
-- ==============================================================================
CREATE TABLE sso_client_registry (
    id          VARCHAR(32)  NOT NULL,
    client      VARCHAR(64)  NOT NULL,
    secret_key  VARCHAR(128) NOT NULL,
    client_url  VARCHAR(255) NOT NULL,
    notify_path VARCHAR(255) NOT NULL DEFAULT '/sso/janus/openApi/notify',
    app_name    VARCHAR(128),
    status      VARCHAR(20)  NOT NULL DEFAULT 'AUTO_APPROVED',
    create_time TIMESTAMP    NOT NULL,
    update_time TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sso_client_registry PRIMARY KEY (id),
    CONSTRAINT uk_sso_client_registry_client UNIQUE (client)
);

COMMENT ON TABLE  sso_client_registry IS 'SSO 客户端注册表（客户端启动自助登记，认证中心运行时热同步）';
COMMENT ON COLUMN sso_client_registry.id IS '主键ID(32位UUID)';
