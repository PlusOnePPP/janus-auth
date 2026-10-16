-- V6: 角色表增加创建人字段
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS create_by VARCHAR(32);
COMMENT ON COLUMN sys_role.create_by IS '创建人ID';

-- 预置系统角色历史数据默认归属内置超级管理员
UPDATE sys_role SET create_by = 'u0000000000000000000000000000001' WHERE create_by IS NULL;
