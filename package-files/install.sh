#!/usr/bin/env bash
#
# Janus 离线部署一键脚本（安装包内自带）
# 用法：在解压后的安装包目录里执行  ./install.sh [--data-dir=/path] | uninstall | --help
# 前置：已装 Docker 24+ 与 docker compose 插件（或旧版 docker-compose），并有操作 docker 的权限。
# 说明：脚本会自动加载离线镜像、预建宿主机数据目录、拉起服务并等待应用就绪。
#
set -euo pipefail

# 切到脚本所在目录（即解压后的安装包目录）
cd "$(dirname "${BASH_SOURCE[0]}")"
PKG_DIR="$(pwd)"

usage() {
  cat <<'EOF'
Janus 离线部署一键脚本

用法: ./install.sh [选项]

  （无参数）              全自动安装/升级：数据目录用默认值，拉起 PostgreSQL + Redis + Janus
  --data-dir=/path       指定数据目录（logs/uploads/config/pgdata 的宿主机存放位置），
                         并写入包目录 .env，后续手工 docker compose up -d 自动沿用
  --site-name=名称       预置系统名称（可选，用于预安装交付场景；写入包目录 .env，
                         应用启动时自动完成初始化向导，客户首次打开不再要求设置系统信息）
  --site-desc=描述       预置系统描述/Slogan（可选，配合 --site-name 使用）
  --copyright=版权       预置页脚版权声明（可选，配合 --site-name 使用）
  uninstall              卸载：移除 Janus 的容器与 janus/* 镜像，不影响其他项目（默认保留数据目录）
  --help                 查看本说明

环境变量:
  JANUS_DATA_DIR=/path   与 --data-dir 等价（优先级低于参数）
  JANUS_PURGE_DATA=1     配合 uninstall 使用：连数据目录一起删除（含数据库/上传/日志，慎用于生产）
EOF
}

# ---- 参数解析 ----
ACTION="install"
ARG_DATA_DIR=""
ARG_SITE_NAME=""
ARG_SITE_DESC=""
ARG_COPYRIGHT=""
for arg in "$@"; do
  case "$arg" in
    --help|-h)      usage; exit 0;;
    uninstall)      ACTION="uninstall";;
    --data-dir=*)   ARG_DATA_DIR="${arg#--data-dir=}";;
    --site-name=*)  ARG_SITE_NAME="${arg#--site-name=}";;
    --site-desc=*)  ARG_SITE_DESC="${arg#--site-desc=}";;
    --copyright=*)  ARG_COPYRIGHT="${arg#--copyright=}";;
    *)              echo "未知参数: $arg"; usage; exit 1;;
  esac
done

# 数据目录优先级：--data-dir 参数 > 环境变量 JANUS_DATA_DIR > 旧容器挂载（升级自动沿用）> 包目录 .env > 默认值
if [ -z "$ARG_DATA_DIR" ] && [ -z "${JANUS_DATA_DIR:-}" ]; then
  JANUS_DATA_DIR="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/app/janus_workspace"}}{{.Source}}{{end}}{{end}}' janus 2>/dev/null || true)"
  if [ -z "$JANUS_DATA_DIR" ] && [ -f .env ] && grep -q '^JANUS_DATA_DIR=' .env 2>/dev/null; then
    JANUS_DATA_DIR="$(grep '^JANUS_DATA_DIR=' .env | head -n1 | cut -d= -f2-)"
  fi
fi
DATA_DIR="${ARG_DATA_DIR:-${JANUS_DATA_DIR:-/opt/janus_workspace}}"
# export 给 docker compose 插值用（${JANUS_DATA_DIR:-/opt/janus_workspace} 挂载路径与脚本保持同一来源）
export JANUS_DATA_DIR="$DATA_DIR"

if [ "$ACTION" = "install" ]; then
  echo "=================================================="
  echo " Janus 离线部署  (安装包目录: ${PKG_DIR})"
  echo " 数据目录: ${DATA_DIR}"
  echo "=================================================="
fi

# 显式指定 --data-dir 或站点预置参数时写入包目录 .env：后续绕过脚本手工 docker compose up -d 也能解析到同一来源
if [ "$ACTION" = "install" ] && [ -n "${ARG_DATA_DIR}${ARG_SITE_NAME}${ARG_SITE_DESC}${ARG_COPYRIGHT}" ]; then
  ENV_TMP=".env.tmp.$$"
  # 保留非脚本管理的自定义行，脚本管理的键统一在下方重写（避免覆盖用户手工加入的其他变量）
  if [ -f .env ]; then
    grep -vE '^(JANUS_DATA_DIR|JANUS_SITE_PRESET_ENABLED|JANUS_SITE_PRESET_NAME|JANUS_SITE_PRESET_DESC|JANUS_SITE_PRESET_COPYRIGHT)=' .env > "$ENV_TMP" || true
  fi
  echo "JANUS_DATA_DIR=${DATA_DIR}" >> "$ENV_TMP"
  echo "  📝 数据目录已写入包目录 .env（后续手工 docker compose up -d 自动沿用）"
  # 站点预置：本次参数优先，未传的项沿用 .env 已有值（重跑/升级不丢预置）
  SITE_NAME="${ARG_SITE_NAME:-$(grep '^JANUS_SITE_PRESET_NAME=' .env 2>/dev/null | head -n1 | cut -d= -f2-)}"
  SITE_DESC="${ARG_SITE_DESC:-$(grep '^JANUS_SITE_PRESET_DESC=' .env 2>/dev/null | head -n1 | cut -d= -f2-)}"
  SITE_COPYRIGHT="${ARG_COPYRIGHT:-$(grep '^JANUS_SITE_PRESET_COPYRIGHT=' .env 2>/dev/null | head -n1 | cut -d= -f2-)}"
  if [ -n "$SITE_NAME" ]; then
    {
      echo "JANUS_SITE_PRESET_ENABLED=true"
      echo "JANUS_SITE_PRESET_NAME=${SITE_NAME}"
      echo "JANUS_SITE_PRESET_DESC=${SITE_DESC}"
      echo "JANUS_SITE_PRESET_COPYRIGHT=${SITE_COPYRIGHT}"
    } >> "$ENV_TMP"
    echo "  📝 站点信息预置已写入 .env（应用启动时自动完成初始化）: ${SITE_NAME}"
  fi
  mv "$ENV_TMP" .env
fi

# ---- [1/5] 环境检查 ----
echo "==> [1/5] 环境检查"
if ! command -v docker >/dev/null 2>&1; then
  echo "  ❌ 未检测到 docker，请先安装 Docker 24+"; exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "  ❌ docker 守护进程未运行，或当前用户无权限（需 root 或加入 docker 组）"; exit 1
fi

# ---- 卸载分支：只动 janus 的容器与 janus/* 镜像，不影响其他项目 ----
if [ "$ACTION" = "uninstall" ]; then
  echo "==> 卸载 Janus（数据目录: ${DATA_DIR}）"
  for c in janus janus-postgres janus-redis; do
    if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
      docker rm -f "$c" >/dev/null 2>&1 && echo "  🗑 已移除容器: $c"
    fi
  done
  IMG_IDS="$(docker images 'janus/*' -q)"
  if [ -n "$IMG_IDS" ]; then
    docker rmi -f $IMG_IDS >/dev/null 2>&1 && echo "  🗑 已删除 janus/* 镜像"
  fi
  if [ "${JANUS_PURGE_DATA:-0}" = "1" ]; then
    if [ -z "$DATA_DIR" ] || [ "$DATA_DIR" = "/" ]; then
      echo "  ❌ 数据目录为空或为根目录，拒绝删除"; exit 1
    fi
    echo "  ⚠️ JANUS_PURGE_DATA=1，删除数据目录: ${DATA_DIR}（含数据库/上传/日志，不可恢复）"
    rm -rf "${DATA_DIR:?}/"
  else
    echo "  ℹ️ 数据目录已保留: ${DATA_DIR}（确认不再需要数据时用 JANUS_PURGE_DATA=1 ./install.sh uninstall）"
  fi
  echo "卸载完成 ✅"
  exit 0
fi

# 兼容新插件(docker compose)与旧命令(docker-compose)
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "  ❌ 未检测到 docker compose（插件或命令），请安装后重试"; exit 1
fi
echo "  ✔ docker 就绪，compose 命令: ${DC}"

# ---- [2/5] 加载离线镜像 + 按架构选取 ----
# 取 compose 里 janus 镜像具体版本，判断本地是否已存在，避免重复 load
JANUS_IMG="$(grep -E 'image:\s*janus/janus' docker-compose.yml | head -n1 | awk '{print $2}')"
PG_IMG="janus/postgres:14-alpine"
REDIS_IMG="janus/redis:7-alpine"
IMG_VER="${JANUS_IMG#*:}"
echo "==> [2/5] 加载离线镜像 (${JANUS_IMG} + ${PG_IMG} + ${REDIS_IMG}，含 amd64/arm64)"

# 宿主架构（docker 术语：amd64 / arm64），用于校验镜像架构是否匹配
HOST_ARCH="$(docker version -f '{{.Server.Arch}}' 2>/dev/null)"
[ -z "$HOST_ARCH" ] && HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in
  x86_64) HOST_ARCH=amd64;;
  aarch64|arm64) HOST_ARCH=arm64;;
esac

# 判断是否需要加载：镜像缺失，或与宿主架构不一致（单架构包只含一种架构，架构不符必须重载）
NEED_LOAD=0
for img in "${JANUS_IMG}" "${PG_IMG}" "${REDIS_IMG}"; do
  if ! docker image inspect "$img" >/dev/null 2>&1; then
    NEED_LOAD=1  # 首次安装镜像必然缺失，属正常流程，从离线包加载即可，不打警告
  else
    img_arch="$(docker inspect -f '{{.Architecture}}' "$img" 2>/dev/null)"
    if [ "$img_arch" != "$HOST_ARCH" ]; then
      NEED_LOAD=1; echo "  ⚠️ 镜像架构不符: $img 为 $img_arch，宿主为 $HOST_ARCH，将重载"
    fi
  fi
done

if [ "$NEED_LOAD" -eq 0 ]; then
  echo "  ✔ 所需镜像均已存在且架构匹配（$HOST_ARCH），跳过加载"
elif [ ! -f janus-images.tar ]; then
  echo "  ❌ 未找到 janus-images.tar，安装包不完整"; exit 1
else
  docker load -i janus-images.tar
fi

# 兼容两种包：混合包（含 -amd64/-arm64 后缀）按架构 re-tag；单架构独立包镜像已是裸 tag，无需处理
case "$(uname -m)" in
  x86_64|amd64)  T=amd64 ;;
  aarch64|arm64) T=arm64 ;;
  *) T="" ;;
esac
if [ -n "$T" ] && docker image inspect "janus/janus:${IMG_VER}-${T}" >/dev/null 2>&1; then
  echo "  📦 宿主机架构 ${T}，选取对应镜像"
  docker tag "janus/janus:${IMG_VER}-${T}" "janus/janus:${IMG_VER}"
  docker tag "janus/postgres:14-alpine-${T}" "janus/postgres:14-alpine"
  docker tag "janus/redis:7-alpine-${T}" "janus/redis:7-alpine"
  # 删掉另一架构标签，省空间（可选）
  OTHER="$([ "$T" = amd64 ] && echo arm64 || echo amd64)"
  docker rmi -f "janus/janus:${IMG_VER}-${OTHER}" "janus/postgres:14-alpine-${OTHER}" "janus/redis:7-alpine-${OTHER}" >/dev/null 2>&1 || true
elif [ -n "$T" ] && ! docker image inspect "${JANUS_IMG}" >/dev/null 2>&1; then
  echo "  ⚠️ 离线包未包含 ${T} 架构镜像，将直接使用已有镜像（可能与宿主机架构不符，启动可能失败）"
fi

# ---- [3/5] 预建宿主机数据目录 ----
# 容器把这些目录挂卷映射进来，提前建好避免日志/上传/外挂配置写不进去
echo "==> [3/5] 预建宿主机数据目录 ${DATA_DIR}"
mkdir -p "${DATA_DIR}"/logs "${DATA_DIR}"/uploads "${DATA_DIR}"/config
echo "  ✔ ${DATA_DIR} 就绪 (logs/ uploads/ config/ pgdata 由容器自动生成)"

# ---- [4/5] 拉起服务 ----
# 兼容「全新安装」与「升级/重装」：容器名固定（janus / janus-postgres / janus-redis），
# 若既往部署残留同名容器，先移除再重建；仅删容器、不碰数据目录（bind 挂载，删容器不丢数据）。
NAMED_CONTAINERS=(janus janus-postgres janus-redis)
HAS_OLD=0
for c in "${NAMED_CONTAINERS[@]}"; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then HAS_OLD=1; break; fi
done
if [ "$HAS_OLD" -eq 1 ]; then
  echo "==> [4/5] 检测到同名旧容器，按【升级/重装】处理（保留数据目录 ${DATA_DIR}）"
  for c in "${NAMED_CONTAINERS[@]}"; do
    if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
      echo "  🔄 移除旧容器: $c"
      docker rm -f "$c" >/dev/null 2>&1 || true
    fi
  done
else
  echo "==> [4/5] 全新安装，拉起服务 (PostgreSQL + Redis + Janus)"
fi

# 宿主机端口预检：25432（PG 对外映射）、26379（Redis 对外映射）、8899（janus host 模式直接监听宿主机）。
# 端口被占会导致容器首次启动失败，此处提前拦截并给出占用进程。
# 端口清单从 docker-compose.yml 动态解析（ports: 的 "宿主机:容器" 映射左侧）+ janus 服务端口，
# 业务修改端口后预检自动跟随，不硬编码。
# 注意：必须在移除旧容器之后执行（旧容器持有端口属正常升级流程）。
HOST_PORTS="$(grep -E '^[[:space:]]*-[[:space:]]*"?[0-9]+:[0-9]+' docker-compose.yml 2>/dev/null | grep -oE '[0-9]+:[0-9]+' | cut -d: -f1 | sort -un)"
HOST_PORTS="${HOST_PORTS}$(printf '\n8899')"
if command -v ss >/dev/null 2>&1 && [ -n "$HOST_PORTS" ]; then
  for p in $HOST_PORTS; do
    if ss -ltnH "sport = :$p" 2>/dev/null | grep -q .; then
      echo "  ❌ 宿主机端口 ${p} 已被占用（docker-compose.yml 端口映射需要），占用情况："
      ss -ltnp "sport = :$p" 2>/dev/null | head -n 3
      echo "     请先停止占用进程（或修改 docker-compose.yml 端口映射）后重新安装。"
      exit 1
    fi
  done
  echo "  ✔ 端口预检通过 ($(echo $HOST_PORTS) 空闲)"
fi

$DC up -d
$DC ps

# ---- [5/5] 等待应用启动 ----
echo "==> [5/5] 等待 Janus 启动 (最多 120s)..."
READY=0
for i in $(seq 1 60); do
  if $DC logs janus 2>/dev/null | grep -q "Started JanusApplication"; then
    READY=1; break
  fi
  # 容器异常退出也提前结束
  if ! $DC ps janus 2>/dev/null | grep -q "Up"; then
    echo "  ⚠️ janus 容器未处于 Up 状态，请查看日志: $DC logs janus"
    $DC logs janus 2>/dev/null | tail -n 30
    exit 1
  fi
  printf "."
  sleep 2
done
echo ""

if [ "$READY" -eq 1 ]; then
  echo ""
  echo "=================================================="
  echo " 部署成功 ✅"
  # 访问端口与协议动态获取：janus 为 host 模式（无端口绑定），端口取 compose 注入的
  # SERVER_PORT（业务改了也不提示错，未写则默认 8899）；协议取自 JANUS_SSL_ENABLED
  APP_PORT="$(grep -E '^[[:space:]]*SERVER_PORT:' docker-compose.yml 2>/dev/null | grep -oE '[0-9]+' | head -n1 || true)"
  APP_PORT="${APP_PORT:-8899}"
  SCHEME="https"
  [ "$(grep -E 'JANUS_SSL_ENABLED' docker-compose.yml 2>/dev/null | grep -oE '(true|false)' | head -n1)" = "false" ] && SCHEME="http"
  echo " 访问地址: 浏览器打开 ${SCHEME}://<本机IP>:${APP_PORT}/ （IP 只用于人访问，不进任何配置）"
  CURL_OPTS="-sS"; [ "$SCHEME" = "https" ] && CURL_OPTS="-sk"
  echo " 本地验证: curl ${CURL_OPTS} ${SCHEME}://localhost:${APP_PORT}/ | head -n 5"
  echo " 应用日志: $DC logs -f janus"
  echo " 数据目录: ${DATA_DIR}/"
  echo "=================================================="
else
  echo ""
  echo "⚠️ 等待超时，应用可能仍在初始化。查看日志："
  echo "  $DC logs -f janus"
  exit 1
fi
