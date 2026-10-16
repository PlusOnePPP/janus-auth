#!/usr/bin/env bash
#
# Janus 单体部署一键打包（pom 驱动），按架构产出独立离线安装包 janus-install-<版本>-<日期>-linux-<arch>.tar.gz
#   - maven 在 prepare-package 阶段自动下载 Node/pnpm、构建前端 web-antd、打入 static；
#   - docker buildx 出运行镜像（按目标架构 amd64 / arm64，分别打独立包）；
#   - docker save 导出 janus + postgres 单架构镜像，连同 docker-compose.yml 打成该架构的安装包。
# 宿主机只需 JDK17 + Maven + docker(buildx) + 联网（首次拉基础镜像/依赖；跨架构构建需 QEMU）。
# x86 构建机可借 QEMU 模拟打出 arm64 包；也可在对应架构机器上各打各的（无需 QEMU）。
#
# 用法：  ./build.sh <版本号> [amd64|arm64|all]   例如:  ./build.sh 1.0.0 all
#        第二个参数缺省为 all（出 amd64 + arm64 两份独立包）
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "用法: $0 <版本号>   例如: $0 1.0.0"
  exit 1
fi
VERSION="$1"

# 脚本所在目录（即 package-files/，打包脚本、Dockerfile、compose、install.sh 都在这里）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 仓库根目录（maven 工程与 Dockerfile 构建上下文都在此，Dockerfile 内 COPY janus-backend/target/... 需相对它）
PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"

# ---- 工具链路径（按需修改） ----
export JAVA_HOME="${JAVA_HOME:-/data/soft/openjdk-17.0.2_linux-x64_bin/jdk-17.0.2}"
MVN="${MVN:-/data/soft/apache-maven-3.8.1-bin/apache-maven-3.8.1/bin/mvn}"

# ---- 0. 版本对齐：把 pom 版本（聚合根 + 全部子模块 + 内部依赖引用）连带同步为指定版本 ----
# 镜像 tag / 安装包名取自 $VERSION 参数，jar 实际版本取自 pom；二者必须一致，否则镜像标签与内容脱节。
POM_VERSION="$("${MVN}" -f "${PROJECT_ROOT}/pom.xml" -q help:evaluate -Dexpression=project.version -DforceStdout)"
if [ "${POM_VERSION}" != "${VERSION}" ]; then
  echo "==> pom 版本 ${POM_VERSION} ≠ 指定版本 ${VERSION}，自动同步 pom ..."
  "${MVN}" -f "${PROJECT_ROOT}/pom.xml" -q versions:set -DnewVersion="${VERSION}" -DprocessAllModules -DgenerateBackupPoms=false
else
  echo "==> pom 版本已是 ${VERSION}，跳过同步"
fi

# ---- 1. maven 打包（含前端构建 + 打入 static） ----
echo "==> [1/4] maven 打包 janus-backend（前端由 pom 内 frontend-maven-plugin 自动构建并打入 jar）"
cd "${PROJECT_ROOT}"
"${MVN}" -f pom.xml -pl janus-backend -am clean package -DskipTests

# ---- 2/3. 按架构分别构建镜像 + 打独立安装包 ----
# 带重试执行：镜像仓库网络不稳时偶发 short read / unexpected EOF，重试即可（默认 4 次，间隔 5s）
run_with_retry() {
  local tries=4 i
  for ((i=1; i<=tries; i++)); do
    if "$@"; then return 0; fi
    echo "  ⚠️ 命令失败（第 $i/$tries 次），5s 后重试..."; sleep 5
  done
  return 1
}

# 可选第二个参数指定架构：amd64 / arm64 / all（默认 all，出两份独立包）
ARG_ARCH="${2:-all}"
case "$ARG_ARCH" in
  amd64|arm64) TARGETS=("$ARG_ARCH") ;;
  all)         TARGETS=(amd64 arm64) ;;
  *) echo "❌ 不支持的架构参数: $ARG_ARCH（可选 amd64 / arm64 / all）"; exit 1 ;;
esac

