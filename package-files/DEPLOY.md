# Janus 部署手册

***

## 一、安装包说明

开发交付的是**一个**安装包，例如 `janus-install-1.0.0-20260917.tar.gz`（包名含打包日期）。解压后结构：

```
janus-install-1.0.0-20260917/
├── docker-compose.yml   # 服务编排（PostgreSQL + Janus）
├── install.sh           # 一键部署脚本（按宿主机架构自动选取镜像）
└── janus-images.tar     # 离线镜像（janus/janus:<版本> + janus/postgres:14-alpine + janus/redis:7-alpine，单架构）
```

***

## 二、环境要求

- 服务器：Linux（**x86\_64 或 aarch64/ARM64** 均可，安装包内含两套架构镜像，`install.sh` 自动适配）
- 已安装 **Docker 24+** 与 **docker compose 插件**（旧版用 `docker-compose` 命令亦可）
- 拿到安装包 `janus-install-<版本>-<日期>.tar.gz`

## 三、安装参数（执行前先读完本节）

`install.sh` 全部参数如下，**全部可省略**（不带参数 = 全默认）：

| 参数                 | 作用                                         | 不指定时                   |
| ------------------ | ------------------------------------------ | ---------------------- |
| （无参数）              | 全自动：数据目录用默认值，拉起 PostgreSQL + Redis + Janus | —                      |
| `--data-dir=/path` | 数据目录（logs/uploads/config/pgdata 的宿主机存放位置）  | `/opt/janus_workspace` |
| `uninstall`        | 卸载：移除 Janus 的容器与 janus/\* 镜像，不影响其他项目       | —                      |
| `--help`           | 查看以上说明                                     | —                      |

**数据目录**：默认 `/opt/janus_workspace`，数据盘不在 /opt 时安装时用 `--data-dir=/data/janus` 指定。指定后无需记忆：脚本自动把路径写入安装包目录 `.env`（后续手工 `docker compose up -d` 自动解析），升级时还会自动从旧容器挂载读取沿用。

## 四、安装步骤

```bash
# 1) 上传安装包到服务器，例如 /opt，然后解压
tar xzf janus-install-1.0.0-20260917.tar.gz -C /opt
# 得到 /opt/janus-install-1.0.0-20260917/ 目录

# 2) 进入目录，执行一键部署（加载镜像 + 预建数据目录 + 拉起 + 等启动，一条命令）
cd /opt/janus-install-1.0.0-20260917
./install.sh                       # 常规：全自动
./install.sh --data-dir=/data/janus   # 数据盘不在 /opt 时
```

> 若命令是旧版 `docker-compose`（带横杠），`install.sh` 会自动兼容。

## 五、卸载（可选）

`./install.sh uninstall` **只**停止并移除 Janus 的三个容器（`janus` / `janus-postgres` / `janus-redis`）、删除 `janus/*` 前缀镜像，**不影响本机其他项目的容器与镜像**。

```bash
cd /opt/janus-install-1.0.0-20260917
./install.sh uninstall                 # 移除容器 + 删除 janus 镜像，数据目录默认保留
JANUS_PURGE_DATA=1 ./install.sh uninstall   # 额外连数据目录一起删除（含数据库/上传/日志，慎用于生产）
```

> 默认**保留**数据目录（`pgdata`/`uploads`/`logs`/`config`），避免误删数据库。仅在确认不再需要数据时加 `JANUS_PURGE_DATA=1`。

启动后宿主机数据目录：`/opt/janus_workspace/`（含 `logs/`、`uploads/`、`config/`、`pgdata/`、`ssl/`）。
访问地址：`https://<服务器IP>:8899/`（HTTPS 由应用直接提供，见下方证书说明）

***

## 六、验证

```bash
docker compose ps                 # janus 与 janus-postgres 均为 Up
docker compose logs -f janus     # 出现 Started JanusApplication 即成功
curl -sk https://localhost:8899/ | head -n 5   # 应返回前端页面（-k 跳过自签证书校验）
```

***

