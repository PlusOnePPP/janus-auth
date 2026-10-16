-- ==============================================================================
-- V8__position_and_user_dept.sql —— 职务管理与用户多部门任职
-- ==============================================================================

-- 1. 职务表（扁平化结构，纯 level_weight 权重高低驱动上下级）
CREATE TABLE IF NOT EXISTS sys_position (
    id           VARCHAR(32)  NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    level_weight INT          NOT NULL DEFAULT 0,
    sort         INT          NOT NULL DEFAULT 0,
    status       SMALLINT     NOT NULL DEFAULT 1,
    remark       VARCHAR(500) DEFAULT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_position PRIMARY KEY (id),
    CONSTRAINT uk_sys_position_code UNIQUE (code)
);
COMMENT ON TABLE  sys_position IS '职务表';
COMMENT ON COLUMN sys_position.id IS '职务ID(32位UUID)';
COMMENT ON COLUMN sys_position.name IS '职务名称';
COMMENT ON COLUMN sys_position.code IS '职务编码';
COMMENT ON COLUMN sys_position.level_weight IS '职级权重(越大级别越高，部门内按此判定老大)';
COMMENT ON COLUMN sys_position.sort IS '排序序号';
COMMENT ON COLUMN sys_position.status IS '状态:0停用/1启用';
COMMENT ON COLUMN sys_position.remark IS '备注';

DROP TRIGGER IF EXISTS trg_sys_position_set_update_time ON sys_position;
CREATE TRIGGER trg_sys_position_set_update_time
    BEFORE UPDATE ON sys_position
    FOR EACH ROW EXECUTE FUNCTION sys_update_timestamp();

-- 2. 用户-部门-职务关联表
CREATE TABLE IF NOT EXISTS sys_user_dept_position (
    id           VARCHAR(32) NOT NULL,
    user_id      VARCHAR(32) NOT NULL,
    dept_id      VARCHAR(32) NOT NULL,
    position_id  VARCHAR(32) NOT NULL,
    is_main      SMALLINT    NOT NULL DEFAULT 0,
    is_leader    SMALLINT    NOT NULL DEFAULT 0,
    create_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_user_dept_position PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_dept UNIQUE (user_id, dept_id)
);
CREATE INDEX IF NOT EXISTS idx_udp_user_id ON sys_user_dept_position (user_id);
CREATE INDEX IF NOT EXISTS idx_udp_dept_id ON sys_user_dept_position (dept_id);
CREATE INDEX IF NOT EXISTS idx_udp_position_id ON sys_user_dept_position (position_id);

COMMENT ON TABLE  sys_user_dept_position IS '用户部门职务关联表';
COMMENT ON COLUMN sys_user_dept_position.id IS '主键ID';
COMMENT ON COLUMN sys_user_dept_position.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_dept_position.dept_id IS '部门ID';
COMMENT ON COLUMN sys_user_dept_position.position_id IS '职务ID';
COMMENT ON COLUMN sys_user_dept_position.is_main IS '是否主部门:1是/0否';
COMMENT ON COLUMN sys_user_dept_position.is_leader IS '是否部门主负责人:1是/0否';

-- 3. 预置 8 个标准默认职务
INSERT INTO sys_position (id, name, code, level_weight, sort, status, remark) VALUES
('p0000000000000000000000000000001', '总经理',         'GM',          100, 1, 1, '公司最高管理职务'),
('p0000000000000000000000000000002', '副总经理',       'DGM',          90, 2, 1, '公司级副职'),
('p0000000000000000000000000000003', '一级部门经理',   'DEPT_MGR_1',   80, 3, 1, '一级部门正职负责人'),
('p0000000000000000000000000000004', '一级部门副经理', 'DEPT_DMGR_1',  70, 4, 1, '一级部门副职'),
('p0000000000000000000000000000005', '二级部门经理',   'DEPT_MGR_2',   60, 5, 1, '二级部门正职负责人'),
('p0000000000000000000000000000006', '二级部门副经理', 'DEPT_DMGR_2',  50, 6, 1, '二级部门副职'),
('p0000000000000000000000000000007', '组长',           'TEAM_LEADER',  40, 7, 1, '基层执行小组组长'),
('p0000000000000000000000000000008', '普通员工',       'STAFF',        20, 8, 1, '基础员工')
ON CONFLICT (id) DO NOTHING;

-- 4. 存量 janus 用户主部门数据迁移初始化
UPDATE sys_user 
SET dept_id = 'd0000000000000000000000000000001' 
WHERE username = 'janus' AND (dept_id IS NULL OR dept_id = '');

INSERT INTO sys_user_dept_position (id, user_id, dept_id, position_id, is_main, is_leader)
SELECT 
    'udp_janus_main_dept',
    u.id,
    u.dept_id,
    'p0000000000000000000000000000001', -- 赋予总经理职务
    1,                                  -- 主部门
    1                                   -- 部门负责人
FROM sys_user u
WHERE u.username = 'janus'
ON CONFLICT (user_id, dept_id) DO NOTHING;

-- 5. 注册菜单：系统管理 -> 职务管理 与对应权限按钮
INSERT INTO sys_menu (id, pid, name, type, path, component, redirect, status, sort, auth_code, meta) VALUES
('m0000000000000000000000000000112', 'm0000000000000000000000000000100', 'SystemPosition', 'menu', 'position', '/system/position/index', NULL, 1, 2, NULL, '{"icon":"lucide:award","order":2,"title":"page.system.position"}'),
('m0000000000000000000000000001010', 'm0000000000000000000000000000112', 'SystemPositionCreate', 'button', NULL, NULL, NULL, 1, 0, 'AC_100040', '{"title":"新增职务"}'),
('m0000000000000000000000000001011', 'm0000000000000000000000000000112', 'SystemPositionUpdate', 'button', NULL, NULL, NULL, 1, 1, 'AC_100050', '{"title":"编辑职务"}'),
('m0000000000000000000000000001012', 'm0000000000000000000000000000112', 'SystemPositionDelete', 'button', NULL, NULL, NULL, 1, 2, 'AC_100060', '{"title":"删除职务"}'),
('m0000000000000000000000000001013', 'm0000000000000000000000000000112', 'SystemPositionView',   'button', NULL, NULL, NULL, 1, 3, 'AC_1000003', '{"title":"查看职务"}')
ON CONFLICT (id) DO NOTHING;

-- 授权超级管理员与管理员
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
('r0000000000000000000000000000001', 'm0000000000000000000000000000112'),
('r0000000000000000000000000000001', 'm0000000000000000000000000001010'),
('r0000000000000000000000000000001', 'm0000000000000000000000000001011'),
('r0000000000000000000000000000001', 'm0000000000000000000000000001012'),
('r0000000000000000000000000000001', 'm0000000000000000000000000001013'),
('r0000000000000000000000000000002', 'm0000000000000000000000000000112'),
('r0000000000000000000000000000002', 'm0000000000000000000000000001010'),
('r0000000000000000000000000000002', 'm0000000000000000000000000001011'),
('r0000000000000000000000000000002', 'm0000000000000000000000000001012'),
('r0000000000000000000000000000002', 'm0000000000000000000000000001013'),
('r0000000000000000000000000000003', 'm0000000000000000000000000000112'),
('r0000000000000000000000000000003', 'm0000000000000000000000000001013')
ON CONFLICT DO NOTHING;
