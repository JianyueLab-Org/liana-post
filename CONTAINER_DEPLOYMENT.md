# Liana Post 容器化部署说明

本文档说明当前项目的容器化部署、更新、重置和服务器 nginx 接入方式。当前方案定位为演示环境/模拟生产环境，核心编排文件为 `docker-compose.demo.yml`。

## 1. 部署结构

当前 compose 会启动以下容器：

- `mysql`: MySQL 8.0，初始化 `db/` 和 `db_revise/` 下的 SQL。
- `redis`: Redis 7.2。
- `nacos`: Nacos standalone。
- `liana-gateway`: 统一网关，宿主机暴露 `8080`。
- `liana-auth-service`
- `liana-oms-service`
- `liana-dispatch-service`
- `liana-sorting-service`
- `liana-tracking-service`
- `liana-facility-service`
- `liana-transport-service`
- `liana-syncer`
- `postage-db-init`: 为新建或已有 MySQL 数据卷补齐邮资库和表，成功后退出。
- `liana-postage-service`: 邮资与报价服务，内部端口 `8090`。
- `liana-web`: 前端 nginx，宿主机暴露 `80`。

默认访问入口：

```text
前端: http://localhost
网关: http://localhost:8080
Nacos: http://localhost:8848/nacos
MySQL: localhost:3307, root / liana_demo
```

业务服务不直接暴露给宿主机，由 gateway 和 Docker 内部网络访问。

## 2. 前置要求

部署机器需要安装：

- Docker
- Docker Compose v2
- 可访问 Docker 镜像源
- 如果在服务器上直接 `--build`，还需要能访问 Maven/npm 依赖源

如果是 Windows 本机运行 Docker Desktop，部分命令可能需要管理员权限。

## 3. 首次启动

在项目根目录执行：

```bash
docker compose -f docker-compose.demo.yml up -d --build
```

查看状态：

```bash
docker compose -f docker-compose.demo.yml ps
```

查看日志：

```bash
docker compose -f docker-compose.demo.yml logs -f
```

检查是否有启动错误：

```bash
docker compose -f docker-compose.demo.yml logs --tail 200 | grep -E "ERROR|Exception|Application run failed|Public Key Retrieval|ClassNotFound|No schema scripts"
```

PowerShell:

```powershell
docker compose -f docker-compose.demo.yml logs --tail 200 | Select-String -Pattern "ERROR|Exception|Application run failed|Public Key Retrieval|ClassNotFound|No schema scripts"
```

## 4. 数据库初始化机制

MySQL 容器首次创建 `mysql_data` volume 时会执行：

```text
script/demo-mysql-init.sh
db/*.sql
db_revise/*.sql
```

`db/*.sql` 会按文件名自动创建数据库，例如：

```text
db/auth_db.sql -> auth_db
db/oms_db.sql -> oms_db
```

`db_revise/*.sql` 按脚本自身的 `USE xxx_db` 执行。

业务服务自身的 Spring SQL 初始化默认关闭：

```yaml
SPRING_SQL_INIT_MODE: never
```

不要让各业务服务在容器启动时各自执行 `schema.sql/data.sql`，否则容易出现重复建表、找不到 SQL 文件或初始化顺序问题。

### 邮资数据库和初始资费

`db/postage_db.sql` 创建独立的 `postage_db`，字符集为 `utf8mb4`、排序规则为 `utf8mb4_unicode_ci`，并通过 MySQL `SOURCE` 执行挂载的 `liana-postage-service/src/main/resources/schema.sql`。该 SQL 依赖 Compose 中的 `/demo-postage-schema.sql` 挂载路径；手工部署应先建库，再直接执行服务的 `schema.sql`。

`postage-db-init` 在 MySQL 健康后执行同一脚本，邮资服务等待它成功后才启动。因此，已有 `mysql_data` 也会自动补齐邮资库，无需删除数据卷。脚本仅创建不存在的表和锁记录，保留现有资费和报价。

邮资服务沿用当前演示环境的 `root` 账号及数据库密码，通过 `POSTAGE_DB_URL`、`POSTAGE_DB_USERNAME`、`POSTAGE_DB_PASSWORD` 连接 `mysql:3306/postage_db`，JDBC 时区为 `UTC`。`POSTAGE_SQL_INIT_MODE` 为 `never`，建表由数据库初始化容器负责。