## 七、访问地址

`https://<服务器IP>:8899/`

网络模式说明：

- janus 主服务为 **host 网络模式**：8899 直接监听宿主机，业务系统（同为 host 模式）通过 `127.0.0.1:8899` 直连认证中心，janus 单点登出回调业务系统也走 `127.0.0.1:<业务端口>`——全程回环地址，零机器 IP 依赖，用户浏览器访问用的 IP 只出现在访问地址里，不进任何配置。
- PostgreSQL / Redis 为 bridge 模式，分别发布宿主机端口 25432 / 26379；janus 通过 `127.0.0.1` 回环直连，这两个端口仅供外部客户端/运维使用。

***

## 八、HTTPS 证书说明

应用内置默认证书（jar 内 `janus.p12`，公司 CA 签发，有效期 10 年），开箱即用，无需任何配置：

- 浏览器首次访问出现"不安全/证书无效"提示属**正常现象**（自签证书，内网环境不做校验），点击"高级 → 继续前往"即可。
- 若办公机导入过签发该证书的公司根 CA（Fiberhome StarrySky Server CA），则无任何警告。

**替换为客户自有证书**（有正规域名/IP 证书时）：

```bash
# 1. 证书转 p12（服务器上执行；PEM 三件套 crt/key/ca 链转单文件）
mkdir -p /opt/janus_workspace/ssl
openssl pkcs12 -export -in server.crt -inkey server.key \
  -certfile ca.crt -out /opt/janus_workspace/ssl/customer.p12 -name customer

# 2. 在 docker-compose.yml 的 janus 服务 environment 增加 3 行（注意外部文件路径需带 file: 前缀），up -d 重建生效：
#      JANUS_SSL_KEY_STORE: file:/app/janus_workspace/ssl/customer.p12
#      JANUS_SSL_KEY_STORE_PASSWORD: <客户证书密码>
#      JANUS_SSL_KEY_ALIAS: customer
# 3. docker compose up -d（改了 environment 需重建容器）
```

本地调试回退 http：`JANUS_SSL_ENABLED=false`（本地裸跑 jar 时），部署包内无需改动。

***

## 九、日常运维

```bash
docker compose ps                 # 状态
docker compose logs -f janus      # 实时看应用日志
docker compose logs -f postgres   # 实时看数据库日志
docker compose stop               # 停止（保留数据）
docker compose start              # 启动
docker compose restart janus      # 仅重启应用（改配置后常用）
```

日志与文件位置（宿主机，全部在 `/opt/janus_workspace/` 下）：

- 数据库：`/opt/janus_workspace/pgdata/`
- 应用日志：`/opt/janus_workspace/logs/janus.log`（含按天滚动归档）
- 上传文件：`/opt/janus_workspace/uploads/`
- 外挂配置：`/opt/janus_workspace/config/`

***

## 十、配置修改（无需重打包）

优先级：外挂 yml ＞ 环境变量 ＞ jar 内置默认值。

**方式 A：环境变量**（最常用）——编辑 `docker-compose.yml` 的 `janus.environment`，例如改密码或接外部库：

```yaml
  janus:
    environment:
      DB_HOST: 127.0.0.1        # host 模式回环直连 postgres 发布的宿主机端口
      DB_PORT: 25432
      DB_USERNAME: janus
      DB_PASSWORD: janus123
      DB_NAME: janus_admin
      SPRING_PROFILES_ACTIVE: prod
```

改完生效：`docker compose up -d`

**方式 B：外挂 yml**（改大片配置时）——在 `/opt/janus_workspace/config/application-prod.yml` 写覆盖项，例如：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:janus_admin}
    username: ${DB_USERNAME:janus}
    password: ${DB_PASSWORD:janus123}
janus:
  auth:
    sms-mock: false
    email-mock: false