# 本机架构（用于判断是否跨架构，跨架构才需要 QEMU 模拟）
HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in x86_64|amd64) HA=amd64;; aarch64|arm64) HA=arm64;; *) HA="";; esac
NEED_QEMU=0
for t in "${TARGETS[@]}"; do
  [ "$t" != "$HA" ] && NEED_QEMU=1
done
if [ "$NEED_QEMU" -eq 1 ]; then
  echo "==> 注册 QEMU（跨架构构建需要，首次联网拉 tonistiigi/binfmt）"
  docker run --privileged --rm tonistiigi/binfmt:latest --install all >/dev/null 2>&1 \
    || echo "  ⚠️ QEMU 注册失败（可能无网络/无特权）：无法跨架构构建，请只在对应架构的机器上打包该架构"
fi

DATE="$(date +%Y%m%d)"
PRODUCED=()

for arch in "${TARGETS[@]}"; do
  plat="linux/${arch}"
  echo "##################################################"
  echo "==> [打包架构] ${arch} (${plat})"
  echo "##################################################"

  # 预拉取基础镜像（带重试）：把最易断流的基础镜像下载隔离出来，成功即缓存，后续构建不再依赖网络
  echo "  → 预拉取基础镜像 eclipse-temurin:17-jre-jammy (${plat})"
  if ! run_with_retry docker pull --platform "${plat}" eclipse-temurin:17-jre-jammy; then
    echo "  ❌ 基础镜像拉取失败，跳过 ${arch} 架构包"
    continue
  fi

  # janus 运行镜像（单架构，janus/ 前缀 tag；连续构建会被下一个 arch 覆盖，但每次打完即存包，无碍）
  # 注意：Dockerfile 在 package-files/ 下，但构建上下文必须是工程根目录（COPY janus-backend/target/...）
  # 先删同名旧 tag，避免 classic 镜像存储复用上一次（可能是另一种架构）的镜像
  docker rmi "janus/janus:${VERSION}" >/dev/null 2>&1 || true
  echo "  → 构建 janus/janus:${VERSION} (${plat})"
  if ! run_with_retry docker buildx build --platform "${plat}" -t "janus/janus:${VERSION}" -f "${SCRIPT_DIR}/Dockerfile" "${PROJECT_ROOT}" --load; then
    echo "  ❌ 构建 janus:${VERSION} (${arch}) 失败，跳过该架构包"
    continue
  fi

  # postgres / redis 镜像（单架构，带重试）
  # 关键坑：直接 `docker pull --platform` + `docker tag` 在 containerd / classic 镜像存储下，
  # 容易因"同 tag 复用 / manifest list 默认架构"导致 arm64 包里混入 amd64（janus 本身用 buildx 构建已证明 OK）。
  # 因此这里统一用 buildx 以目标架构构建（只 FROM 基础镜像、无 RUN，故无需 QEMU 即可跨架构产出单平台镜像），
  # 直接打成 janus/ 前缀，彻底绕开上述问题。
  build_janus_alpine() {
    # $1=目标镜像(含 tag)  $2=源镜像  $3=arch
    local dst="$1" src="$2" a="$3" df tmp
    docker rmi "$dst" >/dev/null 2>&1 || true          # 先清掉可能残留的 janus/ 旧 tag
    tmp="$(mktemp)" && printf 'FROM %s\n' "$src" > "$tmp"
    if ! run_with_retry docker buildx build --platform "linux/${a}" --load -t "$dst" -f "$tmp" "$(dirname "$tmp")"; then
      rm -f "$tmp"; return 1
    fi
    rm -f "$tmp"; return 0
  }
  echo "  → 构建 janus/postgres:14-alpine (${plat})"
  if ! build_janus_alpine "janus/postgres:14-alpine" "postgres:14-alpine" "${arch}"; then
    echo "  ❌ 构建 janus/postgres:14-alpine (${arch}) 失败，跳过该架构包"
    continue
  fi
  echo "  → 构建 janus/redis:7-alpine (${plat})"
  if ! build_janus_alpine "janus/redis:7-alpine" "redis:7-alpine" "${arch}"; then
    echo "  ❌ 构建 janus/redis:7-alpine (${arch}) 失败，跳过该架构包"
    continue
  fi

  # ---- 出包前强校验：三个镜像的实际架构必须等于目标架构 ----
  # 防止"包名是 arm64、里面却是 amd64"这类静默错误（装到对应架构机器上会因 platform 不符启动失败）
  ARCH_BAD=0
  for img in "janus/janus:${VERSION}" "janus/postgres:14-alpine" "janus/redis:7-alpine"; do
    got="$(docker inspect -f '{{.Architecture}}' "$img" 2>/dev/null || echo '?')"
    if [ "$got" != "$arch" ]; then
      echo "  ❌ 架构不符: $img 实际为 ${got}，目标为 ${arch}"
      ARCH_BAD=1
    fi
  done
  if [ "$ARCH_BAD" -eq 1 ]; then
    echo "  ❌ ${arch} 架构镜像不正确，跳过该架构包（否则部署到 ${arch} 机器会报 platform 不符）"
    echo "     建议：检查构建机 docker/buildx 版本（跨架构构建需可用 builder）；或直接在 ${arch} 机器上执行 ./build.sh ${VERSION} ${arch}"
    continue
  fi
  echo "  ✔ 架构校验通过（janus/janus、postgres、redis 均为 ${arch}）"

  # 打该架构的安装包（镜像均已带 janus/ 前缀，对应此架构，install 时无需再挑）
  # 安装包与最终产物 tar.gz 全部落在 package-files/ 目录内
  PKG="janus-install-${VERSION}-${DATE}-linux-${arch}"
  rm -rf "${SCRIPT_DIR}/${PKG}"; mkdir -p "${SCRIPT_DIR}/${PKG}"
  cp "${SCRIPT_DIR}/docker-compose.yml" "${SCRIPT_DIR}/${PKG}/"
  cp "${SCRIPT_DIR}/install.sh" "${SCRIPT_DIR}/${PKG}/"
  cp "${SCRIPT_DIR}/DEPLOY.md" "${SCRIPT_DIR}/${PKG}/"
  chmod +x "${SCRIPT_DIR}/${PKG}/install.sh"
  # 把包内 compose 的镜像引用从 janus:latest 固定为具体版本号，部署后可追溯版本
  sed -i "s|image: janus/janus:latest|image: janus/janus:${VERSION}|" "${SCRIPT_DIR}/${PKG}/docker-compose.yml"
  docker save "janus/janus:${VERSION}" "janus/postgres:14-alpine" "janus/redis:7-alpine" -o "${SCRIPT_DIR}/${PKG}/janus-images.tar"
  tar czf "${SCRIPT_DIR}/${PKG}.tar.gz" -C "${SCRIPT_DIR}" "${PKG}"
  rm -rf "${SCRIPT_DIR}/${PKG}"
  PRODUCED+=("${PKG}.tar.gz")
  echo "  ✔ 产出安装包: package-files/${PKG}.tar.gz"
done

# ---- 4. 清理临时目录 ----
echo "==> [4/4] 清理临时目录"

echo ""
echo "完成 ✅  安装包（按架构独立出包，部署时按服务器架构选对应包，统一落在 package-files/ 目录）:"
for p in "${PRODUCED[@]:-}"; do [ -n "$p" ] && echo "  - package-files/${p}"; done
echo ""
echo "每个包内容（解压后为 janus-install-<版本>-<日期>-linux-<arch>/）："
echo "  docker-compose.yml"
echo "  install.sh          (一键部署脚本)"
echo "  janus-images.tar   (janus:${VERSION} + postgres:14-alpine + redis:7-alpine，单架构)"
echo ""
echo "实施人员部署（按服务器架构选包）："
echo "  tar xzf package-files/janus-install-${VERSION}-${DATE}-linux-<arch>.tar.gz -C /opt"
echo "  cd /opt/janus-install-${VERSION}-${DATE}-linux-<arch>"
echo "  ./install.sh"
echo "详见 package-files/DEPLOY.md。"
