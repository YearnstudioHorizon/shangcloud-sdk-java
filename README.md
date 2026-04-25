# ShangCloud SDK for Java

Java SDK，封装了授权登录与基础用户信息接口。

- GroupId: `cn.yearnstudio`
- ArtifactId: `shangcloud-sdk`
- Java 版本: 11+
- 依赖: [Gson](https://github.com/google/gson) 2.10.1
- License: [MIT](../shangcloud-sdk-go/LICENSE)

## 安装

在 `pom.xml` 中添加依赖（暂不可用）：

```xml
<dependency>
    <groupId>cn.yearnstudio</groupId>
    <artifactId>shangcloud-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

也可以克隆本仓库后通过 `mvn install` 安装到本地：

```bash
mvn install -DskipTests
```

## 快速开始

以下是一个基于 Servlet 的完整 OAuth 授权码模式 (Authorization Code) 流程示例。

```java
import cn.yearnstudio.shangcloud.Client;
import cn.yearnstudio.shangcloud.User;
import cn.yearnstudio.shangcloud.UserBasicInfo;
import cn.yearnstudio.shangcloud.ShangCloudException;

// 通常作为单例或 Bean 注入
Client client = Client.initClient(
    "your-client-id",
    "your-client-secret",
    "https://your-app.example.com/oauth/callback"
);

// 生成授权跳转 URL，将用户引导到授权页
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if ("/login".equals(req.getServletPath())) {
        resp.sendRedirect(client.generateOAuthUrl());
    }
}

// 处理授权回调，使用 code 换取 User 实例
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if ("/oauth/callback".equals(req.getServletPath())) {
        String code  = req.getParameter("code");
        String state = req.getParameter("state");

        try {
            User user = client.generateUserInstance(code, state);

            // 拉取用户基本信息
            UserBasicInfo info = user.getBasicInfo();
            resp.getWriter().println("Hello, " + info.getNickname()
                + " (uid=" + info.getUserId() + ")");
        } catch (ShangCloudException e) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        }
    }
}
```

## 核心 API

### `Client.initClient(clientId, clientSecret, redirectUri)`

创建 SDK 客户端。默认 `scope` 为 `user:basic`，`baseUrl` 为 `https://api.yearnstudio.cn`，并使用内置的线程安全内存 KV 作为 state 存储。如需自定义，可直接修改返回实例的公开字段。

```java
Client client = Client.initClient("client-id", "client-secret", "https://example.com/callback");
// 或直接构造
Client client = new Client("client-id", "client-secret", "https://example.com/callback");

// 覆盖默认值
client.scope   = "user:basic";
client.baseUrl = "https://api.yearnstudio.cn";
```

### `client.generateOAuthUrl()`

生成授权跳转 URL，内部随机生成 state 并写入 `kvStorage`，用于后续回调校验。

### `client.generateUserInstance(code, state)` throws `ShangCloudException`

校验 state，向 `/oauth/token` 换取 access token / refresh token，返回实现了 `User` 接口的实例。

抛出 `ShangCloudException`：
- state 不存在或已被消费（重放攻击防护）
- 服务端授权失败（非 200 响应）
- 网络连接失败或超时

### `client.setClientSecret(clientSecret)`

更换 Client Secret。

```java
client.setClientSecret("new-secret");
```

### `User` 接口

```java
public interface User {
    void initUser(String accessToken, String refreshToken,
                  String tokenType, int expiresIn, Client client);
    void save();
    boolean isExpired();
    UserBasicInfo getBasicInfo() throws ShangCloudException;
}
```

SDK 提供了默认的内存实现 `UserInstance`。`isExpired()` 提前 60 秒返回 `true`，`getBasicInfo()` 会请求 `/api/user/info`。

### `UserBasicInfo`

```java
public class UserBasicInfo {
    public int    getUserId();      // uid
    public String getNickname();
    public String getMail();
    public String getAvatar();
}
```

## 自定义扩展

### 自定义 state 存储

实现 `TempVarStorage` 接口，替换为 Redis 等共享存储，适用于多 JVM / 集群部署：

```java
import cn.yearnstudio.shangcloud.TempVarStorage;
import cn.yearnstudio.shangcloud.ShangCloudException;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;

public class RedisKv implements TempVarStorage {
    private final JedisPool pool;

    public RedisKv(JedisPool pool) { this.pool = pool; }

    @Override
    public void setTempVariable(String key, String value) {
        try (Jedis j = pool.getResource()) {
            j.setex(key, 300, value); // 5 分钟过期
        }
    }

    @Override
    public String getTempVariable(String key) throws ShangCloudException {
        try (Jedis j = pool.getResource()) {
            String v = j.get(key);
            if (v == null) throw new ShangCloudException("Key '" + key + "' not found");
            return v;
        }
    }

    @Override
    public void deleteTempVariable(String key) {
        try (Jedis j = pool.getResource()) { j.del(key); }
    }
}

client.kvStorage = new RedisKv(jedisPool);
```

实现须自行保证线程安全（内置 `RamKv` 已通过 `ConcurrentHashMap` 保证）。

### 自定义 User 持久化

实现 `User` 接口，在 `initUser` / `save` 中加入数据库逻辑：

```java
import cn.yearnstudio.shangcloud.User;
import cn.yearnstudio.shangcloud.UserBasicInfo;
import cn.yearnstudio.shangcloud.Client;
import cn.yearnstudio.shangcloud.ShangCloudException;
import java.time.Instant;

public class DbUser implements User {
    private String accessToken;
    private String tokenType;
    private Instant expiryTime;
    private Client client;
    private final long userId; // 数据库主键

    public DbUser(long userId) { this.userId = userId; }

    @Override
    public void initUser(String accessToken, String refreshToken,
                         String tokenType, int expiresIn, Client client) {
        this.accessToken = accessToken;
        this.tokenType   = tokenType;
        this.expiryTime  = Instant.now().plusSeconds(expiresIn);
        this.client      = client;
        save();
    }

    @Override
    public void save() {
        // INSERT OR UPDATE INTO user_tokens ...
    }

    @Override
    public boolean isExpired() {
        return Instant.now().plusSeconds(60).isAfter(expiryTime);
    }

    @Override
    public UserBasicInfo getBasicInfo() throws ShangCloudException {
        return client.getUserBasicInfo(accessToken, tokenType);
    }
}
```

## 注意事项

- **内存 KV 仅适用于单 JVM 实例**。在多节点 / 分布式部署时，请替换 `kvStorage` 为 Redis 等共享存储，否则跨节点的 state 校验会失败。
- `clientSecret` 与 token 字段为 `private`，不会出现在 `toString()` 或序列化输出中，但仍应避免将 `Client` / `UserInstance` 对象直接打印到日志。
- `generateUserInstance` 和 `getBasicInfo` 均声明 `throws ShangCloudException`（checked exception），调用方须显式处理或继续向上抛出。
- 内置 `HttpClient` 实例为 `static final`，在 JVM 生命周期内复用连接池，`Client` 本身可以安全地作为单例使用。
- `isExpired()` 提前 60 秒返回 `true`，确保 token 在请求过程中不会中途失效。
- SDK 未实现 token 刷新；需要刷新时请自行调用平台 refresh 端点后重建 `UserInstance`。

## License

[MIT](../shangcloud-sdk-go/LICENSE)