```

改完生效：`docker compose restart janus`

***

## 十一、版本升级

```bash
# 0) （可选）旧版容器若仍在，install.sh 在 [4/5] 步会自动检测并移除同名旧容器，
#    无需手动处理；此处仅作等价说明。容器名固定为 janus / janus-postgres / janus-redis，
#    任意目录按名删除即可，数据目录（默认 /opt/janus_workspace，自定义时 install.sh 自动沿用）不动。
# docker rm -f janus janus-postgres janus-redis

# 1) 解压新包并一键部署（install.sh 会自动识别升级/重装并保留数据）
tar xzf janus-install-1.1.0-20260917.tar.gz -C /opt
cd /opt/janus-install-1.1.0-20260917
./install.sh

# 2) （数据目录固定，库/上传/日志全部保留，install.sh 内已拉起；此处仅确认状态）
docker compose ps
```

数据全部在固定的数据目录（默认 `/opt/janus_workspace/`，自定义时 install.sh 自动沿用，见「安装参数」；含 `pgdata/` 库数据、`logs/`、`uploads/`、`config/`），与解压目录无关，升级只重建容器、不碰数据。新版本带 Flyway 脚本时启动自动迁移。

***

## 十二、回滚

保留历史安装包即可：

```bash
# 先停当前版本（容器名固定，任意目录按名删除即可，无需进原部署目录）
docker rm -f janus janus-postgres janus-redis

# 解压并重载旧版本
tar xzf janus-install-1.0.0-20260917.tar.gz -C /opt
cd /opt/janus-install-1.0.0-20260917
./install.sh
```

> Flyway 只向前迁移。回滚应用版本后若库已执行过更高版本脚本，可能需 DBA 手工处理库结构，请先备份。

***

## 十三、数据备份

| 数据      | 位置                                           | 备份                                                                     |
| ------- | -------------------------------------------- | ---------------------------------------------------------------------- |
| 数据库     | `/opt/janus_workspace/pgdata/`               | `docker exec janus-postgres pg_dump -U janus janus_admin > backup.sql` |
| 应用文件/日志 | `/opt/janus_workspace/`（logs、uploads、config） | `tar czf ws.tgz -C /opt janus_workspace`                               |

***

## 十四、常见问题

| 现象                                                        | 原因                                      | 处理                                                                                            |
| --------------------------------------------------------- | --------------------------------------- | --------------------------------------------------------------------------------------------- |
| `up` 卡在 Pulling                                           | 离线包漏了镜像                                 | 重新解压并 `docker load` 完整包，确认含 `janus/postgres:14-alpine` 与 `janus/redis:7-alpine`               |
| 连不上库                                                      | PG 未就绪 / `DB_*` 错                       | 等健康检查通过；`docker compose logs postgres` 查库；确认 `DB_HOST=127.0.0.1`、`DB_PORT=25432`                               |
| `platform (linux/amd64) does not match ... (linux/arm64)` | 安装包内镜像架构与宿主机不符（常见于 arm64 包里误装 amd64 镜像） | 用匹配架构的包，或在目标架构机器上 `cd package-files && ./build.sh <版本> <架构>` 重新出包；`install.sh` 现已会在加载后校验并明确报错 |
| 8899 被占用（address already in use）                          | 端口冲突                                    | 见下方「意外情况：8899 端口被占用」                                                                          |

### 意外情况：8899 端口被占用（address already in use）

`./install.sh` 或 `docker compose up -d` 拉起 janus 容器时失败，应用日志（`docker logs janus`）中出现：

```text
... java.net.BindException: Address already in use
```

含义：janus 为 **host 网络模式**，8899 直接监听宿主机端口；宿主机的 8899 已被其他进程或容器占用，janus 进程绑定失败。先排查占用者，再按情况处理：

```bash
# 1) 看谁占着 8899
ss -lntp | grep 8899                 # 或 netstat -lntp | grep 8899
docker ps -a --filter name=janus     # 最常见：旧的 janus 容器还在运行（容器名固定，重装/换目录部署时新旧目录冲突）
```

按占用者分两种情况处理：

- **占用者是旧的 janus 容器**（最常见，升级/换目录重装时容易出现）：删除旧容器后重新拉起，数据目录不受影响：
  ```bash
  docker rm -f janus janus-postgres janus-redis   # 只删 janus 相关容器
  docker compose up -d
  ```
- **占用者是宿主机上其他服务**：host 模式没有端口映射，直接在 `docker-compose.yml` 的 janus 服务 `environment` 中增加一行改应用监听端口，保存后重建容器：
  ```yaml
  janus:
    environment:
      SERVER_PORT: 8898        # 应用改为监听宿主机 8898
  ```
  ```bash
  docker compose up -d
  ```
  此时访问地址变为 `https://<服务器IP>:8898/`；已对接本认证中心的业务系统中配置的 Janus 地址需同步更新。

