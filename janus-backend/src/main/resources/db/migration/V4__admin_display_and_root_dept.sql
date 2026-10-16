-- V4: 超管显示名与根机构规范化
-- 1) 超级管理员显示姓名：Janus -> 超级管理员
-- 2) 默认机构名：总公司 -> 根机构（备注"顶级部门"维持不变）
UPDATE sys_user SET real_name = '超级管理员' WHERE username = 'janus';
UPDATE sys_dept SET name = '根机构' WHERE id = 'd0000000000000000000000000000001';
