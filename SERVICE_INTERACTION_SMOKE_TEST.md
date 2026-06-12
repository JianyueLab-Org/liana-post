# 服务交互流程冒烟测试指引

## 前置条件
- `Nacos`、`Redis`、`MySQL` 已启动，且各库已导入对应 `schema/data`。
- `facility`、`oms`、`dispatch`、`tracking`、`syncer` 服务已启动并注册到 Nacos。
- 如端口不同，请将示例中的端口替换为你的实际端口。

## 一、连通性检查
- `GET /api/facilities/types`
- `GET /api/facilities`
- `GET /api/facilities/routes`
- `GET /api/oms/mails`
- `GET /api/dispatch/bags`
- `GET /api/tracking/events`

## 二、白天收寄流程
1. 在 OMS 创建邮件：`POST /api/oms/mails`
2. 在 Tracking 记录 `ACCEPTED`：`POST /api/tracking/events`
3. 查询 OMS 待发候选：`POST /api/oms/mails/dispatch-candidates`

## 三、夜间封发流程
1. 在 Dispatch 创建总包：`POST /api/dispatch/bags`
2. 封发回写：`POST /api/dispatch/bags/sync-mail`
3. OMS 回写总包号：`POST /api/oms/mails/{waybillNo}/bag`
4. Tracking 记录 `DISPATCHED`：`POST /api/tracking/events`

## 四、下一站接收流程
1. OMS 更新到站状态：`POST /api/oms/mails/{waybillNo}/status`
2. Tracking 记录 `ARRIVED`：`POST /api/tracking/events`

## 五、用户查询验证
- `GET /api/tracking/events/waybill/{waybillNo}`
- `GET /api/tracking/events/{eventNo}`

## 六、Redis 验证点
- 同一 `waybillNo` 连续查询两次，第二次应更快。
- 待发候选重复查询应命中缓存。
- 写入和状态变更后，应看到缓存失效和新结果。

## 七、Syncer 验证
- 仅检查服务启动、Nacos 注册和日志无异常。
- 当前不作为主业务闭环强断言点。

## 八、通过标准
- 白天收寄可生成邮件并记录 `ACCEPTED`。
- 夜间封发可生成 `bagNo`，并完成 OMS / Dispatch 双向回写。
- 下一站接收可推进到 `ARRIVED`。
- Tracking 可查到完整轨迹。
- Redis 不应造成旧数据长期不刷新。
