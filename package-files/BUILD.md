# Janus 打包流程（开发 / 构建机）

适用对象：在**有网络**的机器上出安装包的开发或构建人员。
目标产物：一个离线**安装包** `janus-install-<版本>-<日期>.tar.gz`（包名含打包日期），内含 `docker-compose.yml` 与镜像包，交给实施人员解压后即一键部署。

---

## 一键打包

打包脚本位于 `package-files/` 目录，**传入版本号**即产出安装包（最终安装包也落在 `package-files/` 下）：

```bash
cd package-files
./build.sh 1.0.0
# 产物：package-files/janus-install-1.0.0-linux-<arch>.tar.gz
```

脚本依次完成：
1. **版本对齐**：pom 版本与传入版本号不一致时，自动执行 `versions:set` 把聚合根 + 全部子模块 + 内部依赖引用连带同步为 `<版本>`（保证镜像 tag、安装包名、jar 实际版本三者一致），一致则跳过
2. `mvn` 打包后端（前端 web-antd 由 pom 插件自动构建并打进 `resources/static`）
3. `docker buildx` 出 `janus/janus:<版本>` 镜像，并 `tag janus/janus:latest`（latest 保留供开发机直接 `up`；包内 compose 固定引用 `janus/janus:<版本>`）
4. `docker pull` 上游 `postgres:14-alpine` + `redis:7-alpine`，`docker tag` 打上 `janus/` 前缀后 `docker save` 导出 `janus/janus:<版本>`、`janus/postgres:14-alpine`、`janus/redis:7-alpine` 到 `janus-images.tar`，并将 compose 镜像引用固定为 `janus/janus:<版本>` 后拷入
5. `tar czf` 打成 `janus-install-<版本>-<日期>.tar.gz`，清理临时目录

---

## 环境要求（构建机）

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | 仅用于 maven 编译后端 |
| Maven | 3.8+ | 打包后端 fat jar |
| Docker | 24+ | 打镜像、导出镜像 |
| 网络 | 需要 | 首次拉基础镜像、maven 依赖、postgres 镜像 |

> 前端（web-antd）由后端 `pom` 的 frontend-maven-plugin 在 `mvn package` 阶段自动构建并打进 `resources/static`，**构建机无需手动装 Node/pnpm**。

> 工具链路径在 `build.sh` 顶部（`JAVA_HOME` / `MVN`），如与机器不符请按需修改，或用环境变量覆盖：
> `JAVA_HOME=/path/to/jdk17 MVN=/path/to/mvn ./build.sh 1.0.0`

---

## 安装包内容

```
janus-install-1.0.0-20260917.tar.gz
└── janus-install-1.0.0-20260917/
    ├── docker-compose.yml   # 服务编排（PostgreSQL + Janus），镜像固定引用 janus:1.0.0
    └── janus-images.tar     # 离线镜像（janus/janus:1.0.0 + janus/postgres:14-alpine + janus/redis:7-alpine）
```

把 `package-files/janus-install-<版本>-<日期>-linux-<arch>.tar.gz` 这一个文件交给实施人员即可。部署步骤见 `package-files/DEPLOY.md`。

---

## 常见问题

- **`docker build` 报找不到 `janus-backend/target/*.jar`** → 第 1 步 maven 打包未成功，检查 maven 输出。
- **`docker save` 报找不到镜像** → 脚本第 3 步已 `docker pull` 上游 `postgres:14-alpine` / `redis:7-alpine`，确认构建机可联网。
- **目标机 `docker compose up` 卡在 Pulling** → 安装包漏了镜像，确认用的是本脚本产出的包（已含 `janus/postgres:14-alpine` 与 `janus/redis:7-alpine`）。
- **启动报 logback `%clr` 转换器未注册** → 已修复（logback-spring.xml 已 include Spring Boot 的 `defaults.xml`），用最新代码重跑脚本。
- **切 `prod` 连不上库** → 已内置 `application-prod.yml`（datasource 用 `${DB_HOST}` 等由 compose 注入），正常无需额外处理。
