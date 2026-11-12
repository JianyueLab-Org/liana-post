# 邮资前端功能

侧栏新增「邮资业务」：

- **邮资报价** `/postage/quote`：ADMIN，或具备 MAIL_CREATE 权限的 CLERK/MANAGER 可用。读取当前产品、价格和国家分区，输入实际克重报价，显示基本费/附加费、规则、版本、有效期及操作员；按报价编号查询历史明细和重新检查有效性。
- **资费管理** `/postage/tariffs`：仅 ADMIN。查看草稿、生效中、预约及历史版本；新建空白草稿或复制已有版本；通过表单维护产品目录、国家分区、首重续重、重量档和附加费；保存后确认立即或预约发布。已发布定义只读，修改须复制为新版本。
- **邮资零售** `/postage/retail`：柜台售卖面额×张数、确认现金收款、发行及打印数码邮票、查询凭证；管理员可填写原因作废未核销凭证。
- **邮票发行规则** `/postage/stamp-policy`：仅 ADMIN，配置面额上下限及有效天数，记录修订和审计。初始 0.01–100.00 LNR、365 天有效。
- **收寄结算** `/postage/acceptances`：查询逐件结算，恢复异常收寄，OMS 确认取消后释放邮资预占。收寄录入 `/mail/create` 支持传统邮票面额×张数、数码邮票组合、溢贴与差额补购。

发布携带后端 revision；冲突时保留当前输入，并提供重新加载入口。未保存内容切换编辑对象或离开路由时提示。预约时间明确按 UTC+08 解释，不采用浏览器当地时区。收寄须先报价、核对足额邮资，再通过结算入口创建邮件及核销。

## 启动前提

按 `liana-postage-service/README.md` 初始化独立 postage_db 并启动邮资服务；资费在 2026-10-15 00:00 UTC+08 生效。此前报价页面展示“尚无生效资费”，ADMIN 仍可管理版本。

网关、Auth 和 Postage 需正常运行并接入同一 Nacos；登录使用现有员工账号。前端通过 `/liana-postage-service/api/postage/...` 请求网关，保留 JWT。Vite 代理与 Nginx 转发均已加入邮资服务路径。不在前端嵌入价格、签发 JWT 或绕过后端权限。

## 验证

在 liana-web 目录执行：

```powershell
node --test tests/postage-acceptance.test.js tests/postage-retail.test.js tests/postage.test.js tests/session.test.js
npm.cmd run build
```

15 项 Node 测试覆盖贴用金额、重复代码、请求恢复与隐私、零售金额张数、角色菜单、版本生效边界、预约时区、复制隔离、请求路径/JWT/revision/409 错误传递及登录缓存失效。

开发环境启动 `npm.cmd run dev` 后打开 `/tests/postage-ui.html`，页面自动挂载真实 Vue 组件，使用隔离模拟接口验证报价及草稿发布流程，输出 PASS/FAIL。它只用于开发验证，不随生产入口打包，不使用真实 JWT，不写数据库。3 组页面流程已通过。

零售阶段记录见 `../docs/postage-retail-v1.md`。当前逐件收寄、状态恢复、权限和兼容升级见 `../docs/postage-acceptance-v1.md`。不包含在线支付。模拟页面测试不能替代真实服务间收寄验收。

