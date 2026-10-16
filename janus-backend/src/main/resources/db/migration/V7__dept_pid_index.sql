-- V7: 为部门表 pid 字段增加索引，支持海量部门懒加载与下级查询性能优化
CREATE INDEX idx_sys_dept_pid ON sys_dept(pid);
