---
name: janus-client-starter
description: Janus 统一认证客户端 Starter（janus-client-spring-boot-starter）开发技能。当为业务 Spring Boot 系统接入 Janus SSO 单点登录、编写/修改 starter 自身代码、使用 janus.client.* 配置、UserContext 用户上下文、JanusClient 门面 API、JanusAuthHook 登录钩子时使用。聚焦 Sa-Token SSO 模式二统一 Token、本地缓存与失效通知、拦截器路径配置等核心场景。
license: MIT
compatibility: 面向 AI 编码助手（Claude Code / Cursor / Trae 等）。要求 Spring Boot 3.x + Java 17+，使用 Sa-Token 1.45.x SSO。
metadata:
  framework: Spring Boot 3.5.x / Sa-Token 1.45.x
  language: Java 17+
  groupId: com.janus
  artifactId: janus-client-spring-boot-starter
  version: "1.2.3"
---

当用户要求为业务系统接入 Janus 认证、或修改/扩展本 starter 时，严格按照本说明编码。

## CRITICAL RULES - NEVER VIOLATE THESE

**🚫 绝对禁止：**

1. **禁止在业务 yml 中出现任何 `sa-token.*` 配置** —— starter 已在代码内完成全部 Sa-Token 封装（token-name、token-prefix、cookie 安全基线、SSO 四开关等），业务侧唯一配置源是 `janus.client.*`。见到 `sa-token:` 出现在业务配置中必须删除并改用 `janus.client.*`。
2. **禁止在业务 yml 中出现 `spring.data.redis.*`** —— Redis 连接由 `janus.client.redis.*` 声明，starter 内部自动映射；若 Redis 与认证中心同机部署，`redis` 节点可整个省略（自动从 `server-url` 提取 host）。
3. **禁止业务代码直接调用 `StpUtil.login()` / `StpUtil.logout()` 管理会话** —— 会话建立（验票后采纳中央 Token）与登出级联由 starter 托管。业务只做只读操作（如 `StpUtil.getLoginIdAsString()`）或通过钩子/事件感知生命周期。
4. **禁止修改客户端通知路径** —— 失效通知接收路径固定 `POST /sso/janus/openApi/notify`，不支持外部配置；服务端 OpenApi 统一收敛在 `/sso/janus/openApi/**`。
5. **禁止硬编码 secret-key 等敏感值** —— 使用环境变量（如 `${JANUS_CLIENT_SECRET_KEY}`）注入。
6. **禁止绕过 `JanusClient` 门面直接注入 `JanusUserCacheService` 等缓存服务** —— 业务侧一律面向 `com.janus.client.api.JanusClient` 接口编程，便于单测替换与后续演进。
7. **禁止假设 `UserContextHolder.get()` 一定非空** —— 放行路径、异步线程中为 null。业务门禁语义用 `require()`；可空场景必须判空。
8. **禁止在 `JanusAuthHook` 实现中抛出异常中断登录流程** —— Hook 异常会被 starter 捕获记录，但 Hook 内不应承担认证门禁职责；权限判断用 `UserContext.hasRole/hasAuth`。
9. **禁止在 starter 内新增带 `janus`/`sa-token` 以外前缀的配置**，且 starter 自身日志必须带 `[Janus]` 前缀并包含关键链路信息（拦截地址、重定向、验票结果、会话创建/登出）。
10. **禁止用排除法（exclude-paths）适配"少数路径才需要登录"的场景** —— 网关类动态前缀场景必须用白名单 `include-paths`。

**✅ 始终遵守：**

1. 业务接入只配 `janus.client.*`（必配 4 项：`server-url` / `client` / `secret-key` / `client-url`）。
2. 用户/机构/角色/职务查询一律注入 `JanusClient` 接口。
3. 请求内获取当前用户一律用 `UserContextHolder.get()/require()`，门禁用 `hasRole()/hasAuth()`。
4. 登录/登出业务逻辑通过实现 `JanusAuthHook`（Spring Bean）或订阅 `JanusLoginSuccessEvent`/`JanusLogoutEvent` 挂载。
5. 客户端自助注册是幂等的（先到先得 + 所有者可更新），启动时自动完成，无需在认证中心手工登记。
6. 拦截器配置优先显式声明：默认模式 `/**` 减 `exclude-paths`；白名单模式配 `include-paths`（两者下 starter 内置端点始终自动放行）。