Compose 默认启用 `POSTAGE_SEED_INITIAL_TARIFF=true`，应用从 `initial-tariff.json` 导入初始版本 `LIANA-TARIFF-2026-001`，仅在资费版本表为空时执行，不覆盖管理员配置。初始资费于 **2026-10-15 00:00:00 UTC+08:00** 生效；此前报价返回 `409`。

首次导入成功后，可按邮资服务 README 关闭后续导入检查：

```powershell
$env:POSTAGE_SEED_INITIAL_TARIFF = 'false'
docker compose -f docker-compose.demo.yml up -d liana-postage-service
```

也可在本地 `.env` 中配置 `POSTAGE_SEED_INITIAL_TARIFF=false`。邮资服务启用 Nacos 注册，通过网关访问 `/liana-postage-service/api/postage/...`，需要现有登录 JWT。

只部署邮资服务及所需依赖：

```bash
docker compose -f docker-compose.demo.yml up -d --build liana-postage-service
docker compose -f docker-compose.demo.yml logs --tail 100 postage-db-init liana-postage-service
```

## 5. 更新部署

普通代码更新后，在项目根目录执行：

```bash
docker compose -f docker-compose.demo.yml up -d --build --force-recreate
```

只更新某个服务，例如 `liana-oms-service`：

```bash
docker compose -f docker-compose.demo.yml build liana-oms-service
docker compose -f docker-compose.demo.yml up -d --force-recreate liana-oms-service
```

如果怀疑镜像缓存导致旧 jar 没更新：

```bash
docker compose -f docker-compose.demo.yml build --no-cache liana-oms-service
docker compose -f docker-compose.demo.yml up -d --force-recreate liana-oms-service
```

全量无缓存重建：

```bash
docker compose -f docker-compose.demo.yml build --no-cache
docker compose -f docker-compose.demo.yml up -d --force-recreate
```

## 6. 重置演示数据库

仅演示环境使用。以下命令会删除 MySQL 数据卷：

```bash
docker compose -f docker-compose.demo.yml down -v
docker compose -f docker-compose.demo.yml up -d --build
```

不要在保留数据的服务器环境执行 `down -v`。

普通停止但保留数据：

```bash
docker compose -f docker-compose.demo.yml down
```

## 7. x-ray 安检演示资源

`liana-sorting-service` 会读取 x-ray 演示数据：

```yaml
SORTING_XRAY_ROOT: /opt/liana/x-ray
volumes:
  - ./x-ray:/opt/liana/x-ray:ro
```

当前 Java 逻辑不会运行 Python/YOLO 推理，只读取：

```text
x-ray/data.yaml
x-ray/test/images
x-ray/test/labels
```

服务器部署时可只上传这些演示数据，避免上传模型、训练结果和缓存目录。

建议排除：

```text
x-ray/runs/
x-ray/.ultralytics/
x-ray/.matplotlib/
x-ray/*.pt
```

## 8. 上传服务器部署

演示环境可以直接压缩项目上传服务器，然后在服务器上构建运行。

压缩时建议排除：

```text
.git/
.idea/
**/target/
liana-web/node_modules/
liana-web/dist/
data/
x-ray/runs/
x-ray/.ultralytics/
x-ray/.matplotlib/
*.log
```

服务器目录示例：

```text
/opt/liana-post/
  Dockerfile
  docker-compose.demo.yml
  pom.xml
  db/
  db_revise/
  script/
  liana-*/
  liana-web/
  x-ray/
```

启动：

```bash
cd /opt/liana-post
docker compose -f docker-compose.demo.yml up -d --build
```

## 9. 既有 nginx 和域名接入

如果服务器已有 nginx 和域名，不建议让 `liana-web` 直接占用公网 `80`，也不建议把各业务服务暴露公网。

推荐把 compose 端口改为仅本机监听：

