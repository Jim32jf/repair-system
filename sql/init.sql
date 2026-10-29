-- =============================================
-- 校园设备报修系统 - 数据库初始化脚本
-- 执行方式：mysql -uroot -p < init.sql
-- 说明：初始化账号密码统一为 123456（BCrypt 加盐哈希存储）
-- 注意：文件必须使用 UTF-8 编码保存
-- =============================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS repair_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE repair_system;

DROP TABLE IF EXISTS evaluation;
DROP TABLE IF EXISTS order_log;
DROP TABLE IF EXISTS repair_order;
DROP TABLE IF EXISTS device_category;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_role;

-- 角色表
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_name   VARCHAR(50)  NOT NULL COMMENT '角色名称',
    role_code   VARCHAR(50)  NOT NULL COMMENT '角色编码',
    description VARCHAR(200)          DEFAULT NULL COMMENT '描述',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB COMMENT ='角色表';

-- 用户表
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加盐哈希）',
    real_name   VARCHAR(50)           DEFAULT NULL COMMENT '真实姓名',
    phone       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    role_id     BIGINT       NOT NULL COMMENT '角色ID',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role_id (role_id)
) ENGINE = InnoDB COMMENT ='用户表';

-- 设备分类表
CREATE TABLE device_category (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(50) NOT NULL COMMENT '分类名称',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT ='设备分类表';

-- 报修工单表
CREATE TABLE repair_order (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(32)  NOT NULL COMMENT '工单号',
    student_id  BIGINT       NOT NULL COMMENT '报修学生ID',
    category_id BIGINT                DEFAULT NULL COMMENT '设备分类ID',
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    location    VARCHAR(100) NOT NULL COMMENT '故障地点',
    description VARCHAR(500)          DEFAULT NULL COMMENT '故障描述',
    image_url   VARCHAR(500)          DEFAULT NULL COMMENT '图片地址',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待派单 1已派单 2处理中 3已完成 4已评价',
    repairer_id BIGINT                DEFAULT NULL COMMENT '维修工ID',
    finish_time DATETIME              DEFAULT NULL COMMENT '完成时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_student_id (student_id),
    KEY idx_repairer_id (repairer_id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='报修工单表';

-- 工单流转记录表
CREATE TABLE order_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT      NOT NULL COMMENT '工单ID',
    operator_id BIGINT      NOT NULL COMMENT '操作人ID',
    action      VARCHAR(50) NOT NULL COMMENT '操作动作',
    remark      VARCHAR(200)         DEFAULT NULL COMMENT '备注',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB COMMENT ='工单流转记录表';

-- 评价表
CREATE TABLE evaluation (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT       NOT NULL COMMENT '工单ID',
    score       TINYINT      NOT NULL COMMENT '评分：1-5',
    content     VARCHAR(500)          DEFAULT NULL COMMENT '评价内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id)
) ENGINE = InnoDB COMMENT ='评价表';

-- =============================================
-- 初始化数据
-- =============================================

INSERT INTO sys_role (role_name, role_code, description) VALUES
('学生', 'STUDENT', '提交报修、查看进度、评价'),
('维修工', 'REPAIRER', '接单、处理工单'),
('管理员', 'ADMIN', '派单、用户与分类管理');

-- 密码均为 123456，BCrypt 加盐哈希（同一明文每次哈希不同，此处为固定种子值，可直接用于初始化）
INSERT INTO sys_user (username, password, real_name, phone, role_id, status) VALUES
('admin',    '$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796', '管理员', '13800000000', 3, 1),
('student',  '$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796', '张三',   '13800000001', 1, 1),
('repairer', '$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796', '李师傅', '13800000002', 2, 1);

INSERT INTO device_category (name, sort) VALUES
('电脑', 1),
('投影仪', 2),
('空调', 3),
('灯具', 4),
('网络设备', 5);
