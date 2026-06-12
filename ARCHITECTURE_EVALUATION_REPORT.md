# Liana-Post 架构评估报告

## 1. 评估范围
本报告基于当前仓库中的父工程 `pom.xml`、各子模块 `pom.xml`、已实现服务代码、数据库修订脚本，以及最新编译结果进行更新。

## 2. 当前总体结论
当前项目已完成以下关键落地：
- 父工程 `pom.xml` 已采用显式版本管理，编译链路稳定。
- `liana-auth-service` 已完成真实 MySQL + MyBatis 落地。
- `liana-oms-service` 已完成真实 MySQL + MyBatis 落地，并复用公共 UPU 工具。
- `liana-dispatch-service` 已完成真实 MySQL + MyBatis 落地，覆盖总包、路由、批次、交接核心能力。
- 新增 `liana-transport-service`，用于邮政运输资源管理、运输线路、运输计划、运输任务。
- `liana-tracking-service` 已支持运输事件类型，能承接运输任务状态事件。
- `liana-web` 已接入运输管理菜单与对应页面。

最新编译结果：
- 执行 `mvn -pl liana-common,liana-dispatch-service,liana-tracking-service,liana-transport-service -am -DskipTests compile`
- 结果：`BUILD SUCCESS`

## 3. 服务清单
### 3.1 已有服务
- `liana-auth-service`
- `liana-oms-service`
- `liana-dispatch-service`
- `liana-facility-service`
- `liana-tracking-service`
- `liana-syncer`
- `liana-gateway`

### 3.2 新增服务
- `liana-transport-service`

职责边界：
- 负责运输资源
- 负责运输线路
- 负责运输计划
- 负责运输任务
- 不负责总包封发
- 不负责邮件基础信息
- 不负责轨迹展示

## 4. 子模块实现状态
### 4.1 `liana-common`
- 作为公共能力包存在。
- 提供 JWT、Jackson、日志、MyBatis-Plus 基础能力依赖。
- 提供运输/封发/跟踪共享 DTO 与常量。

### 4.2 `liana-auth-service`
- 已接入真实持久化实现。
- 已完成基础认证与用户能力。

### 4.3 `liana-oms-service`
- 已接入真实持久化实现。
- 已完成邮件生命周期、筛选与状态流转。

### 4.4 `liana-dispatch-service`
- 已接入真实持久化实现。
- 已实现路由规则、总包生成、批次审核、交接记录。
- 已增加 `transport_task_code` 兼容字段，用于与运输任务关联。
- 已增加按 `dispatch_bag.id` 查询与运输任务回写接口。

### 4.5 `liana-facility-service`
- 已接入服务化结构。
- 已支持设施与线路基础能力。

### 4.6 `liana-tracking-service`
- 已接入真实持久化实现。
- 已支持运输事件类型：
  - `TRANSPORT_ASSIGNED`
  - `DEPARTED`
  - `IN_TRANSIT`
  - `ARRIVED_AT_HUB`
  - `COMPLETED`

### 4.7 `liana-transport-service`
- 新增运输管理模块。
- 已实现运输资源、线路、计划、任务 CRUD。
- 已实现按 `dispatchBagId` 自动创建运输任务。
- 已实现任务状态变更写入 tracking 事件。
- 已实现回写 dispatch 的运输任务关联。

### 4.8 `liana-syncer`
- 作为同步服务保留。

### 4.9 `liana-gateway`
- 作为统一网关入口保留。

## 5. 与需求的差距
### 5.1 已满足项
- Database Per Service 的主体原则已落实。
- `dispatch` 与 `transport` 的跨服务关系已改为业务关联，不做跨库外键。
- `transport-service` 已正式加入项目并完成核心服务边界。
- `tracking-service` 已接收运输事件。
- `web` 已补齐运输管理菜单与页面。

### 5.2 仍建议后续补齐项
- `transport-task` 的详情页与更完整的操作流转。
- `dispatchBagId` 到运输任务的更强约束校验与查询接口。
- 生产环境配置分层，如 `application.yml` / Nacos 配置 / profile。
- 更完整的跨服务幂等与重试策略。

## 6. 结论
当前项目已经从“已有封发/邮件/跟踪”扩展为“封发 + 运输 + 跟踪”的分布式结构。`liana-transport-service` 已成为新的独立服务，并且与 `dispatch`、`tracking` 完成了最小闭环联动。