```yaml
liana-web:
  ports:
    - "127.0.0.1:18080:80"

liana-gateway:
  ports:
    - "127.0.0.1:18081:8080"

mysql:
  ports: []

redis:
  ports: []

nacos:
  ports:
    - "127.0.0.1:8848:8848"
```

已有 nginx 示例：

```nginx
server {
    listen 80;
    server_name demo.example.com;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location ~ ^/(liana-auth-service|liana-facility-service|liana-oms-service|liana-dispatch-service|liana-sorting-service|liana-records-service|liana-transport-service)/ {
        proxy_pass http://127.0.0.1:18081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

HTTPS 证书、域名解析和公网入口由既有 nginx 管理。

## 10. 使用外部 MySQL

如果服务器已有 MySQL，可以删除或禁用 compose 中的 `mysql` 服务，并修改各服务数据库 URL：

```yaml
OMS_DB_URL: jdbc:mysql://mysql-host:3306/oms_db?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
OMS_DB_USERNAME: your_user
OMS_DB_PASSWORD: your_password
```

需要为每个业务库分别配置：

```text
AUTH_DB_URL
OMS_DB_URL
DISPATCH_DB_URL
SORTING_DB_URL
TRACKING_DB_URL
FACILITY_DB_URL
TRANSPORT_DB_URL
SYNCER_DB_URL
POSTAGE_DB_URL
```

外部 MySQL 需要提前执行 `db/` 和 `db_revise/` 下的 SQL。

## 11. 常见故障

### no main manifest attribute

原因：容器复制了普通 Maven jar，而不是 Spring Boot 可执行 jar。

当前 Dockerfile 已固定复制：

```dockerfile
COPY --from=build /workspace/${MODULE}/target/*-exec.jar /app/app.jar
```

如果仍出现该问题，执行：

```bash
docker compose -f docker-compose.demo.yml build --no-cache
docker compose -f docker-compose.demo.yml up -d --force-recreate
```

### No schema scripts found

原因：某个服务启动时启用了 `spring.sql.init` 并指向容器内不存在的 SQL。

当前 compose 已设置：

```yaml
SPRING_SQL_INIT_MODE: never
```

如果仍出现该问题，检查是否有 Nacos 配置覆盖，或是否运行了旧镜像。

### Public Key Retrieval is not allowed

原因：MySQL 8 默认认证方式下，JDBC 非 SSL 连接未允许公钥拉取。

当前 JDBC URL 已包含：

```text
allowPublicKeyRetrieval=true
```

如果使用外部 MySQL，也要在外部数据库 URL 里加上该参数。

### ClassNotFoundException: SyncerApplication

原因：`liana-syncer` 曾缺少启动类。

当前已补充：

```text
liana-syncer/src/main/java/com/liana/post/syncer/SyncerApplication.java
```

重建镜像即可。

### MySQL 初始化未执行

MySQL 官方镜像只在数据目录为空时执行 `/docker-entrypoint-initdb.d`。

如果 `mysql_data` 已存在，新增 SQL 不会自动执行。演示环境可重置：

```bash
docker compose -f docker-compose.demo.yml down -v
docker compose -f docker-compose.demo.yml up -d --build
```

保留数据环境需要手工进入 MySQL 执行新增 SQL。

## 12. 验证命令

查看全部容器：

```bash
docker compose -f docker-compose.demo.yml ps
```

查看某个服务日志：

```bash
docker compose -f docker-compose.demo.yml logs -f liana-oms-service
```

查看 Nacos 注册情况：

```text
http://localhost:8848/nacos
```

正常应看到：

```text
liana-gateway
liana-auth-service
liana-oms-service
liana-dispatch-service
liana-sorting-service
liana-tracking-service
liana-facility-service
liana-transport-service
liana-syncer
liana-postage-service
```

## 13. 生产化建议

当前方案适合演示和模拟生产。长期运行建议升级为：

- CI 或本地构建镜像。
- 推送镜像仓库。
- 服务器 compose 只使用 `image:`，不在服务器上 `build:`。
- MySQL、Redis、Nacos 使用受控的持久化和备份策略。
- 对外只暴露既有 nginx。
- nginx 负责域名、HTTPS、证书和公网访问控制。
- 不暴露业务服务端口、MySQL、Redis 到公网。
