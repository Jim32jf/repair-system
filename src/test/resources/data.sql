-- =============================================
-- 集成测试种子数据（与 sql/init.sql 同步，另加 student2 用于越权用例）
-- 固定主键，保证断言里 用户ID/角色ID/分类ID 可预期：
--   角色 STUDENT=1 / REPAIRER=2 / ADMIN=3
--   用户 admin=1 / student=2 / repairer=3 / student2=4
-- 密码均为 123456 的 BCrypt 哈希
-- =============================================

INSERT INTO sys_role (id, role_name, role_code, description) VALUES
(1, '学生', 'STUDENT', '提交报修、查看进度、评价'),
(2, '维修工', 'REPAIRER', '接单、处理工单'),
(3, '管理员', 'ADMIN', '派单、用户与分类管理');

INSERT INTO sys_user (id, username, password, real_name, phone, role_id, status) VALUES
(1, 'admin',    '$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796', '管理员', '13800000000', 3, 1),
(2, 'student',  '$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796', '张三',   '13800000001', 1, 1),
(3, 'repairer', '$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796', '李师傅', '13800000002', 2, 1),
(4, 'student2', '$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796', '李四',   '13800000003', 1, 1);

INSERT INTO device_category (id, name, sort) VALUES
(1, '电脑', 1),
(2, '投影仪', 2),
(3, '空调', 3),
(4, '灯具', 4),
(5, '网络设备', 5);