---

## PROJECT SETUP（业务接入）

### Maven 配置

构件发布在私有 Nexus（匿名可读），业务工程需配置仓库：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.janus</groupId>
    <artifactId>janus-client-spring-boot-starter</artifactId>
    <version>1.2.3</version>
</dependency>
```

```xml
<!-- ~/.m2/settings.xml 或项目指定的 settings.xml -->
<profiles>
  <profile>
    <repositories>
      <repository>
        <id>nexus-releases</id>
        <url>http://<NEXUS_HOST>:<NEXUS_PORT>/repository/maven-releases/</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
      </repository>
    </repositories>
  </profile>
</profiles>
```

**⚠️ mirror 陷阱**：settings.xml 中所有可能匹配到私仓请求的 mirror（如 aliyun 的 `mirrorOf: *`），必须排除私仓 id：`<mirrorOf>*,!nexus-releases</mirrorOf>`（SNAPSHOT 场景再加 `,!nexus-snapshots`），否则公共镜像会劫持私有构件请求。依赖解析失败后 Maven 会缓存 `.lastUpdated`，修复后需 `-U` 强刷。

### 最小可运行配置（application.yml）

```yaml
janus:
  client:
    # 必配 4 项，缺一启动报错
    server-url: https://127.0.0.1:8899     # 认证中心地址（回环地址=同机部署，浏览器跳转自动跟随请求 Host）
    client: demo-app                        # 应用标识（全局唯一）
    secret-key: ${JANUS_CLIENT_SECRET_KEY}  # HMAC 签名密钥（注册后由认证中心下发）
    client-url: http://localhost:8081       # 本系统对外地址（SLO 回调 {client-url}/sso/logoutCall）
