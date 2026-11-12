# Sentinel 课程演示开发工作清单

## 目标范围

本清单面向课程演示标准，不按生产级高可用完整落地评估。目标是证明项目已经具备以下能力：

- Sentinel Dashboard 能看到 gateway 和核心业务服务。
- Gateway 能对服务路由或 API 做基础限流。
- OpenFeign 跨服务调用具备降级兜底。
- 下游服务异常、超时或被限流时，前端/接口能收到统一、可解释的降级响应。
- 可以通过一次演示脚本复现限流、熔断、降级效果。

## 需求确认留空

请在开发前补齐以下内容，用于对齐实现颗粒度。

| 确认项 | 待填写 |
| --- | --- |
| 演示重点服务 | `liana-gateway`、`liana-sorting-service`、`liana-oms-service`、`liana-tracking-service` |
| 演示重点接口 | Gateway 限流：`GET /liana-sorting-service/api/v1/sorting/dashboard/summary`；Feign 降级：`sorting -> oms`、`oms -> tracking` |
| Gateway 限流维度 | 默认按 route id：`sorting-alias`；工业系统常见做法是先保护入口路由总量，再扩展 API 分组和热点参数 |
| Gateway 默认 QPS 阈值 | `2`，可通过 `SENTINEL_GATEWAY_QPS` 覆盖 |
| 是否需要热点参数限流 | 本轮不做，作为扩展项 |
| Feign 降级覆盖范围 | 本轮覆盖 `liana-sorting-service -> liana-oms-service`、`liana-oms-service -> liana-tracking-service` |
| 降级返回是否统一使用 Result.fail | 是 |
| 读接口降级时是否允许返回空列表 | 否，返回 `Result.fail`，避免误判为业务成功 |
| 写接口降级时是否允许异步补偿 | 本轮不做异步补偿，写接口不伪造成功 |
| 是否需要 Nacos 持久化 Sentinel 规则 | 本轮不做，先用代码初始化演示规则；Dashboard 手动调整只作为演示临时变更 |
| 是否需要 Docker Compose 启动 Sentinel Dashboard | 是，同时保留本地 jar 启动方式 |
| 演示验收方式 | PowerShell curl + Sentinel Dashboard 应用列表/规则页观察 |

## 推荐演示链路

| 链路 | 演示点 | 建议优先级 |
| --- | --- | --- |
| gateway -> sorting | 网关路由限流 | 高 |
| sorting -> oms | Feign 读接口降级 | 高 |
| oms -> tracking | 轨迹记录失败降级 | 高 |
| dispatch -> sorting | 下游不可用时 fallback | 中 |
| transport -> dispatch | 跨服务查询失败降级 | 中 |

## 开发任务清单

### 1. Sentinel 基础依赖

- [x] 在父 POM 或各服务 POM 中确认 Spring Cloud Alibaba Sentinel 版本由 BOM 管理。
- [x] 为 `liana-gateway` 添加 Sentinel Gateway 相关依赖。
- [x] 为需要演示的业务服务添加 Sentinel starter。
- [x] 确认新增依赖不会和 Spring Boot 3.2.4 / Spring Cloud 2023.0.1 冲突。
- [x] 执行 Maven 编译验证。

验收标准：

- [ ] 服务能正常启动。
- [ ] Sentinel Dashboard 能识别应用。
- [ ] 控制台无 Sentinel 相关启动异常。

### 2. Sentinel Dashboard 运行配置

- [x] 明确 Dashboard 运行方式：本地 jar、Docker、或 docker-compose。
- [x] 补充 Dashboard 启动说明。
- [x] 配置各服务 `spring.cloud.sentinel.transport.dashboard`。
- [x] 配置各服务 `spring.cloud.sentinel.transport.port`，避免端口冲突。
- [x] 将 Sentinel Dashboard 地址写入演示文档。

待确认：

- Dashboard 地址：本地 `127.0.0.1:8858`；容器内 `sentinel-dashboard:8858`
- Dashboard 用户名：`sentinel`
- Dashboard 密码：`sentinel`

本地 jar 启动：

```powershell
java -Dserver.port=8858 -Dsentinel.dashboard.auth.username=sentinel -Dsentinel.dashboard.auth.password=sentinel -jar D:\sentinel\sentinel-dashboard-1.8.9.jar
```

容器化启动：

```powershell
$env:SENTINEL_HOME = 'D:/sentinel'
docker compose -f docker-compose.demo.yml up sentinel-dashboard
```

验收标准：

- [ ] 启动 gateway 后，Dashboard 出现 `liana-gateway`。
- [ ] 调用业务服务后，Dashboard 出现对应业务服务。

### 3. Gateway 限流能力

- [x] 在 `liana-gateway` 接入 Sentinel Gateway Filter。
- [x] 按 route id 或 API path 配置至少 1 条限流规则。
- [x] 对 `/liana-sorting-service/**` 或选定演示接口配置低 QPS 阈值。
- [x] 定制 gateway 限流返回体，保持 JSON 格式。
- [x] 确认限流响应 HTTP 状态码。

待确认：

- 限流 route id：`sorting-alias`
- 限流 path：`/liana-sorting-service/**`
- QPS 阈值：`2`
- HTTP 状态码：`429`
- 返回 message：`请求过于频繁，请稍后再试`

