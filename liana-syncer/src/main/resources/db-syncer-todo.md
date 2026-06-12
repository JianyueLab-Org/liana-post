# liana-syncer TODO

- TODO: 明确业务服务写入 outbox 的事件格式与 payload schema。
- TODO: 明确 sync_task 与 outbox_message 的状态流转规则。
- TODO: 明确是否需要 Redis 断网缓存以及恢复后的补偿顺序。
- TODO: 明确是否需要 HTTP 管理接口、健康检查和手动补偿接口。
- TODO: 明确是否需要 MQ 与数据库轮询并存。
- TODO: 明确 retry_record 的唯一约束是否需要按业务幂等键细化。