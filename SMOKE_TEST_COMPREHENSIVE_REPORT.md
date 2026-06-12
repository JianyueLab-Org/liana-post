# Liana Postal System 综合冒烟与修复报告

生成时间：2026-06-09

## 1. 结论

本次 `script/smoke_test.py` 已完成一轮端到端冒烟，并在修复后可以重复运行。当前脚本覆盖了主要业务闭环，但**不能算完全覆盖所有需求**：它覆盖了跨服务核心链路、基础查询、关键状态流转与部分缓存行为；但未覆盖认证授权、同步器内部补偿机制、全部异常分支、并发/压测、以及所有实体/接口的完整组合。

## 2. 已覆盖范围

### 2.1 服务连通性
- `facility`、`oms`、`dispatch`、`tracking` 的基础连通性
- 主要只读接口健康检查

### 2.2 白天收寄链路
- OMS 创建邮件
- Tracking 记录 `ACCEPTED`
- OMS 待发候选查询

### 2.3 夜间封发链路
- Dispatch 创建总包
- Dispatch 封发回写
- OMS 回写总包号
- Tracking 记录 `DISPATCHED`

### 2.4 下一站接收链路
- OMS 更新到站状态为 `ARRIVED`
- Tracking 记录 `ARRIVED`

### 2.5 查询验证
- Tracking 按 `waybillNo` 查询轨迹
- Tracking 按 `eventNo` 查询单条事件

### 2.6 重复执行
- 脚本支持复用已有 `waybillNo` / `bagNo`
- Dispatch bag 更新改为显式更新，避免重复插入
- Tracking 的不存在事件查询已按业务返回 `404`

## 3. 主要问题与修复记录

### 3.1 冒烟脚本参数与字段问题
- 修正了 tracking 请求体里 `operatorId` / `payload` 的类型问题
- 修正了 `dispatch/bags/sync-mail` 的字段名为 `mailNoList`
- 修正了 `dispatch` / `facility` 默认端口写反的问题
- 改造脚本为可复跑模式：存在数据则复用，不强制每次新建

### 3.2 服务运行时依赖问题
- `OMS` 和 `Tracking` 对 `liana-common` 的运行时依赖在本环境中不稳定
- 改为在 `OMS` / `Tracking` 的 Maven 构建中直接引入 `../liana-common/src/main/java`
- 修正了若干 `@PathVariable` 未显式命名导致的反射绑定问题

### 3.3 业务幂等问题
- `dispatch` 的 `sync-mail` 从“二次插入”改为“更新已有 bag”
- `tracking` 的不存在事件查询补充了异常处理，避免 404 被默认转成 500

### 3.4 运行时 500 问题
- `tracking` 的 `GET /api/tracking/events/{eventNo}` 查询不存在记录时，补齐异常映射，返回业务级错误
- `OMS` 内部 waybill 号生成移除对旧 `UpuBarcodeUtil` 运行时依赖

## 4. 当前冒烟测试是否完全覆盖所有需求

**结论：没有完全覆盖。**

原因如下：
- 冒烟脚本主要验证的是**核心业务闭环**，不是全量需求测试
- 未覆盖 `auth` 的完整登录/权限链路
- 未覆盖 `syncer` 的异常补偿、断网恢复、重试成功/失败等内部机制
- 未覆盖所有 CRUD、边界参数、非法状态迁移、空值/重复值等全面异常分支
- 未验证 Redis 的所有缓存一致性细节，只做了有限的流程级观察
- 未做并发、压力、长时间运行、重复幂等回归等系统级验证

因此，当前结果可以说明：
- 主要跨服务链路已打通
- 核心状态流转已可用
- 冒烟测试可以重复运行

但不能说明：
- 所有业务场景都已完全正确
- 所有异常分支都已穷尽
- 所有性能与稳定性要求都已满足

## 5. 建议后续补测项

- `auth` 登录与鉴权回归
- `syncer` 的补偿/重试/断网恢复
- `dispatch` 的重复 bag / batch / handoff 场景
- `tracking` 的更多查询过滤条件与不存在数据返回
- `oms` 的重复创建、重复状态更新、非法状态更新
- Redis 缓存命中/失效的日志级验证
- 多次连续跑冒烟脚本的幂等回归

## 6. 留档说明

本报告用于记录本轮冒烟测试、定位问题和修复过程，便于后续回归和交接。