验收标准：

- [ ] 连续快速请求目标接口时触发限流。
- [ ] 返回 JSON，不返回默认错误页。
- [ ] 前端或 curl 能明确看到“请求过于频繁”或类似提示。

### 4. Feign Sentinel 开关

- [x] 在演示服务配置中开启 `feign.sentinel.enabled=true`。
- [x] 确认已有 `@FeignClient` 不因 fallback 配置导致 Bean 冲突。
- [x] 为演示链路中的 Feign Client 增加 `fallbackFactory`。
- [x] fallback 中记录 warn 日志，包含 client 名称、接口语义、异常原因。

建议优先覆盖：

- [x] `liana-sorting-service` -> `liana-oms-service`
- [x] `liana-oms-service` -> `liana-tracking-service`
- [ ] `liana-dispatch-service` -> `liana-sorting-service`
- [ ] `liana-transport-service` -> `liana-dispatch-service`

验收标准：

- [ ] 停止下游服务后，上游接口不直接 500。
- [ ] fallback 返回统一业务响应。
- [ ] 日志能看出触发了 Feign 降级。

### 5. 降级返回策略

- [ ] 梳理读接口、写接口、事件记录接口的降级差异。
- [ ] 读接口 fallback 优先返回 `Result.fail` 或空数据，按需求确认执行。
- [ ] 写接口 fallback 默认不伪造成功。
- [ ] 事件类接口 fallback 可考虑返回可重试提示或写入补偿任务。
- [ ] 保持前端能识别降级失败，不误展示为业务成功。

待确认：

- 读接口降级策略：`Result.fail`
- 写接口降级策略：`Result.fail`，不伪造成功
- 事件记录降级策略：记录 warn 日志并返回 `Result.fail`，本轮不做补偿任务
- Dashboard 接口降级策略：`Result.fail`

验收标准：

- [ ] 降级返回格式与现有 `Result` 模型一致。
- [ ] 写操作降级不会造成“实际失败但页面显示成功”。
- [ ] 关键业务失败信息可从日志追踪。

### 6. Sentinel 规则配置

- [ ] 准备课程演示用流控规则。
- [ ] 准备课程演示用熔断规则。
- [ ] 确认规则是代码初始化、Dashboard 手动配置，还是 Nacos 动态配置。
- [ ] 如果使用 Nacos，补充对应 dataId、group、格式说明。
- [ ] 记录规则恢复方式，避免演示前重启后规则丢失。

待确认：

- 规则存放方式：Gateway 规则代码初始化，Dashboard 可临时调整
- Nacos dataId：本轮不使用
- Nacos group：本轮不使用
- 是否要求规则重启后保留：代码初始化规则重启后保留；Dashboard 手动调整不保证保留

验收标准：

- [ ] 演示前能快速加载规则。
- [ ] 规则变更后无需重新打包服务。
- [ ] 演示脚本能说明每条规则的触发条件。

### 7. 演示脚本

- [ ] 编写正常请求脚本。
- [ ] 编写高频请求触发限流脚本。
- [ ] 编写停止下游服务触发 Feign fallback 的脚本。
- [ ] 编写恢复下游服务后的验证脚本。
- [ ] 记录每一步预期响应。

建议脚本形式：

- [x] PowerShell curl 示例。
- [ ] Postman 集合。
- [ ] 前端页面操作路径。

PowerShell 限流演示：

```powershell
1..10 | ForEach-Object { curl.exe -s -i http://localhost:8080/liana-sorting-service/api/v1/sorting/dashboard/summary }
```

预期现象：部分请求返回 HTTP `429`，响应体为统一 JSON，message 为 `请求过于频繁，请稍后再试`。

验收标准：

- [ ] 5 分钟内能完整演示限流和降级。
- [ ] 每一步都有明确现象。
- [ ] 演示失败时有备用 curl 命令。

### 8. 文档补充

- [ ] 补充 Sentinel 依赖说明。
- [ ] 补充 Dashboard 启动说明。
- [ ] 补充限流规则说明。
- [ ] 补充降级策略说明。
- [ ] 补充演示步骤和截图占位。

截图占位：

- [ ] Dashboard 应用列表截图：``
- [ ] Gateway 限流规则截图：``
- [ ] 触发限流响应截图：``
- [ ] Feign 降级日志截图：``
- [ ] 下游恢复后正常响应截图：``

## 最小完成定义

达到课程演示标准时，至少应满足：

- [ ] `liana-gateway` 已接入 Sentinel。
- [ ] 至少 1 个业务服务已接入 Sentinel。
- [ ] 至少 1 条 gateway 限流规则可演示。
- [ ] 至少 1 条 Feign 调用链可演示降级。
- [ ] 降级响应是统一 JSON。
- [ ] 有可复现的演示步骤。
- [ ] Maven 编译通过。

## 推荐排期

| 阶段 | 内容 | 估算 |
| --- | --- | --- |
| 第 1 阶段 | 依赖接入、Dashboard 启动、基础配置 | 0.5 人日 |
| 第 2 阶段 | Gateway 限流和统一响应 | 0.5 人日 |
| 第 3 阶段 | 核心 Feign fallback | 1 人日 |
| 第 4 阶段 | 演示规则、脚本、文档 | 0.5-1 人日 |

总计建议预留：2-3 人日。