```

- 同机部署时 `server-url` 配回环地址（127.0.0.1/localhost）即可，登录跳转自动跟随用户实际访问地址；跨机部署必须配真实 IP/域名。
- 全量配置项参考 starter jar 内的 `classpath:application-janus-client.yml`（带注释的完整模板，IDE 输入 `janus.client` 有自动补全）。
- 本地临时关闭认证：`janus.client.enabled: false`（整个自动装配不生效）。

---

## ARCHITECTURE OVERVIEW（starter 自身结构）

```
com.janus.client
├── api/          JanusClient（业务门面接口）/ JanusDefaultClient（默认实现）
├── config/       JanusClientAutoConfiguration（装配核心）/ JanusClientProperties（janus.client.* 绑定）
│                 JanusRedisEnvironmentPostProcessor（redis.* → spring.data.redis.* 映射）
├── context/      UserContext（请求级身份）/ UserContextHolder（ThreadLocal 持有器）
├── controller/   JanusSsoClientController（SSO 路由）/ JanusNotifyController（失效通知）
│                 JanusMonitorController（监控页端点）
├── hook/         JanusAuthHook（业务生命周期钩子接口）
├── listener/     JanusSaTokenAuthListener（Sa-Token 事件 → Hook + Spring 事件分派）
├── event/        JanusLoginSuccessEvent / JanusLogoutEvent / JanusClientRegisteredEvent
├── interceptor/  JanusAuthInterceptor（认证+上下文注入）/ JanusMonitorAuthInterceptor（监控页 HTTP Basic）
├── registrar/    JanusClientRegistrar（启动时幂等自助注册，指数退避重试）
├── notify/       JanusChangeReconciler（增量对账兜底）/ NotifyPayloadSigner（HMAC 验签）/ JanusNotifyEventLog
├── service/      JanusUserCacheService / JanusOrgCacheService / JanusRoleCacheService /
│                 JanusPositionCacheService（Caffeine 本地缓存）/ JanusDataFetcher（回源认证中心）/
│                 JanusInvalidateService（失效编排 + Redis 广播订阅）
├── template/     JanusSsoClientTemplate（回环地址登录跳转跟随 Host 修正）
├── http/         RestTemplateSaHttpTemplate（Sa-Token 出站 HTTP 模板，SLO/getData 依赖）
└── model/        UserInfoVO / DeptInfoVO / RoleInfoVO / PositionInfoVO / UserDeptPositionVO / PageResult
```

### 核心工作流

1. **装配**：`@AutoConfigureBefore(SaSsoBeanRegister.class)` 抢先注册 `SaSsoClientTemplate`；`SmartInitializingSingleton` 在官方装配之后同步 `janus.client.*` → `SaSsoClientConfig`（isHttp=false 模式二、isSlo=true、isCheckSign=true 四开关必须显式设置，防 NPE）。
2. **统一 Token**：验票成功后 `strategy.ticketResultHandle` 采纳认证中心中央 Token（`StpUtil.login` 带原 token），认证中心与所有客户端共用同一 Token；`remainTokenTimeout` 非正时绝不能回退 -1（Sa-Token 中 -1 = 永久有效），必须用客户端默认 timeout。
3. **认证拦截**：`JanusAuthInterceptor` 拦 `/**`（或 includePaths 白名单）减 excludes，注入 `UserContext`，请求完成自动清理。
4. **缓存一致性**：认证中心数据变更 → HMAC 签名通知 `POST /sso/janus/openApi/notify` → 失效/异步热刷新本地 Caffeine 缓存；另有 Redis 广播订阅 + 定时对账（默认 5 分钟）兜底最终一致。单批失效超 `invalidate-batch-threshold`（默认 5000）自动降级清空缓存。
5. **本地会话安全基线**：tokenName 全域统一（默认 `Authorization`，决定 Redis key 前缀与 Cookie 名）、tokenPrefix=Bearer、HttpOnly + SameSite=Lax 强制设置。

---

## CODING PATTERNS（业务侧标准写法）

### 1. 获取当前登录用户

```java
// ✅ 正确：门禁场景用 require()，未注入即抛异常（调用方式错误的语义）
UserContext user = UserContextHolder.require();
if (!user.hasRole("admin")) {
    throw new AccessDeniedException("需要 admin 角色");
}
String realName = user.getRealName();   // uid/username/realName/deptId/deptName/roles/authCodes

// ✅ 正确：可空场景（放行路径、异步线程可能为 null）
UserContext maybe = UserContextHolder.get();
if (maybe != null) { ... }

// ❌ 错误：get() 后直接解引用
String uid = UserContextHolder.get().getUid();  // 放行路径上 NPE
```

### 2. 查询用户/机构/角色信息

```java
// ✅ 注入门面接口，数据全走本地缓存（未命中自动回源，业务无需关心缓存细节）
@RequiredArgsConstructor
@Service
public class ApproveService {
    private final JanusClient janusClient;

    public void notify(String deptId) {
        UserInfoVO leader = janusClient.getOrgLeader(deptId);          // 部门负责人
        List<DeptInfoVO> children = janusClient.getOrgChildren(deptId); // 子机构（级联回源）
        PageResult<UserInfoVO> page = janusClient.listUsersByOrg(deptId, true, "", 1, 20);
        Map<String, UserInfoVO> users = janusClient.getUsersByIds(uids); // 批量，自动分片≤500
        // ⚠️ getUserById 永不返回 null：查无此人返回 exists=false 的 EMPTY 哨兵，须判 exists
    }
}
```

### 3. 登录/登出生命周期挂钩

```java
// 方式一：实现 JanusAuthHook（推荐，方法默认空实现，按需覆写，支持 @Order 排序）
@Component
public class AuditHook implements JanusAuthHook {
    @Override
    public void onLoginSuccess(String loginId, String tokenValue) {
        // 初始化本地档案 / 记录审计日志（异常会被 starter 隔离，不影响登录）
    }
    @Override
    public void onLogout(String loginId, String tokenValue, String logoutType) {
        // logoutType: "logout" 主动注销 / "slo" 单点登出推送 / "kickout" 被踢下线
    }
}

// 方式二：订阅 Spring 事件
@Component
public class LoginListener {
    @EventListener
    public void onLogin(JanusLoginSuccessEvent event) { ... }

    @EventListener
    public void onLogout(JanusLogoutEvent event) { ... }
}
```

### 4. 接口认证范围控制

```java
// ✅ 默认模式（排除法）：/** 拦截，追加公开接口
janus.client.exclude-paths: [/sso/**, /, /index.html, /favicon.ico, /error, /api/public/**]

// ✅ 白名单模式：只拦声明路径（网关类动态前缀场景必须用这个）
janus.client.include-paths: [/admin/**, /api/inner/**]
```

### 5. 单元测试

```java
// 业务单测：mock JanusClient 门面 + 手动注入 UserContext
@ExtendWith(MockitoExtension.class)
class ApproveServiceTest {
    @Mock JanusClient janusClient;
    @InjectMocks ApproveService service;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .uid("u-1").username("zhangsan").realName("张三")
                .roles(List.of("admin")).authCodes(List.of())
                .build());
    }
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }   // 必须清理，防 ThreadLocal 泄漏
}

// 集成调试期整体关闭认证：janus.client.enabled=false
```

---

## COMMON PITFALLS & SOLUTIONS

| 陷阱 | 说明 | 正确做法 |
|---|---|---|
| 业务 yml 配 `sa-token.*` | starter 强制安全基线，业务配置会打架或被覆盖 | 只配 `janus.client.*` |
| 业务 yml 配 `spring.data.redis.*` | 与 `janus.client.redis` 双源冲突 | 用 `janus.client.redis.*`，同机部署可整个省略 |
| `token-name` 与认证中心不一致 | Redis key 前缀、请求头识别、Cookie 名全部错位 | 保持默认 `Authorization`，全网统一 |
| `redis.database` 与认证中心不一致 | 验票读票失败 | 必须与认证中心同库（默认 0） |
| `remainTokenTimeout` 异常时回退 -1 | -1 在 Sa-Token 中表示永久有效，签发永久会话 | 回退客户端默认 timeout（默认 7200s） |
| SSO 消息端点返回 500 | 协议分发方无法解析错误 | 必须返回 HTTP 200 + 错误 SaResult |
| 登出未级联撤销刷新令牌 | 中央会话注销但刷新令牌残留 | 登出链路挂 `RefreshTokenRevocationListener`（服务端） |
| `.properties` 中文值乱码 | Properties 默认 ISO-8859-1 解码 | 中文值用 `\uXXXX` Unicode 转义 |
| 静态资源 404 走 ERROR 堆栈 | favicon 等噪音日志 | `NoResourceFoundException` 用 WARN 处理 |
| 私仓依赖拉不到 | mirror 劫持 / `.lastUpdated` 缓存 | mirrorOf 排除私仓 id + `-U` 强刷 |

---

## QUICK REFERENCE

### 必配 4 项
`server-url` / `client` / `secret-key` / `client-url`，缺一启动报错。

### 关键端点（starter 内置，业务零配置）
| 端点 | 用途 |
|---|---|
| `POST /sso/janus/openApi/notify` | 失效通知接收（固定路径，HMAC 验签） |
| `/sso/janus/monitor` + `/sso/janus/monitor.html` | 客户端监控页（可配 HTTP Basic 口令，`monitor-enabled` 可关） |
| `/sso/logout`、`/sso/logoutCall` | 单点登出发起 / 回调（Sa-Token 原生路由） |

### 配置速查（均有默认值）
`enabled=true` · `monitor-enabled=true` · `monitor-username=admin` · `monitor-password=空` · `token-name=Authorization` · `exclude-paths=[/sso/**,/,/index.html,/favicon.ico,/error]` · `include-paths=[]` · `invalidate-batch-threshold=5000` · `reconcile-enabled=true` · `reconcile-interval-seconds=300` · `notify.async-refresh=true` · `redis{host 自动提取, port=26379, password=janus@123, database=0}` · `cache{max-size=50000, org-max-size=5000, expire-hours=12}`

### UserContext 字段
`uid`（=loginId，UUID）、`username`、`realName`、`deptId`、`deptName`、`roles`（角色编码）、`authCodes`（button 型菜单权限码）、`hasRole(code)`、`hasAuth(code)`

### 构建与发布（starter 自身开发）
- 编译：`JAVA_HOME=/data/soft/openjdk-17.0.2_linux-x64_bin/jdk-17.0.2 /data/soft/apache-maven-3.8.1-bin/apache-maven-3.8.1/bin/mvn -q compile -DskipTests`（禁用 wrapper maven 与 `~/.m2` 仓库）。
- 发布必须走 `./package-files/publish.sh`：先发 parent pom（`janus-auth`，`deploy -N`）再发 starter（`deploy -pl janus-client-spring-boot-starter`），私服 `maven-releases` 禁止同版本重复发布。
- jar 根目录必须包含 `application-janus-client.yml` 配置模板；新增配置项须同步更新该模板并在 `JanusClientProperties` 写清 Javadoc（configuration-processor 会生成 IDE 提示）。