> 注意：修改 docker-compose.yml 后必须用 `docker compose up -d` 重建容器才能生效，`docker restart janus` 仍沿用旧容器的端口与环境配置；若 `up -d` 仍报同样的错误，先 `docker rm -f janus` 再 `up -d`。

***

## 十五、Docker 镜像与清理常用命令

本系统涉及的镜像均为 `janus/` 前缀（`janus/janus`、 `janus/postgres:14-alpine`、 `janus/redis:7-alpine`）。以下命令**全部只作用于 janus 相关镜像与容器，不会影响本机其他项目的资源**：

```bash
# 1) 查看（查看不删除，可全局看，但筛选只看 janus）
docker images 'janus/*'               # 只列出 janus/ 前缀镜像
docker ps -a --filter name=janus      # 只列出 janus 相关容器（janus / janus-postgres / janus-redis）

# 2) 删除指定 janus 镜像（版本号按实际）
docker rmi janus/janus:1.0.0
docker rmi janus/postgres:14-alpine janus/redis:7-alpine

# 3) 删除所有 janus/ 开头的镜像（最常用，重打镜像/清场时）——
#    用 'janus/*' 过滤，绝不波及 janus 之外的镜像
docker rmi -f $(docker images 'janus/*' -q)

# 4) 镜像被容器占用时，先只停/删 janus 容器，再删 janus 镜像
docker compose down                  # 只停并删本目录编排的 janus 容器（不删数据卷）
# 或按容器名精确删除（只这三个，不影响其他容器）：
docker rm -f janus janus-postgres janus-redis
docker rmi -f $(docker images 'janus/*' -q)   # 再删 janus 镜像
```

> 提示：`docker rmi -f` 仅强制删除镜像，**不会删容器**；若镜像是某个运行中容器在用，删镜像后容器仍可读原层，但重建会失败。彻底清理请先 `docker compose down` 或 `docker rm -f janus janus-postgres janus-redis`。
>
> ⚠️ 避免使用 `docker image prune` / `docker image prune -a` / `docker container prune` / `docker builder prune` 这类**不带过滤**的全局清理命令——它们会删除本机**所有**项目（非 janus）的悬空/未用镜像、已停止容器和构建缓存。如确需全局清理，请明确知道影响范围后单独执行，不要混在本节 janus 流程里。

***

## 十六、生产环境 Checklist

- [ ] 修改 `postgres` 的 `POSTGRES_PASSWORD` 为强密码，同步改 `janus.environment` 的 `DB_PASSWORD`
- [ ] 修改 `redis` 的 `REDIS_PASSWORD`（compose 内 `${REDIS_PASSWORD:-janus@123}` 默认值），同步改 `janus.environment` 的 `SPRING_DATA_REDIS_PASSWORD`（两者须一致，否则 janus 连不上 redis）
- [ ] 数据库对外端口为 25432（非默认 5432，避免冲突）；上线前确认防火墙仅对必要来源放行该端口
- [ ] redis 对外端口为 26379（非默认 6379，避免冲突）；仅运维/排障需要开放
- [ ] 已接真实短信/邮件服务，或显式置 `JANUS_AUTH_SMS_MOCK/EMAIL_MOCK=false`
- [ ] 定期备份 `/opt/janus_workspace/`（含数据库 `pgdata`）
- [ ] 保留至少一个历史安装包便于回滚

