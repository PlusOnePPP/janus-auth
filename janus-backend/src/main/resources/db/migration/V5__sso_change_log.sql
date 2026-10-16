-- Description: SSO 用户数据变更流水表（失效通知对账基础）
CREATE TABLE sso_change_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uids        TEXT         NOT NULL,
    dept_ids    TEXT         NOT NULL,
    role_ids    TEXT         NOT NULL,
    reason      VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_sso_change_log_created_at ON sso_change_log (created_at);

COMMENT ON TABLE sso_change_log IS 'SSO 用户数据变更流水（失效通知对账基础，按保留期清理）';
COMMENT ON COLUMN sso_change_log.id IS '全局递增序号（对账游标 seq）';
COMMENT ON COLUMN sso_change_log.uids IS '受影响用户ID集合，升序去重逗号分隔，空为空串';
COMMENT ON COLUMN sso_change_log.dept_ids IS '受影响机构ID集合，升序去重逗号分隔';
COMMENT ON COLUMN sso_change_log.role_ids IS '受影响角色ID集合，升序去重逗号分隔';
COMMENT ON COLUMN sso_change_log.reason IS '变更原因（updateUser/deleteRole/assignMenus 等）';
