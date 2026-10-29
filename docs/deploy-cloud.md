# 校园设备报修系统 · 云服务器部署手册（Docker Compose 版）

目标：在一台 Ubuntu 22.04 云服务器上，用 Docker Compose 拉起
**Nginx(80) → Spring Boot(8080) → MySQL 8 / Redis 7**，浏览器 IP 直接访问。

架构与端口策略：

```
公网 ──80──> nginx ──> app(8080) ──> mysql(内网3306)
                              └────> redis(内网6379)
```

只有 Nginx 的 80 端口对外；3306/6379/8080 全部只在 compose 内网可达。

---

## 第 0 步：控制台放行端口（最常踩的坑）

登录云厂商控制台，把 **80** 端口放行（22 默认已放行）：

- 阿里云 ECS：实例 → 安全组 → 配置规则 → 入方向添加 TCP 80
- 腾讯云轻量：实例 → 防火墙 → 添加规则 TCP 80

不做这步，后面一切正常但浏览器就是打不开，别白排查。

## 第 1 步：本机打包

在 `E:\demo\repair-system` 下执行（本机默认 JDK 8 即可）：

```bash
mvn clean package -DskipTests
```

产物：`target/repair-system-1.0.0.jar`（约 40~60MB）。

## 第 2 步：上传部署包到服务器

本机打包一个 bundle（在项目根目录执行，Git Bash 没有 zip，用 tar）：

```bash
cd /e/demo/repair-system
tar -czf deploy-bundle.tar.gz Dockerfile .dockerignore docker-compose.yml .env.example \
    deploy/nginx.conf sql/init.sql target/repair-system-1.0.0.jar
```

上传 `deploy-bundle.tar.gz` 到服务器 `/opt/`（两种方式任选）：

```bash
# 方式 A：Windows 自带 scp（会提示输密码）
scp deploy-bundle.tar.gz root@<服务器公网IP>:/opt/
```

方式 B：云控制台网页的「上传文件」功能（登录服务器后的远程连接页面里一般有）。

然后在 **SSH 终端**（控制台「远程连接」或 `ssh root@<服务器公网IP>`）解压：

```bash
mkdir -p /opt/repair && cd /opt/repair
tar -xzf /opt/deploy-bundle.tar.gz
```

## 第 3 步：安装 Docker（服务器上，只需一次）

```bash
apt update && apt install -y docker.io docker-compose-v2
systemctl enable --now docker
docker --version && docker compose version   # 都能看到版本号即成功
```

> 若 `docker-compose-v2` 装不上，改用官方脚本：
> `curl -fsSL https://get.docker.com | sh`

## 第 4 步：配置密钥并启动

```bash
cd /opt/repair
cp .env.example .env
```

编辑 `.env`，把两个值改成真实随机内容（`openssl rand -hex 32` 可生成）：

```bash
vi .env    # 或用你熟悉的编辑器
```

启动（第一次会拉镜像 + 构建，约 3~8 分钟）：

```bash
docker compose up -d
docker compose ps        # 等 4 个服务都变成 running (healthy)
```

首次启动时 MySQL 容器会自动执行 `sql/init.sql` 建库建表（仅当数据卷为空时）。

## 第 5 步：验证

```bash
# 服务器本机自检
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1/     # 期望 200

# 你本地电脑浏览器打开
# http://<服务器公网IP>/        → 登录页（演示账号见 sql/init.sql，密码 123456）
# http://<服务器公网IP>/doc.html → Knife4j 接口文档
```

排查顺序：页面打不开 → 先查第 0 步端口放行；`docker compose ps` 有非 healthy
→ `docker compose logs -f app`（或 mysql/nginx）看报错。

## 以后怎么更新代码

改了代码想重新上线时：

```bash
# 1. 本机重新打包
mvn clean package -DskipTests
# 2. 只传 jar
scp target/repair-system-1.0.0.jar root@<服务器IP>:/opt/repair/target/
# 3. 服务器上重建 app 镜像并滚动更新（数据库数据不受影响）
docker compose up -d --build app
```

## 数据与注意事项

- **数据持久化**在命名卷 `mysql-data` / `redis-data` 里，重启容器、换 jar 都不丢。
- `docker compose down -v` 会**删库**，别随手执行；想重置演示数据：
  `docker compose down -v && docker compose up -d`（init.sql 重新执行）。
- `.env` 含真实密码，**不要把 `.env` 传上 GitHub**（`.env.example` 才可提交）。
- 演示完不用时可到控制台「停机不收费」（阿里云）或关机，数据保留在磁盘上。
- 想后续加域名 + HTTPS 时，只需给 nginx 服务换配置挂证书，架构不变。

## 面试可讲的点

- 容器化编排：compose 定义四服务拓扑，healthcheck + `service_healthy` 控制启动顺序
- 小内存调优：MySQL `performance-schema=OFF`、innodb buffer 128M；JVM 固定 `-Xmx512m`
  （`MaxRAMPercentage` 在 JDK 8 是实验参数，不能直接用于 Temurin 8 镜像）+ 容器 `mem_limit` 兜底
- 安全边界：数据中间件不出内网，仅反代暴露 80；密钥用环境变量注入不进镜像
- 配置外置：镜像不改，URL/密码/密钥由 compose 环境变量覆盖 `application.yml` 默认值
