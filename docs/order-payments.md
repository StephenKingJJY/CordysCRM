# 订单回款明细

订单直接登记多笔到账，无需关联合同。财务手工填写归属于本订单的金额；一笔打款分给多个订单时，在每张订单分别登记对应金额并备注流水号和分配说明。系统不校验跨订单分配总额。

电脑端：订单详情 → 回款明细。手机端：订单详情底部的回款明细。

每笔包括到账日期、金额、备注和可选凭证（最多 5 个，每个 10 MB，PDF/PNG/JPG/WebP）。金额必须为正数，最多两位小数，到账日期不晚于当天；币种沿用订单，不做换汇。后端用 BigDecimal 汇总有效记录，显示未回款、部分回款、已回款或超额回款。订单金额按两位小数四舍五入后核算。

记录不能直接修改或删除。更正时填写作废原因、作废原记录再重新登记，保留登记人、登记时间、作废人、作废时间和凭证。已有回款历史（含作废记录）的订单不允许删除。

## 权限和隔离

查看及下载凭证使用 ORDER:READ 和订单资源权限。新增、作废使用 ORDER:UPDATE 及订单数据范围校验（CsBatchPermission），不依赖订单当前审批状态。回款单独存储，不更新订单、审批快照、订单状态或领星同步字段。

所有服务访问均检查当前组织与订单，作废及凭证查询同时核对订单、组织和回款关联。提交用客户端生成的 32 位随机 ID 幂等重试；同一 ID 对应不同订单、用户、金额、日期或备注时拒绝。作废操作只更新未作废记录，重复请求保留首次作废信息。外键防止并发删除订单造成孤立回款。

## 数据与接口

新增 `sales_order_payment` 表，由 Flyway `V1.9.1_3__order_payments.sql` 建立。凭证复用附件存储，resource_id 为回款 ID。

- `GET /order/{orderId}/payments`：明细与汇总。
- `POST /order/{orderId}/payments`：multipart，request JSON + 可选 files。
- `POST /order/{orderId}/payments/{id}/void`：作废，JSON reason。
- `GET /order/{orderId}/payments/{id}/receipts/{fileId}`：经过订单权限校验的凭证下载。

## 验证

- `node tests/order-payments.cjs`（frontend/packages/web）：组件交互测试，API 全部为模拟，不访问生产。
- `./mvnw -f backend/pom.xml -pl crm -am test -Dtest=OrderPaymentServiceTest,OrderPaymentPersistenceTest,OrderPaymentPermissionTest -Dsurefire.failIfNoSpecifiedTests=false -DskipAntRunForJenkins`：业务规则及临时 MySQL 的迁移/持久化/外键测试，需 JDK 21 与 Docker。
- Web、Mobile 各自执行 type:check、build；本次变更的 Vue/TS/CSS 执行 ESLint/Stylelint。

## 发布

发布前备份数据库和附件持久卷，并记录当前镜像标识。需要同时发布后端、电脑端和手机端；现有 mobile-only overlay 不包含后端及电脑端变更，不能用于本功能发布。启动后确认 Flyway 新迁移成功；使用隔离的测试订单核验新增、凭证下载、作废和汇总，再由财务使用。

回滚使用原镜像并保留新增表及回款附件，不删除业务记录。注意原镜像不包含禁止删除带回款订单的友好提示，但外键仍保护相关订单。数据库备份只在需要恢复数据库且已核对新增业务数据影响时恢复。


## Exporting order payments

Both **Export all pages** and **Export selected orders** now create one workbook with two sheets:

- **Orders** retains the selected order fields and appends total received, outstanding amount, payment status and the latest effective receipt date.
- **Payment details** has one row per payment, including order number/name, receipt date, allocated amount, remark, recorder/time, valid/voided status, void reason/user/time, receipt filenames and the payment record ID.

Voided records remain in the detail sheet but are excluded from received totals and latest receipt dates. Orders without registered payments show zero received and no latest receipt date. An order expanded into multiple product rows includes its payment totals only once. Money and dates are typed spreadsheet values. Receipt files are not embedded in the workbook; open them in CRM.

The workbook uses the same selection, filters, department scope and approval export permissions as the existing order export. Payment queries are batched only for orders that passed those checks. An empty export still contains both sheets with headers.
