# Liana Post

Liana Pacific 模拟海岛邮政系统，包含收寄、邮资、封发、路单、陆空海运输、到站分拣及轨迹查询。

```powershell
Copy-Item .env.demo.example .env.demo
./script/demo.ps1 -Action Up
./script/demo.ps1 -Action Check
python script/demo-smoke.py
```

演示入口为 `http://localhost/`。部署依赖、账号、日常启停、备份和清理边界见 [容器部署说明](CONTAINER_DEPLOYMENT.md)。演示密码仅保存在本机 `.env.demo`。

路单本轮开发及固定业务验收已完成：保留模拟逐件扫码，支持同包多段直封、原子开拆与重封、取消改派、差异审核和双端可靠关闭。九库统一初始化已接入正式演示 Compose，新版使用独立持久卷；旧 SQL 入口已从工作树移除并归档于 Git 历史。

- [实际业务验收](database/scenarios/manifest-acceptance-result.md)
- [数据库与发布验证](database/verification.md)
- [数据库工具](database/README.md)
- [数据库设计规范](docs/database-design-standard.md)
- [改版计划与范围](docs/manifest-database-redesign-plan.md)

已知范围：路单编号目前使用独立 UUID；部分旧接口保留 LocalDateTime，页面按 UTC+12 展示；tracking 是尽力投影。这些后续增强不阻塞本轮验收。
