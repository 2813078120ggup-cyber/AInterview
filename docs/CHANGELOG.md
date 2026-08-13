# 项目更新日志

> 本文件是项目**唯一的持续更新日志**。以后每次功能、配置、数据库迁移、安全修复、部署或文档更新完成后，都在 `Unreleased` 下登记；发布时将条目移动到带日期和版本/基线的章节。本次干净仓库迁移未携带旧变更报告和一次性发布记录，持续历史以本文件为准。

格式采用“新增 / 变更 / 修复 / 安全 / 运维 / 待执行”分类；不记录任何密码、Token、API Key、API Secret、证书私钥、用户简历或回答正文。

## Unreleased

### 当前项目结构文档同步

- 根据当前工作区真实代码同步 `docs/project-structure.md`：补充模块化单体、`account`/`admin` 模块、独立 `algorithm-judge-worker`、Cloud Gateway/Registry、候选人账户设置、企业招聘与管理员运营路由，以及当前 OpenTalking 保留边界。
- 复核 `backend/src/main/resources/db/migration/` 为 33 个版本化脚本、最高 V40，并登记 V34–V40 的实际职责；后续新增数据库迁移必须从实际最新版本之后继续，不能修改已发布迁移。
- 本次提交排除 `.env`、代理/设计工具缓存、前端调试截图、构建产物、依赖目录、运行数据和本地发布暂存目录；未修改数据库或 Docker 数据卷。
- 验证：根 `mvn.cmd -B -ntp test` Reactor 全部 SUCCESS；`npx.cmd tsc --noEmit`、`npm.cmd run lint`（0 errors，22 条既有 Hook warnings）、`npm.cmd run build` 和 `docker compose -p ainterview config --quiet` 通过。前端构建保留既有 CodeEditor 大 chunk warning。

### 登录后共享头像显示

- 右上角共享用户菜单现在优先显示当前用户头像：通过受保护的 `GET /v1/account/avatar/content` 获取 Blob 并使用 Object URL 展示，不把 Token 放入 URL；无头像、头像加载失败或接口暂不可用时继续降级显示姓名首字母。
- 登录后的本地 `Profile` 增加可选 `avatarAvailable` 状态；账户资料页读取、上传或删除头像后会同步本地状态并通知共享菜单立即刷新，保留现有服务端资料作为权威来源。
- Object URL 在用户切换、头像替换和组件卸载时释放，未改变头像上传校验、账户路由、权限和后端接口；仅修改 `frontend-react/src/lib/session.ts`、`frontend-react/src/components/workspace-shell.tsx` 与 `frontend-react/src/pages/candidate-profile.tsx`。
- 验证：`npx.cmd tsc --noEmit`、`npm.cmd run lint`（0 errors，22 条既有 Hook warnings）和 `npm.cmd run build` 通过。未重启 Docker 容器、未修改数据库、未提交、未推送。

### 共享工作区滚动稳定性

- 修复候选人、企业与管理员共享 `WorkspaceShell` 在长页面滚动到底部后，继续反向滚动会带动顶部全局导航而侧边栏保持不动的问题：全局导航改为固定在视口顶部，主内容统一补偿移动端 64px、桌面端 72px 的导航高度，侧边栏与内容的起始位置继续对齐。
- 本次仅调整共享壳层定位与内容偏移，不修改业务页面、路由、接口、权限、数据库迁移、颜色、字体或组件视觉；保留页面原有的浏览器滚动容器与 `window.scrollTo` 行为，避免影响岗位大厅、申请列表等既有回到顶部逻辑。
- 验证：`npx.cmd tsc --noEmit`、`npm.cmd run lint`（0 errors，22 条既有 Hook warnings）和 `npm.cmd run build` 通过；frontend 镜像已单独重建并以 `--no-deps` 替换，未重启 backend、MySQL、Redis 或删除数据卷。真实候选人长日历在桌面端滚动至底部再向上滚动时，顶栏保持 `top=0`、侧栏保持 `top=72px`，页面无横向溢出。
- 按要求停止后续检查，未继续执行企业端、管理员端和移动端的本轮浏览器回归；共享修复由三端统一使用的 `WorkspaceShell` 生效。未新增 Flyway 迁移，未提交、未推送。

### 候选人可见账户安全活动

- 新增候选人本人接口 `GET /v1/account/security-events`，复用 `operation_audit_log` 按时间与 ID 倒序分页；默认每页 15 条、最大 50 条，查询身份只取自当前登录用户，不接受或信任 `userId`，同时纳入本人作为操作者以及本人作为目标用户的安全事件。
- 安全活动覆盖密码/验证码登录成功与失败、新会话创建、密码修改/重置、手机/邮箱/头像/基本资料修改、当前或指定设备退出、其他设备退出，以及管理员停用/恢复账号；登录成功后新增独立 `AUTH_SESSION_CREATED` 审计，密码重置失败在目标可确认时关联用户，无法确认的登录目标不记录完整用户名、手机号或邮箱。
- 响应事件严格限制为 `eventType`、`result`、固定业务 `summary`、`maskedIp`、归纳后的 `deviceSummary`、`createdAt`；不透传原始审计摘要、完整 IP/User-Agent、请求体、Token、密码、验证码、完整联系方式、管理员内部备注、异常堆栈或审计内部 ID。结果仅为 `SUCCESS`、`FAILURE`、`DENIED`，普通候选人没有删除安全活动接口。
- 新接口使用 `hasRole('CANDIDATE')` 做候选人权限隔离；企业用户实测返回 403，未认证请求返回 401，附带其他用户 ID 的请求仍只返回当前用户事件，DELETE 返回标准 405。管理员 `GET /v1/admin/operation-audit-logs` 与导出能力未修改。
- `/candidate/settings/security` 新增“最近安全活动”时间线，置于登录设备之前；默认加载最近 15 条，支持手动刷新和加载更多，使用现有成功/失败/拒绝语义 Token 与业务文案。安全活动和设备列表使用完全独立的 loading/error/已有数据保留状态，任一区域失败不会清空或阻断另一区域，页面隐藏时不发起额外请求。
- 审计补强：密码登录成功和验证码登录成功同时记录新会话创建；已确认账号的登录失败、密码重置失败可关联用户；管理员停用/恢复摘要改为候选人可理解且不含内部备注的固定语义。全局方法不支持异常补充标准 405 响应，避免把不可删除误报为 500。
- 数据库迁移：实查 `backend/src/main/resources/db/migration` 为 33 个 SQL、最高 V40；本步骤复用 V36 已创建并带用户/时间索引的 `operation_audit_log`，不新增迁移、不修改已发布迁移。完整启动与容器重复启动均验证 34 条 Flyway 历史记录（含历史 baseline），schema V40 且无需迁移。
- 测试与验证：新增安全事件服务/控制器/认证审计测试，覆盖当前用户范围、用户数据隔离、分页和上限、响应白名单与脱敏、登录成功/失败、新会话、密码修改/重置、联系方式/头像/资料修改、会话撤销、管理员停用和不可删除；完整 Maven Reactor 280/280 通过，最终相关回归 11/11 通过。前端 typecheck、lint（0 errors，22 条既有 Hook warnings）和生产 build 通过。
- 运行态验证：仅重建并以 `--no-deps` 替换 backend/frontend，未重启 MySQL、Redis 或其他服务，未删除卷。真实 `candidate_liu` 密码登录后，页面与 API 均显示“密码登录成功”和“创建了新的登录会话”，只含白名单字段、脱敏 IP 与设备摘要；API 页 1/页 2 分页、伪造 `userId`、401/403/405 均通过。深层路由刷新、1440/1024/768/390、浅色/深色、无横向溢出和浏览器控制台无 error 已验证。
- 未验证：生产环境部署、管理员真实停用/恢复演示候选人的端到端展示，以及用真实密码/联系方式执行敏感变更后生成活动；这些路径已由服务层审计调用和白名单映射测试覆盖。已知 warning：前端既有 22 条 Hook warning与 Monaco/worker 大分块；后端既有 Jackson deprecated、MyBatis-Plus 复合实体缺少 `@TableId`、Mockito/Byte Buddy 动态 Agent、Springdoc 默认端点、开发环境 Caffeine/主机名提示。未提交、未推送。

### 候选人服务端通知偏好

- 新增候选人本人接口 `GET /v1/account/notification-preferences` 与 `PUT /v1/account/notification-preferences`，覆盖申请状态、面试创建/改期/取消/提醒、报告发布、账户安全和平台公告八类事件；身份只取自当前登录用户，HR 与管理员不进入候选人偏好模型。
- 复用 V40 已建立的 `user_notification_preference`；GET 对缺失记录按服务端安全默认值返回而不批量补数据，PUT 以 `user_id + event_type + version` 条件更新并递增版本，首次保存使用版本 0，冲突统一返回 409。偏好更新写入脱敏 `operation_audit_log`，不包含联系方式。
- 关键业务事件站内信强制开启；`ACCOUNT_SECURITY` 站内信强制开启，已验证且 Provider 可用时邮件也不可关闭；`PLATFORM_ANNOUNCEMENT` 默认开启但允许关闭。邮件/短信只有在对应联系方式已验证且 Provider 可用时才可启用，接口同时返回渠道可用性及不可用原因。
- 将站内通知写入改为事务提交后独立分发：候选人事件会读取服务端偏好，再分别以新事务写入站内信、发送邮件/短信并记录发送审计；偏好查询、单渠道发送或审计失败均被隔离，不回滚申请、面试或报告发布事务。未知事件及非候选人仍保持原有站内通知能力，不应用候选人偏好。
- 招聘申请状态、AI/线下面试创建、面试改期/取消/完成、报告发布和账户安全入口已接入统一候选人事件；Refresh Token、验证码、完整联系方式和 Provider 凭据不进入通知审计或错误摘要。`INTERVIEW_REMINDER` 已纳入偏好与投递模型，当前代码库尚无定时提醒生产器。
- 前端新增懒加载 `/candidate/settings/notifications` 与账户设置导航入口；按事件分组使用响应式卡片展示站内信、邮件、短信，强制项和不可用渠道显示原因。保存中禁止重复提交，失败恢复最近一次服务端确认值，409 提示重新加载，页面隐藏时不额外请求，现有通知中心行为未改。
- 数据库迁移：实施前实查迁移源为 33 个 SQL、最高 V40；完整启动验证 34 条 Flyway 历史记录（含 baseline），当前 schema V40 且重复启动无迁移。本步骤无需新增迁移，也未修改任何已有迁移。
- 测试与验证：通知偏好服务测试覆盖默认值、正常保存、版本冲突、未验证联系方式、Provider 不可用、必开规则与用户隔离；事务监听测试覆盖真实投递读取偏好、非候选人兼容和偏好服务故障隔离。完整 Maven Reactor 271/271 通过（backend 253、algorithm-judge-worker 18），前端 typecheck、lint（0 errors，22 条既有 Hook warnings）和生产 build 通过。
- 真实浏览器使用 Docker 最新 backend/frontend 与演示候选人验证权威 GET、平台公告关闭/恢复的真实 PUT 保存、不可用渠道说明、强制项、深层路由刷新、1440/1024/768/390、浅色/深色、Space/Enter 键盘操作、无横向溢出和控制台无 error/warn；仅重建并以 `--no-deps` 替换 backend/frontend，未重启数据库、Redis 或删除卷。
- 未验证：真实邮件/短信 Provider 投递（当前演示候选人联系方式未验证），生产环境部署，以及尚不存在生产器的定时 `INTERVIEW_REMINDER`。已知 warning：Mockito/Byte Buddy 动态 Agent、MyBatis-Plus 复合实体缺少 `@TableId`、Jackson deprecated、Springdoc 默认端点、开发环境 Caffeine/主机名提示，以及既有 Monaco/worker 大分块提示。未提交、未推送。

### 候选人登录设备与会话管理

- 新增本人会话接口 `GET /v1/account/sessions`、`DELETE /v1/account/sessions/{sessionId}` 与 `DELETE /v1/account/sessions/others`；查询按 `refresh_token.session_id` 聚合同一设备的 Refresh Token 轮换记录，只读取当前用户，并由当前 JWT 的 `sessionId` Claim 在服务端标记当前设备。
- 会话响应仅返回 `sessionId`、`current`、设备类型、浏览器、操作系统、脱敏 IP、首次登录、最近活动和到期时间；不返回数据库 ID、Token/Hash、完整 IP、完整 User-Agent，也不在没有可信地理服务时推断城市或位置。已撤销和已过期会话不再作为可管理设备返回。
- 单设备撤销使用 `user_id + session_id` 归属条件，跨用户或不存在会话统一 404；已撤销会话再次操作保持幂等成功，并发撤销不会产生内部错误。撤销原因分别记录为 `SESSION_REVOKED`、`OTHER_SESSIONS_REVOKED`，所有成功/拒绝操作写入脱敏 `operation_audit_log`。
- “退出其他设备”复用当前会话排除条件，不影响 JWT 对应的当前 `sessionId`；被撤销设备不能再轮换 Refresh Token。未增加 Redis 活跃会话强依赖，已签发 Access Token 最多继续到现有短期过期，Redis 短暂故障不会因此阻断全部认证请求。
- `/candidate/settings/security` 新增“登录设备”卡片列表：当前设备置顶并标记，支持手动刷新、退出指定设备、退出其他设备及 Radix 二次确认；移动端保持卡片布局，加载/空会话/仅当前设备/失败保留列表均有明确状态，不轮询，页面隐藏时不发起额外请求。
- 撤销当前设备成功后清理本机 Access Token、Refresh Token 和 Profile 并返回登录页；撤销失败保留设备列表和本地会话。设备时间均来自后端响应，前端仅按本地时区格式化展示。
- 未新增 Flyway 迁移；实查迁移源仍为 33 个 SQL、最高 V40，完整 Maven 启动验证 34 条迁移记录（含历史 baseline），数据库 schema V40 且重复启动无需迁移。
- 测试覆盖单设备、多设备、Refresh Token 轮换同一 `sessionId`、当前设备服务端识别、退出其他设备、退出当前设备、跨用户/不存在会话 404、已撤销/已过期会话、并发幂等撤销、被撤销 Token 无法刷新、响应字段白名单与 IP 脱敏；完整 Maven 测试 244/244 通过。
- 前端 `npx.cmd tsc --noEmit`、lint（0 errors，22 条既有 Hook warnings）和生产 build 通过；Playwright 使用两个独立上下文验证浅色/深色、390/768/1024/1440、当前设备置顶、键盘确认、退出其他设备、当前设备清理、失败保留列表和无横向溢出，密码安全相关合并回归 4/4 通过。
- 未验证：宿主机现有 8080 旧开发后端对演示账号登录返回 500，运行中 Docker backend/frontend 尚未更新为本步骤代码；未停止既有进程、未重建容器，也未对真实候选人执行会话撤销。生产环境真实双设备和 Access Token 自然过期时间仍待部署后验收。

### 候选人修改密码与忘记密码闭环

- 新增本人密码修改接口 `POST /v1/account/password/change`：请求只接收当前密码、新密码和当前 Refresh Token，不接收用户 ID；复用 `AccountCredentialPolicy` 与 `PasswordEncoder`，校验当前密码、新旧密码不同及 8–64 位、英文字母、数字、半角可打印字符规则。
- 修改密码在同一事务中轮换当前 Refresh Token、提升 `user.security_version`、更新密码 Hash 并撤销其他 Refresh Token 会话；返回当前设备的新 Access/Refresh Token 和明确的会话行为。旧 Access Token 因安全版本不匹配失效，旧 Refresh Token 记录 `ROTATED` 并失效，其他会话记录 `PASSWORD_CHANGED`。
- 新增公开找回接口 `POST /v1/auth/password/reset/code` 与 `POST /v1/auth/password/reset`；只允许已验证手机号或邮箱实际接收验证码，发送接口对存在/不存在目标返回相同公开结构与说明，Provider 未配置或失败返回统一 503，不返回内部异常。
- 密码重置验证码使用独立 `PASSWORD_RESET` 用途和 `auth:password-reset-code:{user|ANONYMOUS}:PASSWORD_RESET:{channel}:{targetSha256}` Key，与注册、登录和联系方式变更验证码隔离；增加按目标摘要的冷却、每日发送次数、有效期和验证失败次数限制，验证码不进入 URL、日志或审计。
- 重置成功后提升 `security_version` 并以 `PASSWORD_RESET` 撤销该用户全部 Refresh Token，不签发新凭据，前端明确要求使用新密码重新登录；停用账号、企业停用成员和不存在目标不获得可利用的重置能力。
- 修改与重置写入脱敏认证/账户审计，并向实际已验证的手机号、邮箱发送安全通知；通知失败写失败审计但不回滚已完成的密码事务。审计摘要不记录密码、密码强度、密码 Hash、验证码、Token 或完整联系方式。
- 前端新增懒加载 `/candidate/settings/security`，与个人资料页共享账户设置分区导航并保留 `context`；安全页提供当前密码、新密码、确认密码、显示/隐藏、真实密码规则、提交状态和明确的跨设备退出说明，不展示安全评分。
- 登录页增加“忘记密码”入口与 Radix Dialog 手机/邮箱找回流程；倒计时以后端响应为准并在页面隐藏时停止刷新，校验失败保留目标、验证码和新密码输入，密码与验证码只保存在组件内存并通过请求体发送。
- 数据库迁移：实施前复核实际最新为 V40；源码目录当前 33 个 SQL 文件，数据库 Flyway schema 为 V40，日志中的 34 条历史记录包含 baseline 记录。本步骤无需新增迁移，未修改任何既有迁移。
- 验证：密码修改、重置、共享密码策略、验证码错误/过期/跨用途、冷却/失败次数、存在性统一响应、Provider 失败、全部/其他会话撤销、JWT 安全版本拒绝、公开/受保护路由和审计脱敏定向测试通过；完整 `mvn.cmd clean test` 通过，234 tests、0 failures、0 errors、0 skipped；`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 通过；Playwright 双设备与找回表单回归 2/2 通过。
- 浏览器验证：本地最新 Vite 页面确认忘记密码入口、Radix Dialog、键盘操作、显示/隐藏密码、真实规则、深层安全路由、390px 浅/深色与无横向溢出；合成双设备会话确认设备 A 凭据轮换且设备 B 旧 Access Token 收到 401。未用真实候选人执行不可逆密码变更，真实短信/邮件 Provider 送达和生产部署未验证。
- 已知 warning：前端 lint 保留项目既有 22 条 Hook warning，构建保留 Monaco/TypeScript worker 大分块提示；后端保留既有 MyBatis-Plus 复合键缺少 `@TableId`、Spring Boot Jackson deprecated、Mockito 动态 Agent、Springdoc 默认端点和开发环境 Caffeine/主机名提示。未提交、未推送。

### 账户联系方式验证、绑定与变更

- 新增当前用户联系方式接口：`POST /v1/account/phone/code`、`PUT /v1/account/phone`、`POST /v1/account/email/code`、`PUT /v1/account/email`。
- 验证码用途独立为 `CHANGE_PHONE` / `CHANGE_EMAIL`；Redis 验证码 Key 按用户、用途和目标 SHA-256 摘要隔离，增加冷却、每日发送次数、有效期和验证失败次数限制，不复用注册、登录或密码重置验证码。
- 联系方式变更要求当前密码、用途验证码、资料版本和当前用户 Refresh Token；使用数据库唯一约束与条件更新，冲突统一返回“该联系方式不可用”。成功后写入对应 `verified_at`，增加 `security_version`，轮换当前 Refresh Token，并撤销其他设备会话。
- 变更后旧联系方式不再作为验证码登录方式；`availableLoginMethods` 只返回实际已验证的 SMS / EMAIL。历史联系方式不会被盲目标记为已验证。
- 旧、新联系方式安全通知失败不会回滚已成功的联系方式事务，并写入失败审计；验证码、Token、完整手机号、完整邮箱、真实路径和完整文件名不写入日志或审计摘要。未配置 Provider 时验证码接口返回 503，不提示虚假发送成功。
- 个人资料页新增联系方式用途说明、脱敏状态和 Radix Dialog 绑定/更换流程；使用后端冷却时间倒计时，页面隐藏时暂停刷新，成功后更新本地会话 Token，失败保留原联系方式和输入。
- 已复核真实数据库 Flyway：当前版本 V40，共 34 个迁移；本步骤无需新增迁移。
- 已验证：后端联系方式、验证码 Key/用途隔离、限流、错密码、唯一性、通知失败、控制器路由、未验证登录方式测试；完整 Maven 测试；前端 lint、typecheck、build；390px 深色 Mock Provider 浏览器流程。
- 未验证：真实短信/邮件 Provider 的实际投递、生产浏览器环境和真实候选人账号的联系方式变更；浏览器 Mock 流程中通知列表请求仍返回既有测试环境 403。

### 候选人账户头像管理

- 新增本人头像接口：`POST /v1/account/avatar`、`GET /v1/account/avatar/content`、`DELETE /v1/account/avatar`；接口从当前认证用户获取身份，不接受用户 ID，不返回对象存储 key、bucket、绝对路径或完整文件名。
- 复用 `media_file`、`LocalObjectStorage` 和 `user.avatar_media_id`；头像上传、替换和删除均写入 `operation_audit_log`，替换不物理删除旧媒体，删除只解除绑定并沿用现有媒体生命周期。保留历史 `avatar_url`，但本流程不接受任意外部 URL。
- 服务端头像限制为 2MB，并同时校验扩展名、声明 MIME 和 JPEG/PNG/WebP 文件签名；PDF 伪装、错误签名、非图片和超限文件均拒绝。内容响应设置正确 MIME、`Cache-Control: private, no-store`、`X-Content-Type-Options: nosniff` 和 `Content-Disposition: inline`。
- 资料页增加受保护 Blob 头像读取、选择文件、视觉预览、上传/替换、删除确认和局部失败状态；上传中禁止重复提交，失败保留当前头像，页面卸载释放 Object URL，Token 不进入 URL。
- 未新增 Flyway 迁移；实施前复核实际 schema 已为 V40，头像字段和外键由已发布 V40 提供，未修改通用简历上传行为。
- 测试：头像校验、控制器响应头、绑定并发冲突、跨用户媒体读取、替换不删旧媒体和删除仅解除绑定的目标测试通过；完整 `mvn -B -ntp -pl backend -am test` 通过；`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 通过。当前 Docker/8080 运行包未刷新到本轮后端代码，真实候选人浏览器上传、替换、删除和运行态响应头未验证；未提交、未推送。

### 候选人账户设置基础页面

- 新增候选人账户设置路由：`/candidate/settings` 重定向到 `/candidate/settings/profile`，旧 `/users` 兼容重定向；两类重定向均保留原有 query 参数，工作区侧栏和用户菜单也会保留当前 `context`。
- 重构 `candidate-profile.tsx` 为单层 `CandidatePageShell` 下的账户资料页，移除静态“可用功能”卡片和原始 `CANDIDATE` 角色代码展示；页面沿用暖中性色语义 Token、Inter、Source Han Serif CN、现有 Card/Badge/Button、Radix 和 Lucide。
- 账户页以 `GET /v1/account/profile` 为权威资料源，localStorage Profile 只用于初始姓名占位；展示头像占位、姓名、只读用户名、候选人账号类型、联系方式脱敏/验证状态、注册时间和最近登录时间，第一阶段仅提交 `realName + version`。
- 增加 loading、完整失败、局部失败、联系方式缺失、无头像、长姓名/用户名、保存中、保存成功和 409 版本冲突状态；版本冲突只提示重新加载，不覆盖服务端资料，保存失败保留输入，成功后同步本地 Profile 的姓名。
- 使用 `useBeforeUnload` 提供未保存离开提醒；工作区退出登录改为先调用现有 `POST /v1/auth/logout`，随后清理本机会话，网络失败时仍清理本机并明确提示服务端撤销状态未确认；Token 不进入 URL。
- 未新增或修改数据库迁移、后端接口或权限业务逻辑；仅复用现有账户资料和认证接口。
- 验证：`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 均通过；浏览器确认深层路由重定向和 `context` 保留、账户菜单入口、退出后回到 `/login` 及无控制台 error/warn。当前 Docker 后端实例仍为未包含账户资料接口/新 JWT Claim 的旧运行包，真实候选人账户资料成功态、保存态、409、1440/1024/768/390 全尺寸和浅/深色矩阵未在该运行实例完成验证。

### 当前用户账户资料后端接口

- 新增独立 `account` 模块和本人资料接口：`GET /v1/account/profile`、`PUT /v1/account/profile`；两者均从 `CurrentUser` 获取当前 `userId`，请求体不接受用户标识，不复用 ADMIN 专用用户管理控制器。
- 资料响应采用严格白名单，仅返回账户身份、姓名、账户类型/状态、头像可用性、联系方式及脱敏值、验证状态、可用登录方式、登录/注册时间和资料 `version`；不返回密码哈希、删除时间、Refresh Token、Token Hash、角色/权限、公司标识、媒体路径或 Provider 信息。
- 第一阶段 PUT 仅允许更新 `realName` 与 `version`：服务端统一去除首尾空格，拒绝全空白和超过 64 个字符的姓名；`username`、角色、状态、companyId、securityVersion 等字段无法通过请求修改。
- 资料更新使用 `user.id + version + status + deleted_at` 条件 SQL，成功时递增 `version`；版本不一致或并发更新影响行数为 0 返回 409，不静默覆盖。停用用户或所属企业停用的企业成员不能读取或更新本人资料。
- 资料更新写入 `operation_audit_log`，摘要不包含完整联系方式；`/v1/auth/me` 保持原有轻量会话身份接口，不扩张登录响应。
- 未新增或修改 Flyway 迁移；复用已发布 V40 的 `user.version` 等字段。未修改候选人前端页面、角色权限业务逻辑或头像页面。
- 验证：新增账户服务与 Controller 合同测试覆盖候选人、企业用户、管理员本人读取、姓名校验、仅本人更新、保护字段不生效、正常更新、并发版本冲突、停用用户/企业拒绝、审计调用、`userId` 查询参数忽略及响应白名单；目标账户相关测试 10/10 通过，完整 Maven 回归通过。

### 账户认证安全底座

- 修复 JWT 认证过滤器：JWT 解析、账号加载或认证异常只清理认证上下文并继续交给 Spring Security，不再因用户不存在或异常 Token 产生 500；停用用户、逻辑删除用户和所属企业已停用的企业成员不会建立认证上下文。
- Access Token 增加 `securityVersion` 与稳定 `sessionId` Claim；服务端实时校验 `securityVersion` 与 `user.security_version` 一致。缺少 `securityVersion` 或会话标识的旧 JWT 按部署后的兼容策略直接拒绝，要求重新登录；不兼容旧 JWT 的行为在此明确记录。
- Access Token 默认有效期由小时级调整为 20 分钟，Refresh Token 继续使用长周期轮换；首次签发创建会话，轮换沿用同一 `session_id`，旧 Token 写入 `last_used_at` 和 `revoked_reason=ROTATED`。
- `/v1/auth/logout` 的服务端撤销具备幂等行为：已撤销 Token 不重复写入内部错误；Refresh Token 所属用户与当前认证用户不一致时不撤销任何会话，并记录脱敏认证审计。
- 用户停用、企业停用企业成员时递增 `security_version`，使既有 Access Token 失效；当前代码未提供修改密码/重置密码接口，后续真实密码变更入口应复用同一 security version 增量机制。
- 登录成功/失败、刷新、退出、旧 JWT、版本失配、停用账号拒绝和认证异常写入 `operation_audit_log`；审计摘要、User-Agent 中禁止保留密码、Token/JWT、验证码、完整邮箱和完整手机号，角色与权限业务逻辑不变。
- 未新增或修改 Flyway 迁移；继续使用已发布 V40 的 `user.security_version` 与 Refresh Token 会话字段。未开发账户设置页面，未修改前端。
- 验证：目标认证安全测试 16/16 通过，覆盖正常/过期/旧 JWT、security version 失配、停用账号/企业成员、用户加载异常、Refresh Token 轮换会话保持、停用账号刷新撤销、logout 幂等、跨用户撤销、未登录 `/api/v1/auth/me` 返回 401 及匿名 logout 拒绝；审计脱敏测试在完整 Maven 中通过。完整 `mvn -B -ntp test` 通过，61 个测试套件共 208 tests、0 failures、0 errors、0 skipped，Reactor 全部 SUCCESS。Flyway 重复启动验证 schema V40 已是最新；`docker compose config --quiet` 与 `git diff --check` 通过。
- 已知 warning：项目既有 MyBatis-Plus 复合键实体缺少 `@TableId`、Spring Boot Jackson API deprecated、Mockito/Byte Buddy 动态 Agent、SpringDoc 默认端点、开发环境 Redis/算法 Worker 连接失败日志；本次完整测试均为 0 failures、0 errors。未提交、未推送。

### 候选人账户设置数据底座

- 新增 Flyway V40 `V40__create_account_security_foundation.sql`：为 `user` 增加可空 `avatar_media_id`、邮箱/手机号验证时间、`security_version` 和普通持久化 `version` 字段；保留 `avatar_url` 旧字段及历史值。
- 为 `user.avatar_media_id` 增加指向 `media_file` 的 `ON DELETE SET NULL` 外键，删除头像媒体不会物理删除用户。
- 为 `refresh_token` 增加独立 `session_id`、`last_used_at` 和 `revoked_reason`；迁移时已有 453 条 Refresh Token 按行生成独立 UUID 会话，未按设备、IP 或 User-Agent 合并；新增用户会话查询和会话状态索引。
- 新增 `user_notification_preference` 表，按用户和事件类型唯一；不批量写入历史默认偏好，不回填历史邮箱/手机号验证时间。
- 同步 `UserAccount`、`RefreshToken`、`UserNotificationPreference` 实体及 Mapper。当前 `user.version` 未标记 MyBatis-Plus `@Version`，不改变既有管理员 `updateById` 行为。
- 注册流程在服务端验证码校验成功后记录 `phone_verified_at`，不将可选邮箱标记为已验证；未修改前端注册页面。
- Refresh Token 首次签发创建新 session，轮换沿用同一 session，并记录旧 Token 的 `last_used_at` 和 `ROTATED` 撤销原因。
- 验证：实际数据库从 V39 成功迁移到 V40；重启 backend 后 Flyway 校验通过并报告 schema 已是 V40、无需迁移。迁移时现有用户 81 条、Refresh Token 453 条、角色 7 条、用户角色关系 104 条仍在；历史 Token 均有独立 session_id；通知偏好为空；运行中 backend healthy。完整测试期间产生的 2 条登录会话未被清理。
- 测试：`mvn -B -ntp -pl backend -Dtest=AuthServiceRegistrationTest,RefreshTokenServiceTest,AuthServiceCompanyStatusTest,JwtPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过，9 tests、0 failures、0 errors；`mvn -B -ntp test` 通过，reactor 全部 SUCCESS。未修改前端，未提交、未推送。


### Features 企业级产品文案优化

- 重写公开 `/features` 的产品叙事：主标题收敛为“见人，见岗，见依据”，章节标题采用“一岗一档，一面一据”“循简历而问，依回答再问”等克制短句；正文统一按能力、依据、使用边界和责任主体说明，减少口号式、拟人化和模板化表达。
- 统一岗位匹配、简历分析、智能面试、评估报告、企业复核、线下面试邀请与招聘流程演示中的业务名称和状态文案；明确系统提供过程记录与评估参考、录用结论由企业作出，候选人报告仍以企业发布为可见边界。
- 移除“AI 思考中”“理解岗位，也理解回答”“恭喜通过 AI 面试”等拟人化或越权结论文案，并删除“高于岗位均值”等无数据来源的比较性表述；交互演示、页面结构、视觉 Token、字体、路由和业务接口均保持不变。
- 验证：`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 通过；lint 为 0 error，保留项目既有 22 条 Hook warning，构建保留 Monaco、TypeScript worker 与既有大 chunk warning。浏览器检查 1440×900 与 390×844，页面无横向溢出、控制台无 error/warning，线下面试邀请交互文案正常切换。
- 本地 Docker 交付：`docker compose -p ainterview config --quiet` 与 `docker compose -p ainterview build frontend` 通过；本步骤使用 `docker compose -p ainterview up -d --no-deps frontend` 仅重新创建前端容器，未操作数据卷或算法判题 Worker。执行窗口内另有并发进程独立重建并重启 backend，并非本步骤的 `--no-deps` 命令触发；复核时 backend 已恢复 healthy、Flyway schema 为 V40，算法判题 Worker 仍保持 1 个运行实例。前端运行容器镜像摘要与本轮 `ainterview-frontend:latest` 一致，容器内 Features 分包包含新版企业级文案，`nginx -t` 通过。
- 运行态验证：宿主机 `/` 与 `/features` 返回 200，入口 HTML 返回 `no-store`，hash JavaScript 返回 `public, max-age=31536000, immutable`，PDF.js `.mjs` 返回 `application/javascript`。未执行生产远程部署；`/features` 仍使用本地演示数据，不调用生产招聘接口，OpenTalking 上游本轮未调用。

### 三端企业级产品语言治理

- 统一候选人、企业和超级管理员工作台的表达：候选人聚焦申请、面试和能力发展；企业聚焦岗位、申请、面试和待处理事项；管理员聚焦租户、平台运行和服务状态。导航、页面标题、按钮和空状态由口号式表达改为可执行的业务名称。
- 候选人端将工作台、岗位大厅、申请和简历入口统一为“个人总览”“岗位大厅”“申请记录”“简历管理”；企业端将工作台、面试和分析入口统一为“招聘总览”“面试安排”“招聘分析”，并将行动中心改为“处理队列”。
- 管理员端将“先处理最重要的异常”改为“平台运行总览”，将异常区域改为“服务异常与处理队列”；AI 管理导航收敛为“模型服务”“生成规则”“模型调用记录”，技术字段仍保留在必要的管理详情中。
- 管理员企业列表指标改为“本页在招岗位”和“本页申请”，对接口数字进行安全数值转换和千分位格式化，避免异常字符串导致重复数字显示；不改变接口和统计范围。
- 面试中心移除“把每场面试安排在正确的下一步”等口号式标题，改为“面试管理 / 面试安排”，说明聚焦面试形式、服务端时间和状态记录。
- 验证：`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 通过；lint 保留项目既有 22 条 Hook warning，构建保留 Monaco、TypeScript worker 和 PDF.js 的既有大 chunk warning。使用 `docker build --network=host -t ainterview-frontend:latest frontend-react` 成功构建并通过 `docker compose -p ainterview up -d --no-deps frontend` 更新本地前端容器，未操作数据卷。
- 真实浏览器复核：候选人 `candidate_liu` 检查 `/workspace`，企业账号 `xingyun_hr` 检查 `/company`、`/company/interviews`，超级管理员 `admin_zhang` 检查 `/admin/workspace`、`/admin/companies`；确认三端新标题、导航、指标和面试中心文案已由运行中的容器提供。无后端接口、数据库迁移或业务状态逻辑变化。

### 本轮 HR 与超级管理员改造最终本地交付验收

- 完成本地交付复核：重新读取 `docs/CHANGELOG.md` 与实际迁移目录；迁移源共 32 个文件，最新为 `V39__add_role_version.sql`。运行数据库 `flyway_schema_history` 有 32 条成功记录，最新版本为 V39；本次验收未新增迁移、未修改既有迁移。
- 后端验证：`mvn -B -ntp -pl backend -am test` 通过，175 tests、0 failures、0 errors、0 skipped；Flyway 日志显示 schema 当前为 V39 且无需迁移。`docker compose -p ainterview config --quiet` 通过；`git diff --check` 无差异错误。
- 前端验证：`npm.cmd run lint` 通过，0 errors，保留项目既有 22 条 Hook warning；`npm.cmd run build` 通过，保留 `CodeEditor` 约 3.98 MB、TypeScript worker 约 7.03 MB 既有大 chunk warning。前端路由仍使用 lazy-load。
- 镜像与服务：`docker compose -p ainterview build backend frontend` 中 backend 构建成功，frontend 首次因外部 npm 网络 `ECONNRESET` 失败；随后使用 `docker build --network=host -t ainterview-frontend:latest frontend-react` 成功完成同一 Dockerfile 构建与前端打包，未修改代码或依赖。使用 `docker compose -p ainterview up -d --no-build mysql redis cloud-registry backend cloud-gateway frontend algorithm-judge-worker` 启动相关服务，未删除数据卷；`algorithm-judge-worker` 保持 1 个实例。
- Docker 状态：MySQL、Redis、backend、cloud-gateway、cloud-registry、algorithm-judge-worker 均为 healthy；frontend 容器运行中并通过真实 HTTP 验证。backend 容器内 `/actuator/health` 返回 `UP`。网关代理 `/api/actuator/health` 本次仍返回脱敏 500，未据此宣称该代理管理端点通过；不影响业务路由、容器健康检查和后端内部管理端点验收。
- 前端交付物：容器实际运行镜像与 `ainterview-frontend:latest` 镜像 ID 一致；容器中的 `/usr/share/nginx/html/index.html` 引用的 hash bundle 存在，`GET /` 返回 200 且 `Cache-Control` 包含 `no-store`；hash assets 返回 `public, max-age=31536000, immutable`；PDF.js `.mjs` worker 返回 `application/javascript`。
- 自动化与浏览器验收：`npm.cmd run test:e2e` 通过，7/7（约 27 秒）。真实候选人验证 `/candidate/interviews`、`/jobs`；企业账号验证 `/company`、`/company/positions`、`/company/interviews`、`/company/analytics`；超级管理员验证 `/admin/workspace`、`/admin/companies`、`/admin/users`、`/admin/recruitment`、`/admin/ai-operations`。回归覆盖 1440/1024/768/390、浅色/深色、reduced motion、键盘移动抽屉、深层路由刷新、loading/empty/error、无横向溢出和控制台错误检查。
- 权限与数据边界：企业 A 无法读取企业 B 的申请、简历、面试和报告，负向用例返回 403/404；企业 B 管理员只能读取本企业申请，企业 A 面试官只能读取被授权范围。服务端继续强制 `companyId`、角色/权限和报告/简历白名单，不向页面或日志暴露密钥、Token、完整简历、回答正文、Prompt 原文、Provider 原始响应或内部堆栈。
- 日志审计：对 backend、frontend、cloud-gateway、cloud-registry、algorithm-judge-worker 最近 30 分钟日志执行脱敏模式扫描，bearer/secret assignment/raw prompt/raw answer/测试简历与回答正文匹配数均为 0；日志仅输出计数，不记录敏感正文。
- 外部依赖与待执行：本轮没有伪造 Provider 成功，也未使用 Mock Provider；新报告完整 Provider 生成/发布、OpenTalking 上游与真实媒体分段、真实邮件/短信送达、高并发、生产部署和生产回滚/备份未验证。OpenTalking 未在本轮浏览器回归中调用，未恢复 avatar-skill 或讯飞链路。按要求未执行生产远程部署、未提交、未推送。

### HR 与超级管理员自动化端到端回归

- 新增最小 Playwright 接入：`frontend-react/playwright.config.ts`、`frontend-react/e2e/hr-admin-regression.spec.ts` 和 `test:e2e` 脚本；不改变 Vite、Docker 运行方式，使用现有 Chromium/Chrome，测试数据按运行标识追加创建，不删除用户数据、数据库或 Docker 数据卷。
- 自动化回归覆盖 7 个场景：超级管理员创建企业与企业管理员、HR 岗位/申请流程、PDF/DOCX 上传、画像与匹配历史、AI 面试创建/重复创建保护/开始/作答/结束、报告任务状态与既有真实报告的 HR/候选人查看、线下面试与终态推进、服务端运营/审计页面、企业 A 对企业 B 申请/简历/面试/报告的 403/404 隔离，以及 1440/1024/768/390、浅色/深色、reduced motion、键盘移动抽屉、深层路由刷新、empty/error 状态和无横向溢出检查。
- 验证：`npx.cmd playwright test` 7/7 通过（最终总耗时约 1.3 分钟，含登录限流退避；实际用例执行约 26 秒）；岗位草稿→发布由真实 HR 浏览器操作完成；账号矩阵额外确认企业 A 面试官可读取受限面试列表、企业 B 管理员可读取本企业申请；loading 通过受控延迟响应观察，empty/error 通过受控 403 页面状态观察；`npx.cmd tsc --noEmit` 通过；`npm.cmd run lint` 通过（0 errors，项目既有 22 条 Hook warning）；`npm.cmd run build` 通过，仍报告既有 `CodeEditor` 约 3.98 MB、TypeScript worker 约 7.03 MB 大 chunk；`git diff --check` 无差异错误。
- 后端与容器验证：`mvn -B -ntp -pl backend -am test` 通过，175 tests、0 failures、0 errors、0 skipped；实际 Flyway schema 为 V39、无需迁移。`docker compose -p ainterview ps` 显示 backend、cloud-gateway、cloud-registry、algorithm-judge-worker、MySQL、Redis、frontend 及监控服务 healthy；前端 `GET /` 返回 200，backend 容器内管理端点返回 `status=UP`。网关代理 `GET /api/actuator/health` 仍返回 500，未据此宣称网关管理端点通过，也未重启服务或操作数据卷。
- Provider 边界：本套件没有伪造成功的外部 Provider，也没有使用 Mock Provider；登录、岗位、上传、投递、企业范围读取、AI 面试创建/开始/回答/结束和报告任务状态均走真实后端。新建面试的报告最终生成/发布取决于当前运行环境的真实 Provider 配置，测试只记录 `READY/PUBLISHED/GENERATING/PENDING/PROCESSING/FAILED/UNAVAILABLE` 任务状态，不把 Provider 不可用误判为业务淘汰；报告查看使用现有真实 `PUBLISHED` 报告验证 HR 与候选人可见性。OpenTalking 未在本轮面试回归中调用；未恢复 avatar-skill 或讯飞链路。
- 未验证：真实 Provider 在当前环境完成一条新报告并由 HR 点击发布、OpenTalking 上游/真实媒体录制分段、真实邮件/短信送达、生产发布和高并发压力；候选人开始/作答/结束为真实 API 合同回放，页面与权限/结果页使用浏览器验证。预期越权 403/404 资源提示在对应负向测试中豁免浏览器资源错误日志，脚本异常和其他控制台错误仍严格失败。

### HR 与超级管理员 React 结构与性能治理

- 将 `frontend-react/src/pages/admin-interviews.tsx` 从 2156 行收敛为 157 行组合层，抽取 typed API、`useAdminInterviews` 数据加载 Hook、筛选、表格/移动卡片列表、创建弹窗、操作确认、通知弹窗和报告抽屉；保留现有管理员路由、接口、筛选、创建/批量排期、通知、报告重试和删除/通过行为。
- 面试数据与候选人、题库、报告请求改为 `Promise.allSettled` 并行加载；单个请求失败时不清空已有可用数据，统一保留局部内容并显示可重试提示。题库题目按需缓存，页面失焦或隐藏时不启动刷新；报告重试在页面隐藏时暂停轮询，恢复可见后继续。
- 新增 `LazyCodeEditor`，算法题目和提交详情只在编辑器实际渲染时加载 Monaco；保留路由 lazy-load 和独立 `CodeEditor` chunk。未恢复 avatar-skill 或讯飞链路。
- 验证：`npx.cmd tsc --noEmit` 通过；`npm.cmd run lint` 通过且本次涉及文件 0 error，项目仍有既有 22 条 Hook warning；`npm.cmd run build` 通过。拆分后 `admin-interviews` chunk 为 40.25 kB（gzip 11.01 kB，旧基线约 39.82 kB），`CodeEditor` 为 3,977.13 kB、TypeScript worker 为 7,031.83 kB，新增 `LazyCodeEditor` 0.77 kB；构建仍报告 Monaco 大 chunk，但其不进入 HR/管理员路由首屏。
- Docker 验证：仅重建并更新 `frontend`，容器运行正常，`GET /admin/interviews` 返回 200；未重启后端、未修改数据库或数据卷。
- 真实浏览器验证：管理员 `admin_zhang` 检查 `/admin/interviews`、创建面试弹窗和移动布局；企业账号 `xingyun_hr` 检查 `/company` 与 `/company/interviews`，桌面约 1280px、移动 390px 均无横向溢出，控制台无 error；未提交创建、通知、删除、通过、报告重试等会改变演示数据的操作。
- 未验证：生产环境发布、高并发压力、真实 Monaco 编辑输入和完整桌面断点矩阵；本次未运行 Maven，因为没有后端代码或迁移变化。

### 超级管理员 AI 与运维模块统一

- 新增管理员 AI 中心 `/admin/ai-operations`、关联追踪 `/admin/ai-operations/traces/generations/:id` 和运维健康 `/admin/operations`；保留原有 Prompt、AI 调用审计、Provider 配置和操作日志路由，并将它们收拢到 AI / 运维业务域上下文侧栏。
- 新增 `GET /v1/admin/ai-operations/overview`、`GET /v1/admin/ai-operations/traces/generations/{id}`、`POST /v1/admin/ai-operations/tasks/{id}/retry` 和 `GET /v1/admin/operations/summary`。AI 汇总由数据库聚合返回最近 24 小时调用、成功/失败、耗时、Token、任务积压和报告积压；关联追踪只返回业务记录、AI task、generation request、Provider/model、Prompt 版本和业务结果的白名单引用，不返回任务输入输出、简历/回答正文、Prompt 正文、Provider 原始响应、密钥或内部异常堆栈。
- 平台任务重试要求显式确认，仅允许受支持的技术失败任务，复用原任务去重键并对非失败任务幂等返回；不推进招聘申请决定，成功、拒绝和幂等无操作均写入服务端追加式审计。Provider 测试返回明确 `SUCCESS`、`FAILED` 或 `TIMEOUT` 状态，异常消息脱敏并保留超时边界。
- 新增脱敏健康聚合，覆盖 backend、cloud-gateway、cloud-registry、algorithm-judge-worker、MySQL、Redis、AI Provider、AI/报告任务积压和 OpenTalking 上游；只展示状态、处理建议和 Grafana 同源跳转 `/grafana/`，不向业务页面暴露内部 URL、密码、Token、密钥或堆栈。虚拟人聚合仅保留 OpenTalking，不恢复 avatar-skill 或讯飞链路。
- 前端新增统一 Trace map 和可点击关联入口，Provider/Prompt/任务区使用现有语义 Token、Card、Badge、Button 与 Lucide；深层追踪路由刷新后仍选中 AI 业务域，移动端使用现有抽屉逻辑。Nginx 通过同源 `/grafana/` 代理详细指标，并重写登录跳转到子路径。
- 未新增 Flyway 迁移；本次实查迁移源与运行数据库最新版本均为 V39，未修改既有迁移。
- 验证：完整 Maven 测试 175/175 通过（0 failures、0 errors、0 skipped）；新增 AI 运维脱敏、ADMIN 权限、关联追踪、显式确认和审计测试；`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 通过，lint 保留项目既有 22 条 Hook warning，build 保留既有 CodeEditor 大 chunk warning；Compose 配置校验通过，backend/frontend 镜像重建并运行，backend、gateway、registry、judge worker、MySQL、Redis、Prometheus、Alertmanager、Grafana 均 healthy；未登录 AI/运维接口返回 403，前端路由返回 200，Grafana 子路径返回带 `/grafana/` 的登录重定向。
- 真实管理员浏览器验证：AI 中心真实读取调用、Provider、Prompt、任务和追踪链路；运维页真实读取脱敏健康摘要和处理建议；1440px 桌面、390px 移动端无横向溢出，浅色/深色模式和移动导航键盘 Escape 关闭均通过，控制台无 error/warn。未实际触发外部 Provider 成功/超时请求或提交任务重试，避免改变演示任务和产生外部调用；高并发、生产发布和真实 Grafana 登录后的详细仪表盘仍待执行。

### 超级管理员只读招聘运营

- 新增超级管理员招聘运营路由 `/admin/recruitment` 与 `/admin/recruitment/applications/:id`，以平台异常定位为核心，支持按企业、岗位、申请阶段、关键词、提交时间和未推进阈值进行服务端分页筛选，并展示跨企业阶段漏斗、长时间未推进申请以及企业、候选人、匹配任务、面试和报告任务之间的脱敏关联。
- 新增管理员接口 `GET /v1/admin/recruitment/applications`、`GET /v1/admin/recruitment/summary`、`GET /v1/admin/recruitment/applications/{id}` 和 `POST /v1/admin/recruitment/tasks/{taskId}/retry`；所有跨企业读取与任务重试均由 `ADMIN` 服务端授权，数据库负责筛选、分页和漏斗聚合，不在前端加载全部申请后统计。
- 列表与详情只返回白名单关联投影，不返回内部岗位/简历快照、联系方式、Prompt 原文、Provider 原始响应、密钥、内部异常堆栈或原始任务输入；页面明确保持只读，不提供替企业录用、淘汰或发布报告的操作入口。
- 技术任务重试仅允许关联招聘申请的 `JOB_MATCH`、`AUTO_EVALUATION` 失败任务；重试前要求明确确认，沿用原任务与去重键，非失败任务幂等返回，业务数据未准备完成等失败原因拒绝自动重试；重试成功、拒绝和幂等无操作均写入服务端追加式审计，且不改变申请阶段。
- 未新增 Flyway 迁移；本次实查迁移源与运行数据库最新版本均为 V39，未修改既有迁移。
- 验证：`AdminRecruitmentServiceTest` 6/6 通过；完整 Maven 测试 171/171 通过（0 failures、0 errors、0 skipped）；`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 通过，lint 保持项目既有 22 条 Hook warning，build 保持既有 CodeEditor 大 chunk warning；Docker backend/frontend 重建并运行，backend、gateway、registry、MySQL、Redis、judge worker 和监控服务均 healthy；未登录访问列表、汇总和详情接口均返回 403，`git diff --check` 无差异错误。
- 真实管理员浏览器验证：列表和详情深层路由刷新可恢复，筛选条件保存在 URL，管理员上下文侧栏正确选中，1280px 与 390px 布局无横向溢出，控制台无 error/warn；未实际提交重试按钮，避免改变演示任务数据，重试写入路径由后端测试覆盖。未执行生产发布与高并发压测。

### 超级管理员用户与权限模块

- 新增真正的用户与权限管理入口：`/admin/users`、`/admin/users/:id`、`/admin/roles`；保留 `/admin/candidates` 作为候选人业务档案入口，旧路由与管理员壳层兼容不变。
- 用户列表改为后端 SQL 投影分页，支持 `roleCode`、`companyId`、`status`、`createdFrom`、`createdTo` 与关键词筛选，返回账号、姓名、角色、企业、状态、最近登录和创建时间；候选人账号入口改为服务端 `roleCode=CANDIDATE` 查询，不再先取 100 条后在前端过滤。
- 新增 Flyway `V39__add_role_version.sql`，在实际最新 V38 之后增加角色版本号；未修改 V27–V38。角色权限保存使用版本条件更新，冲突返回 409；按 `resourceType` 展示权限矩阵，并显示受影响用户数。
- 平台管理员可创建用户、启停账号和调整角色；企业角色必须绑定有效企业，企业成员只能分配企业角色。`ADMIN`、`CANDIDATE`、`COMPANY_ADMIN`、`COMPANY_RECRUITER`、`COMPANY_INTERVIEWER`、`HR`、`INTERVIEWER` 等系统角色代码受保护；服务端通过行锁与计数保护最后一个启用中的超级管理员，所有成功和拒绝的变更写入服务端审计。
- 验证：Flyway 实际 schema 为 V39；完整 Maven 测试 165/165 通过（0 failures、0 errors、0 skipped），新增用户筛选、企业绑定、最后超级管理员保护、系统角色保护、权限影响确认和版本冲突测试；`npx.cmd tsc --noEmit`、`npm.cmd run lint`、`npm.cmd run build` 通过，lint 保持项目既有 22 条 Hook warning，build 保持既有 CodeEditor 大 chunk warning；Docker backend/frontend 重建并运行，backend、gateway、registry、MySQL、Redis、judge worker、监控服务均 healthy；未登录访问用户列表、用户详情和角色接口均返回 403；管理员浏览器验证用户筛选、深层用户详情和角色权限矩阵，1280px 下无横向溢出且控制台无错误。
- 未验证：当前浏览器工具未提供真实 390/768 viewport 调整，因此移动端实际尺寸和移动端键盘交互未在本轮完成；未提交表单、未停用账号、未修改角色权限，真实写入路径由后端权限测试覆盖。

### 超级管理员企业管理
- 新增管理员企业管理路由 `/admin/companies`、`/admin/companies/:id`，并将“企业管理”接入超级管理员租户业务域；企业列表支持服务端分页、名称/简称/编码搜索和启用状态筛选，详情展示招聘中岗位数、历史申请数、企业成员数和进行中面试数。
- 新增 `GET/POST/PUT /v1/admin/companies`、`PUT /v1/admin/companies/{id}/status`、`GET/POST /v1/admin/companies/{id}/members`；平台管理员可创建企业、编辑现有资料、启用/停用企业和创建/分配企业管理员。创建成员时企业归属由路径服务端确定，不接受请求体指定其他 `companyId`。
- 企业状态更新不提供物理删除；停用前服务端检查招聘中岗位和进行中面试，存在风险时必须显式确认，并将成功、失败阻止和成员分配写入服务端追加式审计。停用后企业成员不能登录企业工作区，已存在的岗位、申请、面试和审计历史保留，候选人端不再展示或接受已停用企业的新岗位投递。
- 未新增 Flyway 迁移；实查迁移源与运行数据库最高版本均为 V38，现有 `company` 字段已覆盖本模块资料和招聘联系人需求。
- 验证：完整 Maven 测试 158/158 通过（0 failures、0 errors、0 skipped）；前端 `npm.cmd run lint` 通过（0 errors、22 条既有 Hook warnings）；`npx.cmd tsc --noEmit` 和 `npm.cmd run build` 通过（保留既有 CodeEditor 大 chunk warning）；Docker backend/frontend 重建后 backend、gateway、registry、MySQL、Redis、算法 Worker 和监控服务 healthy；未登录 `/api/v1/admin/companies` 返回 403；`git diff --check` 无差异错误。
- 真实管理员浏览器验证：列表真实读取企业聚合数据，筛选条件保存在 URL，企业详情深层路由刷新可恢复，创建企业表单可打开/关闭；重启后再次验证列表与详情，页面无横向溢出（当前浏览器视口 1280px），控制台无错误。为保护演示数据，未在浏览器提交创建、资料编辑、成员创建或启停操作；这些写入、停用风险确认和审计路径已由后端测试覆盖。
- 未验证：当前浏览器工具不提供真实 390/768 viewport resize，因此移动端实际尺寸和键盘回放未完成；未执行生产发布及高并发压测。

### 超级管理员壳层与平台工作台
- 管理员端沿用“平台 / 租户 / 业务 / 内容 / AI / 运维”六个顶部业务域和上下文侧栏，保留原有管理员路由，并补充 `/admin/dashboard`、`/admin/index` 到 `/admin/workspace` 的兼容重定向；管理员页面移除重复通用页头与外层内边距。
- 新增 `GET /v1/admin/workspace/summary`，由数据库聚合返回企业数量、活跃用户、招聘中岗位、本周申请、进行中面试、报告积压、AI 失败任务、待处理工单、判题队列及脱敏行动中心状态；前端不再加载全部面试统计，不返回数据库地址、Redis 密码、服务 Token 或内部异常堆栈。
- 平台工作台改为“先处理最重要的异常”布局，保留面试、候选人、题库、工单、AI 审计、操作日志和系统设置等既有业务入口。
- 验证：Maven 全量测试通过；React lint 通过（0 errors，22 条既有 Hook dependency warnings）；前端 build 通过但保留既有 CodeEditor chunk warning；Docker backend、gateway、registry、algorithm judge worker、frontend 运行健康；Flyway 实际最新版本为 V38；未登录访问 `/api/v1/admin/workspace/summary` 返回 403；真实管理员浏览器验证六域导航、聚合数据、旧路由重定向、用户菜单 Escape 关闭、浅色/深色主题和桌面无横向溢出。
- 未验证：本轮浏览器工具不提供真实 viewport resize，因此 390/768 移动端实际尺寸与移动抽屉键盘回放未完成；生产发布、高并发和第二管理员账号回放未执行。

### HR 组织与数据模块

- 2026-08-12 实查迁移目录与运行数据库最新版本为 V37，本步骤新增下一个可用迁移 V38 `add_company_recruitment_contact`；未修改 V27–V37。企业设置接口 `GET/PUT /v1/company/settings` 增加招聘联系人姓名、邮箱和手机号字段，所有读写均从当前登录企业解析 `companyId`，不接受调用方指定租户。
- 团队接口继续使用 `GET /v1/company/team`、`POST /v1/company/team`、`PUT /v1/company/team/{userId}/roles` 和 `PUT /v1/company/team/{userId}/status`；成员列表改为 `company:read`，创建、角色变更和启停仍必须具备 `company:team:manage`。最后一位启用中的 COMPANY_ADMIN 保护逻辑保持服务端锁定校验，跨企业成员按不存在处理。
- 新增 HR 路由 `/company/settings`、`/company/team`、`/company/analytics` 和 `/company/analytics/positions`，沿用工作区壳层、暖中性色 Token、Lucide 图标和移动端抽屉；企业设置支持只读成员查看与管理员编辑，团队页提供创建成员、招聘专员/面试官角色说明、角色变更、启用/停用和管理员保护提示。
- 新增企业范围数据库聚合接口 `GET /v1/company/recruitment/analytics/overview` 和 `GET /v1/company/recruitment/analytics/positions/page`，支持明确 `from/to` 日期范围、阶段漏斗、阶段覆盖率、初筛/进入面试/招聘周期、面试转化率、录用率、匹配分分布和服务端岗位分页；未将申请明细全量加载到 JVM 或前端，低于 10 份申请标记低样本，并明确不作 AI 因果结论。
- 新增 `CompanySettingsServiceTest`、`CompanyAnalyticsServiceTest`，覆盖当前企业边界、日期范围、分页和聚合结果；完整 Maven 测试 150/150 通过，0 failures、0 errors。Flyway 测试上下文和 Docker backend 均从 V37 应用 V38 成功，运行数据库为 V38。
- 验证：`npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings；本步骤新增页面未引入 lint warning）；`npm.cmd run build` 通过（保留既有 CodeEditor 大 chunk warning）；Docker backend、frontend、Gateway、Registry、MySQL、Redis、算法 Worker 和监控服务 healthy；未登录 `GET /api/v1/company/settings` 返回 403。
- 真实浏览器验证：使用真实 HR `xingyun_hr` 验证企业设置、团队、招聘分析和岗位效果页面，接口返回真实企业数据，分析聚合 SQL 正常执行，导航深层路由选中状态保持；390px 数据页和 1024px 团队页无横向溢出，浅色/深色主题切换后无控制台 error/warning。未提交设置或成员写入，避免修改演示数据。
- 未验证：本步骤未在浏览器中使用第二企业账号逐项回放组织写入或数据越权；跨企业与权限边界仍由 `CompanyAccessService`、Controller `@PreAuthorize`、服务测试和每条 SQL 的 `company_id` 条件保障。未执行生产发布、真实邮件/短信送达和高并发压测。

### 企业人才库与招聘协作

- 2026-08-12 实查迁移源与运行数据库最高版本为 V36，本次新增下一个可用迁移 V37 `create_company_talent_pool`；未修改 V27–V36。新增 `company_candidate`、`application_note`、`company_candidate_tag` 和 `company_candidate_tag_relation`，所有记录带 `company_id`，同一候选人在同一企业人才库中唯一，移出与标签移除均为软状态保留历史。
- 新增企业范围接口：`GET /v1/company/recruitment/talent-pool`、`GET /v1/company/recruitment/talent-pool/{candidateId}`、成员状态、共享备注、企业标签和最近联系时间接口；列表由数据库分页/筛选，支持关键词、标签、技能、岗位、最近联系时间和排序。历史申请只按当前企业查询，不返回其他企业申请或未授权的额外隐私信息。
- 备注记录作者、创建/更新时间和版本，更新使用条件版本更新并在冲突时返回 409；人才库加入、移出、备注、标签和联系操作统一调用服务端 `OperationAuditService`，不依赖前端 `localStorage`。受限企业面试官只可看到其被授权面试关联的候选人。
- HR 新增 `/company/talent-pool` 与 `/company/talent-pool/:candidateId`，支持桌面列表、移动端候选人卡片、企业历史申请、共享备注、标签和最近联系；申请详情可加入人才库并跳转企业协作档案，筛选状态保存在 URL。
- 新增 `CompanyTalentPoolServiceTest`，覆盖软移出与审计、跨企业候选人、备注版本冲突、跨企业标签和受限面试官数据库范围；完整 Maven 测试 146/146 通过，0 failures、0 errors。Flyway 在本地测试与 Docker backend 均从 V36 应用 V37 成功，运行数据库为 V37。
- 验证：`npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings）；`npm.cmd run build` 通过（保留既有大 chunk warning）；Docker backend、frontend、Gateway、Registry、MySQL、Redis、算法 Worker 和监控服务 healthy；未登录人才库接口返回 403。
- 真实浏览器验证：HR `xingyun_hr` 加入真实申请候选人并打开协作档案，档案显示当前企业两条历史申请；HR `yunqi_hr` 访问企业 A 候选人档案和申请详情均按不存在处理，企业 B 人才库不显示企业 A 候选人。390px 页面无横向溢出，移动导航可打开并用 Escape 关闭，深层路由刷新可恢复，控制台无 error/warn。
- 未验证：未在浏览器中提交共享备注、创建标签或标记联系时间，避免继续写入演示数据；这些写入路径已由服务层测试覆盖。未执行生产环境发布和高并发压测。

### 服务端持久化操作审计

- 2026-08-12 实查迁移源最高版本为 V35，本次新增下一个可用迁移 V36 `operation_audit_log`；不修改既有 V27–V35，表结构保留请求、操作者、企业、模块、动作、资源、结果、脱敏摘要和请求上下文，历史记录无外键级联删除。
- 新增统一 `OperationAuditService` 和管理员接口 `GET /v1/admin/operation-audit-logs`、`GET /v1/admin/operation-audit-logs/export`，服务端执行分页/筛选/CSV 查询；普通 UI 没有清空入口，接口不提供物理删除能力。
- 接入企业成员管理、岗位发布/关闭、申请状态推进、AI/线下面试创建及取消/改期/完成、报告生成/重试/发布、Provider 配置和管理员用户/角色操作。Provider 权限修改仍遵循现有系统角色固定策略，拒绝的变更尝试也写入审计。
- 统一摘要脱敏密码、Token、密钥、Prompt、Provider 原始响应、完整简历、完整回答和内部异常；审计写入由后端服务触发，不接受前端主动上报。前端已移除 `localStorage` 审计作为权威来源，管理日志页改为真实服务端分页并保留 CSV 导出。
- 验证：完整 Maven 测试 141/141 通过；Flyway 在本地 MySQL 和 Docker backend 均确认 schema 为 V36，服务镜像重建后 backend、frontend、Gateway、MySQL、Redis、Registry、算法 Worker 和监控服务 healthy；未登录访问分页与 CSV 接口均返回 403；`npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings），`npm.cmd run build` 通过；`git diff --check` 无差异错误。
- 真实管理员验证：在管理端切换 `asr-provider` 启用/停用后，服务端日志页显示两条 `AI_PROVIDER_UPDATED`/`SUCCESS` 记录，筛选和总数来自服务端，Provider 最终状态已恢复；前端源码已无旧 localStorage 审计引用。
- 未验证：尚未使用两个不同企业账号逐项回放全部业务动作并核对每一条审计事件；CSV 下载的完整文件内容仅完成接口实现与权限路径验证，外部生产环境审计检索、导出容量和高并发写入仍待执行。

### HR AI 面试到评估报告闭环

- 2026-08-12 新增企业申请报告复核闭环：`GET /v1/company/recruitment/applications/{id}/report`、受控重试和独立发布接口；企业成员必须具备 `interview:review` 或 `report:read`，申请、面试和报告均校验当前 `companyId`，跨企业按 404 处理，不能通过通用管理员报告接口绕过范围限制。
- AI 自动评估完成后报告保持企业内部草稿状态，不再自动发布；HR 查看不会发布，只有具备 `interview:review` 的成员才能执行“发布给候选人”。候选人端继续只读取已发布报告，报告未完成/失败显示状态和受控重试，不透出 Prompt、Provider 原始响应、内部异常或原始 JSON。
- 企业报告页新增结构化题目、回答、AI 追问、分项评分和受保护录制时间线；录制媒体仍使用授权接口、Token 不进 URL，并返回 `Cache-Control: no-store` 与 `X-Content-Type-Options: nosniff`。技术任务失败只标记面试/报告任务失败，不把申请直接改为未通过。
- 未新增或修改 Flyway 迁移；实际迁移源与运行数据库最新版本均为 V35，Docker 启动日志确认 Flyway 已验证 29 个迁移且数据库已是最新。
- 验证：完整 Maven 测试 139/139 通过（失败 0、错误 0）；`npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings）；`npm.cmd run build` 通过（保留既有 CodeEditor chunk size warning）；backend、frontend 镜像构建并重启成功，Compose backend、MySQL、Redis、Gateway、Registry、算法 Worker 和监控服务 healthy。
- 真实生命周期验证：候选人 `candidate_sun` 对真实新申请完成预约时间后的进入面试、文字作答（含 AI 追问）、答案保存、面试结束和 AI 报告生成；HR `xingyun_hr` 在申请详情中看到内部未发布报告、结构化回答/追问/评分/事件时间线，再执行发布；候选人发布前看到“报告尚未发布”，发布后在候选人报告路由看到“报告已生成”和完整报告。该申请最终保持“企业评估中”，未因技术处理失败进入“未通过”。运行态另验证未登录 403、企业 B 访问企业 A 报告 404。
- 未验证：内置浏览器的麦克风权限请求未返回设备授权结果，语音模式选择因此卡在浏览器权限等待；本次真实生命周期使用文字模式，所以真实媒体录制分段上传/回放未完成验收。后端录制访问的企业授权、媒体白名单和时间线逻辑已有针对性测试；外部邮件/短信送达、生产环境发布和高并发压测仍待执行。

### HR 面试中心：统一面试活动、日历与企业范围操作

- 2026-08-12 新增企业面试中心路由 `/company/interviews`、`/company/interviews/calendar` 和 `/company/interviews/:id`，统一展示 AI、现场、视频和电话面试；列表支持今日、未来 7 天、已完成、已取消、岗位、候选人关键词、形式和排序筛选，日历使用后端返回的 `serverNow` 作为业务时间基准。
- 新增企业范围接口：`GET /v1/company/recruitment/interviews`、`GET /v1/company/recruitment/interviews/calendar`、`GET /v1/company/recruitment/interviews/{id}`、`PUT /v1/company/recruitment/interviews/{id}/schedule`、`POST /v1/company/recruitment/interviews/{id}/cancel`、`POST /v1/company/recruitment/interviews/{id}/complete` 和 `POST /v1/company/recruitment/interviews/{id}/retry`。查询、详情和操作均先经过当前企业归属与企业角色/权限校验，跨企业资源统一按 404 处理。
- 新增 Flyway V35 `create_company_interview_status_history`，未修改 V27–V34；记录 AI/线下面试创建、开始、结束、改期、取消和完成的状态历史、操作人、原因和通知状态。线下面试改期、取消和完成使用条件更新处理并发冲突，并向候选人写入去重站内通知。
- AI/线下面试创建继续复用企业申请接口和申请状态机；同一申请通过申请行锁、AI 关联检查及线下面试唯一约束禁止重复活动面试。AI 失败任务只向企业范围的授权面试开放重试，失败信息对 HR 脱敏。
- 新增 `CompanyInterviewServiceTest` 与创建锁验证，覆盖企业范围分页参数、跨企业详情 404、取消/改期的状态历史与通知、并发条件更新冲突和创建前申请行锁；完整 Maven 测试通过 134/134，失败 0，错误 0，跳过 0。Flyway 实查 `flyway_schema_history` 最新版本为 V35，`interview_status_history` 已建表。
- 前端 `npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings），`npm.cmd run build` 通过（保留既有 CodeEditor/chunk size warning）；后端/前端 Docker 镜像构建并重启成功，backend actuator readiness、MySQL、Redis、Gateway、Registry、算法 Worker 和监控服务 healthy，前端根路径 200。
- 运行态验证：未登录请求 `/api/v1/company/recruitment/interviews` 返回 403；浏览器使用当前真实 HR 登录态验证列表、日历、详情深链、候选人档案跳转和服务端时间显示，默认桌面视口与 390×844 均无横向溢出，移动端企业导航可用 Escape 关闭，控制台无 error。
- 未验证：未在真实共享种子面试上提交改期/取消，避免改变既有业务数据；未执行两个真实企业账号之间的浏览器切换和高并发压测，跨企业和并发行为已由服务层权限/条件更新测试覆盖；未执行生产环境发布或外部通知渠道送达验收。

### HR 候选人档案：企业画像白名单与真实文件验收

- 2026-08-12 新增企业授权接口 `GET /v1/company/recruitment/applications/{id}/resume/analysis`，仅返回技能、工作经历、项目经历、教育经历、优势、风险、推荐追问方向、解析版本和解析状态；不返回完整解析原文、Prompt、Provider 原始响应、内部异常或存储真实路径。
- 新增企业画像重试入口 `POST /v1/company/recruitment/applications/{id}/resume/analysis/retry`，要求 `application:review`；画像和重试均先通过当前企业申请归属守卫，跨企业统一按 404 边界处理。企业申请摘要中的解析错误不再透传内部错误文本。
- “简历与画像”标签页改用企业白名单接口，按需加载私有原文件：PDF 复用现有 PDF.js worker，支持在线预览、翻页、缩放和打开原文件；DOCX/TXT 仅提供结构化内容与带 Authorization 的受保护下载，不伪装成网页 Word 预览，也不在 HR 页面展开 TXT 原文。
- 文件请求继续复用企业私有简历接口，不把 Token 放入 URL；后端响应保持 `Cache-Control: no-store` 与 `X-Content-Type-Options: nosniff`。解析处于等待/处理中时，仅在标签页可见时轮询，页面隐藏后停止不必要请求；失败仅显示脱敏提示和重试入口。
- 解析提示词补充工作经历、教育、优势、风险和推荐追问方向字段，并保留旧字段兼容映射；企业接口 DTO 与序列化测试覆盖敏感字段排除。停用简历的历史申请返回无可用画像，候选人删除已关联简历仍由后端阻止，历史申请关系不被删除。
- 新增并通过真实 PDFBox PDF、Apache POI DOCX、TXT 提取，错误 PDF/DOCX 签名、配置大小限制、企业跨范围画像和私有文件响应头测试；完整 Maven 测试通过 128/128，Flyway 实查数据库版本 34，未新增或修改迁移。
- 前端 `npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings），`npm.cmd run build` 通过（保留既有 CodeEditor/chunk size warning）。浏览器使用真实 HR 账号验证详情深链刷新、画像标签、脱敏空/等待状态、1440 与 390 宽度、浅色/深色和无横向溢出。
- 未验证：当前本地真实种子申请没有绑定可下载媒体，因此浏览器未能点击到真实 PDF 在线预览或 DOCX/TXT 下载按钮；上述文件解析与响应头已通过实际格式字节和后端接口测试。未执行 Docker/生产部署验证。

### HR 候选人申请详情：标签页、按需数据与申请时间线

- 2026-08-12 完善 `/company/applications/:id` 独立详情页：新增概览、简历与画像、岗位匹配、面试、评估报告和时间线六个标签页；标签状态写入 URL，深层路由刷新后可恢复当前标签。
- 详情首次只加载申请摘要；简历画像、匹配评估与历史、面试摘要、企业范围报告和申请时间线分别按标签按需加载，各标签独立显示 loading、空状态和错误重试，不会一次性请求简历文件、录制媒体或问答大字段。
- 新增企业范围接口：`GET /v1/company/recruitment/applications/{id}/resume/profile`、`GET /v1/company/recruitment/applications/{id}/interview`、`GET /v1/company/recruitment/applications/{id}/timeline`；简历画像仅返回结构化白名单，不返回简历解析原文、Prompt 或 Provider 信息。
- 时间线统一展示投递、匹配任务、阶段变化、AI 面试创建/开始/结束、报告生成/发布、线下面试邀请和 HR 操作；所有查询先经过当前企业申请归属校验，跨企业访问按既有 404 边界处理。
- 评估报告企业响应补充现有 `report.generated_at` 生成时间，继续排除 Prompt、Provider 密钥、内部异常堆栈和其他内部元数据；未新增 Flyway 迁移，实际数据库和迁移源最新版本仍为 V34。
- 返回流程中心时保留来源列表的 `view`、页码、筛选和排序查询参数；长姓名/岗位、缺联系方式、无简历画像、无面试和无报告均有明确的展示状态。继续复用 `RecruitmentMatchEvaluation`，不展示原始 JSON。
- 新增企业画像和时间线权限测试，覆盖结构化画像白名单、时间线事件组合及跨企业访问阻断；完整 Maven 测试通过。
- 浏览器验证：真实本地 HR 账号验证详情刷新、六个标签、按需加载、匹配历史、无面试/无报告状态、时间线、筛选条件返回恢复；1440×900 与 390×844 无横向溢出，控制台无 error/warn。
- 前端 `npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings），`npm.cmd run build` 通过（保留既有 CodeEditor/chunk size warning）。未验证 Docker 健康状态和生产部署环境。

### HR 候选人流程中心：表格、阶段看板与独立详情

- 2026-08-12 重构 `/company/applications`：新增表格视图和阶段看板视图，统一展示候选人、岗位、当前阶段、匹配度、面试状态、最近活动和下一步；看板不使用拖拽，阶段推进只通过明确操作按钮完成。
- 企业申请查询扩展为服务端分页、岗位/阶段/匹配度区间/投递时间/面试状态筛选，并支持最新投递、匹配度和最长未处理时间排序；查询继续通过当前企业范围守卫执行，列表和每个看板列均使用后端分页。
- 新增 URL 查询参数状态：`view`、`pageNo`、`keyword`、`positionId`、`status`、`minMatchScore`、`maxMatchScore`、`from`、`to`、`interviewStatus`、`sort`；刷新时保留筛选条件。加载失败以内联提示呈现，不覆盖已有列表。
- 新增 `/company/applications/:id` 独立候选人详情路由，移除原申请大弹窗；保留 AI 匹配历史、匹配重试、AI 面试安排和线下面试邀请。详情操作由后端 `allowedTransitions` 驱动，拒绝/录用等终态操作增加二次确认和原因校验。
- `ApplicationView` 增加面试状态、最近活动和下一步字段；移动端使用候选人卡片，不强制横向宽表格。沿用现有语义 Token、Inter、Source Han Serif CN 和 Lucide，不引入新的视觉库或颜色系统。
- 新增 `RecruitmentApplicationQueryTest`，覆盖企业范围查询、服务端分页、筛选/排序路径和非法面试状态筛选；未新增 Flyway 迁移，实查数据库和迁移源最新版本仍为 V34。
- 验证：`mvn.cmd -q test` 通过；`mvn.cmd -pl backend -q '-Dtest=RecruitmentApplicationQueryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` 通过；`npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings）；`npm.cmd run build` 通过（保留既有 chunk size warning）。
- 浏览器验证：使用真实本地 HR 账号 `xingyun_hr` 验证真实申请数据、匹配度 80 分以上筛选、AI 待开始筛选、阶段看板、URL 状态、刷新保留条件、独立详情和终态确认入口；1440/1024/768/390 宽度无横向溢出，移动端展示候选人卡片，控制台无 error/warn。
- 未验证：Docker 服务健康检查和生产环境账号/部署验证未在本轮执行；本地浏览器验证未提交任何终态变更。

### HR 岗位管理：服务端分页、状态动作与独立页面

- 2026-08-11 重构企业岗位管理：新增 `/company/positions`、`/company/positions/new`、`/company/positions/:id` 和 `/company/positions/:id/edit`，拆分列表、详情、创建和编辑页面；列表支持服务端分页，以及关键词、状态、城市和部门筛选。
- 新增企业岗位详情、统计、复制和状态动作接口：`GET /v1/company/recruitment/positions/{id}`、`GET /v1/company/recruitment/positions/{id}/statistics`、`POST /v1/company/recruitment/positions/{id}/clone`、`PUT /v1/company/recruitment/positions/{id}/status`。查询和操作均通过 `CompanyAccessService` 约束当前 `companyId`，跨企业资源统一按 404 处理。
- 岗位创建固定为草稿，编辑不能直接改状态；发布、关闭和重新发布使用显式后端动作，并执行发布前完整性校验。关闭岗位保留历史申请，但候选人端不再展示或接受新投递。
- 新增数据库聚合统计：申请数、平均匹配度、面试人数和录用人数；新增结构化技能标签输入、岗位复制、候选人端预览和未保存离开提醒。岗位操作统一调用服务端 `RecruitmentAuditService`，未继续使用岗位操作的 `localStorage`；持久化审计表仍待后续阶段接入。
- 未新增 Flyway 迁移；本轮实查迁移源和测试启动时数据库均为 V34，已存在迁移未修改。
- 验证：`mvn.cmd -q -Dtest=RecruitmentPositionServiceTest test` 和完整 `mvn.cmd -q test` 通过；Flyway 校验 28 个迁移并确认 V34 已是最新。`npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings），`npm.cmd run build` 通过（保留既有 chunk size warning）。
- 浏览器验证：使用 `xingyun_hr`、`candidate_liu` 和 `yunqi_hr` 完成真实本地草稿→发布→候选人查看→投递→关闭路径；关闭后历史申请仍保留且候选人端不再看到岗位；跨企业详情访问返回 404。已检查 1440/1024/768/390 宽度、移动导航打开与 Escape 关闭、控制台错误/警告和横向溢出。
- 未验证：Docker Desktop 当前无法连接 `dockerDesktopLinuxEngine` named pipe，因此 Docker 服务健康验证未完成；本地浏览器验证产生的测试岗位 `QA-UI-20260811-01` 已关闭并保留一条历史申请，未执行删除。

### HR 工作台：行动中心与企业聚合分析

- 2026-08-11 新增企业范围聚合接口：`GET /v1/company/recruitment/dashboard/summary`、`/dashboard/actions`、`/dashboard/upcoming-interviews`、`/analytics/funnel`、`/analytics/positions`；所有查询均由后端从当前登录用户解析 `companyId`，SQL 聚合后返回，不向前端加载全部申请再统计。
- `/company` 重做为“今天需要处理什么”的 HR 工作台：保留企业名称、招聘概览、主要操作和四项核心指标，新增新申请、匹配失败、AI 面试完成待评估、报告超时、线下面试待确认五类行动中心，以及今日/近期面试、招聘漏斗、岗位效果排行、最后更新时间和手动刷新。
- 页面使用现有暖中性色语义 Token、Inter 正文、Source Han Serif CN 标题和 Lucide 图标；补齐初始 loading、空数据、分区失败和完整失败状态，不改变现有企业岗位/申请业务接口。共享工作区顶部导航改为不透出旧紫粉背景的语义背景。
- 新增 `CompanyDashboardServiceTest`，覆盖企业范围摘要映射、五类行动分组、漏斗阶段/比例和岗位排行边界；未新增 Flyway 迁移，当前实际数据库和迁移源最高版本为 V34。
- 验证：针对性测试 4/4、完整 Maven 测试 106/106 通过；`npm.cmd run lint` 通过（0 errors，22 条既有 Hook dependency warnings），`npm.cmd run build` 通过；Docker backend 镜像构建、Flyway V34 校验和 backend healthy 通过，MySQL、Redis、Gateway、Registry、Frontend、监控和 2 个算法 Worker 当前运行正常；未登录访问聚合接口返回 403。
- 浏览器验证：使用真实本地 HR 账号 `xingyun_hr` 登录，五个聚合区均读取真实数据；1440/1024/768/390 宽度无横向溢出，浅色/深色主题、手动刷新、移动导航打开与 Escape 关闭通过，控制台无 error/warn。
- 未验证：Docker Hub 当前无法拉取 `node:22.13-alpine`，因此本轮未完成新的 frontend 镜像构建；已将通过 `npm run build` 生成的 `dist` 临时覆盖当前前端容器静态目录完成真实页面验收。生产远程发布、生产账号覆盖和高并发聚合压测仍待执行。

### 共享工作区壳层

- 2026-08-11 完整读取 `.interface-design/system.md`，按现有暖中性色语义 Token、Inter 正文和 Source Han Serif CN 标题规范，抽取共享 `WorkspaceShell`、`WorkspaceGlobalNav`、`WorkspaceContextSidebar`、`WorkspaceMobileDrawer`、`WorkspaceUserMenu` 与 `WorkspacePageHeader`。
- 新增 `frontend-react/src/components/workspace-navigation.ts` 作为统一路由元数据配置；候选人、企业和超级管理员的当前路由均由同一套匹配逻辑决定顶部业务域和侧栏选中状态，支持深层路由刷新后恢复选中项，并保留旧 HR/管理员 URL。
- 企业端顶部业务域接入“工作台 / 招聘 / 面试 / 数据 / 组织”，管理员端接入“平台 / 租户 / 业务 / 内容 / AI / 运维”；当前尚无对应业务页面或接口的企业域显示为不可用状态，不伪造新功能，也未改变现有业务接口。
- `CandidatePageShell`、`CompanyPageShell`、`AdminPageShell` 保留为兼容包装层；企业和管理员页面移除紫粉渐变品牌装饰、嵌套 shell 隐藏选择器和重复外层页面内边距，统一内容最大宽度与垂直节奏；候选人业务页面未大规模重写。
- 移动端使用 Radix Dialog 抽屉，保留通知、主题切换、用户信息和退出登录；所有图标继续使用 Lucide，未引入新的视觉库或第二套颜色系统。
- 验证：`npm.cmd run lint` 通过，0 errors、22 条既有 Hook dependency warnings；`npm.cmd run build` 通过；Docker frontend 镜像构建、重建成功，容器内 `nginx -t` 通过，backend healthy，2 个算法判题 Worker 已恢复为 healthy。
- 浏览器验证：使用本地候选人 `candidate_liu`、企业账号 `xingyun_hr` 和超级管理员 `admin_zhang` 完成真实登录；检查 1440/1024/768/390 宽度、浅色/深色、企业申请页和管理员深层面试回顾页刷新选中状态；768/390 移动抽屉可打开并用 Escape 关闭，无横向溢出，已检查页面控制台无错误。
- 未验证：生产服务器远程发布、真实生产账号覆盖、未接入企业业务域的后续页面设计和高并发浏览器压测。

### 企业范围访问控制第一步

- 2026-08-11 新增 `CompanyAccessService`，统一校验企业角色、当前用户 `companyId`、岗位归属、申请归属，以及申请关联的候选人/岗位/面试和报告关系；企业资源跨范围访问统一返回 404，不暴露资源存在性。
- `RecruitmentService`、`CandidateResumeService`、`InterviewService` 的企业操作改用统一守卫；企业简历文件读取继续校验申请、候选人与私有媒体所有者关系，不依赖前端隐藏入口。
- 新增企业报告接口 `GET /v1/company/recruitment/applications/{applicationId}/report`。返回独立的企业报告白名单 DTO，并强制校验申请企业、申请面试与报告面试一致；企业管理员访问通用面试报告接口统一返回 404，不能借此读取报告。
- 企业报告响应不包含 Prompt 原文、Provider 密钥、内部异常堆栈或简历解析原文；候选人通用报告接口仍只允许候选人读取已发布报告，超级管理员通用报告读取能力保持不变。
- 未新增 Flyway 迁移，当前实际数据库与迁移源均为 V33，后续迁移仍从 V34 开始；未修改前端、OpenTalking、avatar-skill 或讯飞链路。
- 新增并通过企业 A 自有报告、企业 B 跨企业 404、候选人企业范围拒绝、超级管理员企业范围拒绝、面试关联一致性、企业报告字段白名单、企业管理员通用报告拒绝和候选人未发布报告拒绝测试。
- 验证：针对性 Maven 测试 18/18 通过；完整 `mvn -B -ntp -pl backend -am test` 通过 89/89；Spring 上下文验证 MySQL 与 V33；backend 镜像重建并重启后 healthy，Compose 中 MySQL、Redis、backend、cloud-registry、cloud-gateway、前端、监控和 2 个判题 Worker 均健康。
- 运行态验证：未登录访问企业报告返回 403；企业 A 读取自身报告返回 200；企业 B 读取企业 A 报告返回 404；企业 A 访问通用面试报告接口返回 404、通用报告目录返回 403；企业报告 JSON 未暴露 Prompt code、Provider key 或原始简历字段。未验证生产服务器远程发布、真实 Provider 新报告生成和高并发压测。

### 企业团队角色与权限基础

- 2026-08-11 在确认迁移目录和 Spring Boot 实际数据库均为 V33 后新增 Flyway V34 `add_company_team_roles_and_permissions`；V27–V33 未修改。新增 `COMPANY_ADMIN`、`COMPANY_RECRUITER`、`COMPANY_INTERVIEWER` 及企业权限映射，保留已有 `COMPANY_ADMIN` 账号兼容逻辑。
- 企业管理员默认拥有全部企业权限；企业招聘者可管理岗位、申请、面试和报告但不能管理成员；企业面试官只读取被授权的候选人/面试并提交面试评价。`CompanyRecruitmentController` 与 `RecruitmentService` 逐步改为权限判断，超级管理员仍通过原有独立管理员接口跨企业管理。
- 新增企业团队接口：`GET /v1/company/team`、`POST /v1/company/team`、`PUT /v1/company/team/{userId}/roles`、`PUT /v1/company/team/{userId}/status`。企业成员创建时由服务端绑定当前 `companyId`，不接受调用方指定其他企业；跨企业成员操作统一返回 404，停用、降级或移除角色前保护企业最后一个可用 `COMPANY_ADMIN`。
- 新增权限加载、企业范围守卫、团队 DTO/服务/控制器及权限组合、跨企业成员、最后管理员保护测试；候选人既有已发布报告读取边界和超级管理员管理员接口保持不变。未修改前端、OpenTalking、avatar-skill 或讯飞链路。
- 验证：针对性 Maven 测试 20/20 通过；完整 `mvn -B -ntp -pl backend -am test` 通过 102/102。Docker backend 镜像构建成功并重启为 `healthy`；启动日志确认 Flyway 校验 28 个迁移，从 V33 应用 V34 并成功启动；未登录访问容器内 `/api/v1/company/team` 返回 403。
- 未验证：生产服务器远程发布、真实企业账号通过网关完成团队成员全流程操作、跨节点高并发角色变更压测和外部 Provider/邮件送达。V34 应用时 MySQL 输出了既有数据重复插入告警及 `VALUES()` 语法弃用告警，但迁移成功完成，后续迁移不得修改已发布的 V34。

### 招聘申请状态机统一约束

- 2026-08-11 新增 `ApplicationStatus` 与 `ApplicationStatusService`，将 `job_application.status` 的初始化、推进、版本更新和历史写入收敛到统一后端边界；审计确认招聘生产代码中不再存在绕过该服务的申请状态写入。
- 统一支持 `SUBMITTED → AI_INTERVIEW_PENDING/UNDER_REVIEW`、`AI_INTERVIEW_PENDING → AI_INTERVIEWING`、`AI_INTERVIEWING → UNDER_REVIEW`、`UNDER_REVIEW → OFFLINE_INTERVIEW/REJECTED/HIRED`、`OFFLINE_INTERVIEW → HIRED/REJECTED`，并保留必要的拒绝和重新安排 AI 面试路径；`REJECTED`、`HIRED` 为终态，不提供无权限、无原因的恢复入口。
- 所有状态变化在同一事务中按当前状态、`version` 和企业归属条件更新，并写入 `job_application_status_history`；条件更新冲突统一返回明确的 409 业务错误。转为企业评估、未通过或已录用时，`StatusUpdateRequest` 与服务层均要求审核备注或变更原因。
- AI 面试创建、面试开始、面试结束和线下面试邀请均通过状态机推进；`ApplicationView` 新增 `allowedTransitions`，企业申请页仅使用后端返回的允许动作同步状态选项，未进行界面重构。
- 新增正常转换、非法转换、终态、原因校验、乐观锁冲突、面试生命周期和跨企业边界测试；保留候选人只能查看已发布报告的既有权限逻辑。
- 未新增 Flyway 迁移，当前实际数据库与迁移源仍为 V33，后续迁移从 V34 开始；未修改 OpenTalking、avatar-skill 或讯飞链路。
- 验证：状态机及生命周期针对性测试 25/25 通过；完整 `mvn -B -ntp -pl backend -am test` 通过 97/97；前端 `npm.cmd run build` 通过，TypeScript 与 Vite 构建成功。前端构建仍提示既有大 chunk warning，不影响构建结果；`git diff --check` 无空白错误（仅有 Windows 换行提示）。
- 未验证：本轮未重建或重启 Docker backend 镜像，未执行双账号真实运行态状态推进和并发压测；生产服务器远程发布、真实 Provider 结果和邮件/外部 Provider 送达仍待验证。

### 项目结构文档同步

- 2026-08-11 按用户明确要求，以当前工作区真实 Maven 模块、后端包、前端路由、Flyway 目录和 Docker Compose 配置为准，集中更新 `docs/project-structure.md`；未新建结构或总结文档。
- 将架构基线同步为 Spring Boot 4.0.7 模块化单体业务核心、Spring Cloud 2025.1.2 Eureka/Gateway 和独立算法判题 Worker，明确 backend 不再挂载 Docker Socket、Worker 为唯一 Docker Socket 使用方，以及同步 RUN/异步 SUBMIT 的调用边界。
- 补充招聘域、候选人/企业/超级管理员页面分区、V27–V33 招聘迁移、默认 Compose 服务、`judge`/`monitoring` profile、Prometheus/Alertmanager/Grafana、OpenTalking 独立代理和私有媒体边界；当前最新迁移为 V33，后续迁移在确认目录无更高版本后从 V34 开始。
- 本次仅修改项目结构说明与本更新日志，不修改业务代码、配置、数据库、部署或 OpenTalking 链路。
- 文档校验已完成：关键目录均存在，Flyway 目录按数字版本确认共有 26 个脚本且最高为 V33，默认 Compose 配置解析出 7 个服务，启用 `judge` 与 `monitoring` profile 后服务清单包含 Java Runner 和三项监控服务；两份文档的 `git diff --check` 通过。

### 招聘主链路 P1：匹配历史快照与双端评估面板

- 2026-08-11 新增 Flyway V32，补齐 `job_match_evaluation` 的摘要、规则命中技能、AI 命中技能、风险和建议快照字段；V31 及以前迁移未修改。
- 2026-08-11 新增 Flyway V33，将已有完成态的 `job_application.match_score` 与 `match_details` 回填为评估版本 `v0` 的只读历史快照；新一轮重算从版本 `v1` 开始，避免与历史数据冲突。
- 匹配评估 DTO 统一返回最终分、规则分、AI 证据分、命中技能、优势、缺口、风险、证据、置信度、Provider/模型/Prompt 元数据和建议；历史记录按数据库分页并按评估版本倒序返回。
- 新增候选人 `GET /v1/recruitment/applications/{id}/match/history` 与企业 `GET /v1/company/recruitment/applications/{id}/match/history`，分别强制校验候选人归属和当前企业归属，避免跨申请读取评估历史。
- 新增共享匹配评估面板，候选人端和企业端申请详情均展示最终匹配度、规则/AI 分项、结构化证据、风险和建议，并支持展开历史版本；旧数据或评估接口暂不可用时保留兼容展示，不阻断申请详情。
- 申请详情首次加载与轮询并行请求申请、当前评估和历史评估，单个评估接口失败不会覆盖申请主链路；未启用 avatar-skill、讯飞或其他虚拟人链路。
- 前端 Nginx 将入口 `index.html` 设置为不缓存，将带 hash 的静态 assets 设置为长期 immutable 缓存；保留 PDF.js `.mjs` 的 `application/javascript` MIME，避免发布后旧入口引用已删除 chunk 导致白屏。

### 招聘主链路 P1 匹配历史验证

- 完整 `mvn -B -ntp -pl backend -am test` 通过 80/80；Spring Boot 上下文验证 27 个迁移、MySQL 连接和 V33 已应用且可重复启动。
- 前端 `npm.cmd run lint` 通过，0 errors、22 个既有 Hook dependency warnings；`npm.cmd run build` 通过，生成候选人/企业申请页与共享评估面板构建产物。
- `docker compose -p ainterview build backend frontend` 已通过；backend 重启后 healthy，算法判题 Worker 已恢复为 2 个 healthy 实例；运行态 Nginx bundle 已包含新版评估面板 chunk，未删除数据卷。
- 使用本地候选人与企业演示账号完成真实登录路径验证：两端申请详情均能读取 V33 历史快照并显示“历史评估（1 轮）”，展开后显示版本、状态和最终分；企业端只看到当前企业申请。
- `docker compose -p ainterview config --quiet` 与 `git diff --check` 通过；未提交、未推送、未执行 reset/checkout/清理或 OpenTalking 配置变更。
- 前端容器内 `nginx -t` 通过；`index.html` 返回 no-store、hash assets 返回 immutable、PDF.js `.mjs` 返回 `application/javascript`，更新后 2 个判题 Worker 均 healthy。
- 未验证：真实 DeepSeek 新一轮匹配返回后的规则/AI 评分效果、生产服务器远程发布、真实高并发重算压测和邮件/外部 Provider 送达。

### 招聘主链路 P1：异步岗位匹配评估升级

- 2026-08-11 新增 Flyway V31 `job_match_evaluation`，保存岗位快照、简历画像快照、规则分、AI 证据分、最终分、优势、缺口、证据、置信度、Provider、模型和 Prompt 版本；V30 及以前迁移未修改。
- 岗位匹配最终分改为后端固定计算：确定性规则分 60% + AI 证据分 40%，AI 返回的 `score` 不再直接作为录用排序最终分；匹配详情同时保留 `ruleScore`、`aiScore`、`score` 和规则命中的技能。
- 申请增加匹配评估版本，AI 任务去重键改为“申请 + 评估版本”；候选人/企业重新匹配通过申请乐观锁生成新版本，避免复用旧成功任务后状态停留在 `PENDING`。
- Worker 回写 `PROCESSING`、`SUCCESS`、`FAILED` 时增加申请版本与评估版本条件，旧 Worker 只能丢弃过期结果，不能覆盖新一轮匹配；岗位匹配失败信息改为按配置错误、上游繁忙和通用失败分类脱敏，不持久化 Provider 响应体。
- 新增候选人与企业匹配详情接口：`GET /v1/recruitment/applications/{id}/match`、`GET /v1/company/recruitment/applications/{id}/match`；接口按候选人归属或当前企业强制授权，不返回岗位/简历内部快照。
- 简历解析完成后的自动匹配回写增加申请版本条件更新，匹配任务只使用对应简历版本和评估版本；AI Provider 元数据从生成记录带入评估历史，未记录密钥或简历正文。
- 未修改 OpenTalking、avatar-skill 或讯飞链路，未提交或推送代码。

### 招聘主链路 P1 匹配评估验证

- 针对性岗位匹配、生命周期、简历权限和报告分页测试通过 10/10；完整 `mvn -B -ntp -pl backend -am test` 通过 80/80。
- Spring Boot 上下文实际校验并执行 V31，Flyway 从 V30 升级到 V31；backend 镜像构建成功并重启为 healthy，算法判题 Worker 恢复为 2 个 healthy 实例，未删除数据卷。
- `docker compose -p ainterview config --quiet` 与 `git diff --check` 通过；运行态经 Nginx 验证根路径 200、API 文档 200、新增候选人/企业匹配接口存在，未登录访问均返回 403；backend 重启后的 Eureka 服务发现短暂刷新期间曾返回 503，等待注册状态恢复后复验通过。
- 未验证：真实企业/候选人 Token 的匹配历史读取、DeepSeek 实际返回后的生产评分效果、生产服务器远程发布和高并发重算压测；前端 lint/build 本轮未重复执行。

### 招聘主链路后端第二轮：简历授权与报告查询优化

- 2026-08-11 补齐招聘简历中心的后端访问边界：新增候选人结构化画像接口 `GET /v1/recruitment/resumes/{id}/analysis`，只返回候选人画像、技能、项目、追问方向和风险提示等白名单字段，不返回解析正文、Prompt 或技术错误详情。
- 新增企业专用简历原文件接口 `GET /v1/company/recruitment/applications/{id}/resume/content`；服务端强制校验企业管理员角色、当前登录企业、申请归属、候选人与简历关系以及私有媒体所有者，禁止通过通用媒体接口绕过申请授权。
- 简历文本提取增加 10MB 输入上限，避免从私有存储使用无界 `readAllBytes()` 读取；AI 解析失败向前端返回脱敏提示，技术细节仍只保存在后端分析记录中。
- 管理员报告列表改为数据库分页，并仅批量加载当前页关联的面试和候选人；关键词先按面试标题、候选人姓名/用户名筛选关联面试，候选人能力摘要只查询本人面试关联的已发布报告，避免全表加载和 JVM 内存分页。
- 本轮未新增 Flyway 迁移，当前数据库仍为 V30；未修改 OpenTalking、avatar-skill 或讯飞链路，未提交或推送代码。

### 招聘主链路后端第二轮验证

- 针对性单测通过 9/9；完整 `mvn -B -ntp -pl backend -am test` 通过 80/80。Spring Boot 上下文验证 Flyway 24 条迁移、当前 schema V30、MySQL 连接和新增控制器装配。
- `git diff --check` 通过；`docker compose -p ainterview config --quiet` 通过；backend 镜像重建成功并重启为 healthy，算法判题 Worker 恢复为原有 2 个 healthy 实例，未删除数据卷。
- 运行态经前端 Nginx 代理验证：根路径 200、`/api/v3/api-docs` 200，OpenAPI 已包含候选人画像和企业简历接口；未登录访问企业简历接口返回 403。
- 未验证：携带企业管理员真实 Token 读取某条真实申请的文件、生产服务器远程部署、真实 PDF/DOCX + AI Provider 解析、ClamAV 扫描和邮件送达。

### 候选人 Candidate Workspace Shell 第一阶段

- 2026-08-11 完成候选人端导航审计并将 `CandidatePageShell` 调整为 GlobalNavbar + ContextualSidebar：顶部按工作台、求职、面试、成长分组，页面侧栏只展示当前模块相关入口，问题反馈与账户设置收纳到底部。
- `/workspace` 作为工作台总览隐藏上下文侧栏并最大化内容区；其余候选人业务页面保留现有路由、API、权限、通知、主题切换、退出登录和页面过渡。
- 延续现有 warm neutral 色彩、Inter / Source Han Serif CN 字体、Lucide 图标与按钮焦点样式；移除候选人品牌入口的渐变/发光装饰，未新增全局调色板、玻璃拟态或业务功能。
- 本轮仅完成桌面壳层与导航结构，移动端继续使用现有抽屉逻辑，统一移动适配留待后续阶段；未修改 OpenTalking、WebRTC、面试房间或后端接口。
- Impeccable 布局/通用检测通过；TypeScript、Lint、Build 通过，Lint 保留工作区既有 22 条 Hook 依赖 warning；浏览器完成 `/workspace` 与 `/jobs` 桌面壳层、上下文选中态和工作台侧栏隐藏检查。

### 候选人反馈与账户入口模块上下文保持

- 2026-08-11 从任意候选人模块点击“问题反馈”或“账户设置”时，将来源模块写入 `context` 查询参数；返回页面后顶部模块选中态和上下文侧栏保持不变，刷新页面也不会退回工作台。
- 仅调整候选人端 `CandidatePageShell` 的导航状态，不修改反馈/账户业务页面、路由表、API、权限或移动端统一适配范围。

### Features 顶部 CTA 收敛

- 2026-08-11 移除 `/features` 顶部导航中与“开始体验”功能重复的“登录”入口，保留 Hero 区和顶部唯一的“开始体验”登录入口；页脚登录链接保持不变。
- 未修改登录路由、认证逻辑或其他页面入口。

### 工作台侧栏保留

- 2026-08-11 恢复 `/workspace` 工作台桌面侧栏显示，保留“总览”、问题反馈、账户设置和候选人信息入口；主内容区同步保留侧栏左侧留白。
- 移动端继续沿用现有可关闭导航抽屉，未修改工作台业务内容、路由、API 或权限逻辑。

### 候选人顶部无效搜索入口移除

- 2026-08-11 移除候选人端顶部仅展示“全局搜索即将开放”且处于禁用状态的搜索按钮，避免保留无实际功能的入口。
- 保留岗位大厅页面内可用的岗位搜索与筛选，不修改其他顶部操作和业务功能。

### 网站默认首页

- 2026-08-11 将 React 根路径 `/` 直接渲染为公开 Features 首页，避免进入网站时继续显示登录页；`/features` 与 `/login` 路由保持独立可访问。
- 未修改登录路由、认证逻辑或登录后的角色工作台跳转。
- 已重建并重启 `ainterview-frontend` 容器，未重启后端、数据库、Redis 或其他服务；运行态验证 `/` 与 `/features` 均返回 HTTP 200。

### 成长侧栏算法入口收敛

- 2026-08-11 移除成长侧栏独立的“算法可视化”菜单；保留 `/algorithm/visualizer` 路由及算法练习页面中的“算法可视化”入口，让该能力归属于算法练习上下文。
- 未修改算法可视化页面、路由表、接口或业务功能。

### 候选人工作台招聘准备模块质量收尾

- 2026-08-11 确认 `candidate-workspace.tsx` 中招聘准备卡片已实际接入申请与简历接口，展示可投递简历数量、岗位申请数量、最近申请状态及简历/申请入口；未修改后端、数据库或权限边界。
- `frontend-react` 的 `npm.cmd run lint` 通过，0 个错误，保留 24 条既有 Hook 依赖警告；`npm.cmd run build` 通过。浏览器完成 `/workspace` 桌面与 390px 移动端检查，移动端页面宽度与滚动宽度均为 380px，无横向溢出。
- 本次仅完成前端质量验证与日志更新，未执行 Git 暂存、提交或远程推送。

### 候选人面试评测报告第一轮 UI/UX 优化

- 2026-08-11 优化 `/candidate/interviews/:id/report` 的加载骨架、报告未生成/未发布/读取失败状态和恢复动作；仅在明确“尚未生成/生成中”时自动轮询，避免权限或其他错误被误报为生成中。
- 优化共享报告详情视图的按钮语义、装饰图标无障碍标记、异步提升计划状态、可信度提示和评分数字排版；保留现有报告、PDF 导出、心得、能力趋势和训练计划 API。
- 通过 Impeccable critique/layout/polish/audit 检查链、React 性能检查与 Web Interface Guidelines 检查；未调整菜单结构、全局导航、报告权限或后端接口。
- TypeScript、Lint、Build 通过；Lint 仍有工作区既有 22 条 Hook 依赖 warning。后端 8080 未启动，浏览器本轮覆盖失败/降级状态及 390px 无横向溢出，未伪造真实报告数据。

### AI 招聘面试平台第四阶段：招聘申请关联 AI 面试

- 2026-08-11 新增企业端公开题库查询和 AI 面试创建接口：`GET /v1/company/recruitment/interview-question-banks`、`POST /v1/company/recruitment/applications/{id}/ai-interview`；企业管理员只能为本企业处于 `SUBMITTED` 或 `UNDER_REVIEW` 的申请创建面试，并且只能选择已发布的公开题库。
- 创建 AI 面试后自动写入申请 `interviewId`，申请状态推进为 `AI_INTERVIEW_PENDING`，记录状态历史并向候选人发送站内通知；面试开始/结束事件自动同步为 `AI_INTERVIEWING` / `UNDER_REVIEW`，结束后通知候选人查看后续评测。
- 企业端申请详情新增“安排 AI 面试”弹窗和 AI 面试预约卡片，支持预约时间、时长、面试类型、题目数量、公开题库、面试官风格和补充说明；候选人端申请详情新增预约信息、“进入 AI 面试”和已完成后的报告入口。
- 修复 Spring Boot 4 默认 Jackson 3 环境下 Snowflake `Long` ID 被序列化为 JSON 数字的问题，注册 Jackson 3 `Long -> String` 序列化模块，避免浏览器安全整数舍入导致申请详情误报 404；Jackson 2 兼容配置保留以兼容现有代码。
- 本阶段未新增 Flyway 迁移，数据库仍为 V30，后续迁移继续从 V31 开始；未修改 OpenTalking、avatar-skill 或讯飞链路，未执行 Git 暂存、提交或远程推送。

### AI 招聘面试平台第四阶段验证

- `mvn -B -ntp -pl backend -am test` 通过：backend 73/73；新增招聘面试生命周期监听器单测覆盖开始、结束、状态历史和候选人通知；Spring Boot 上下文确认 Flyway 校验 24 条迁移且 schema 当前为 V30。
- `frontend-react` 的 `npm.cmd run build` 通过，Docker 内前端生产构建也通过；前端 lint 当前仍有工作区原有的 `candidate-workspace.tsx:167` 未使用组件错误及 24 条 Hook 依赖警告，本阶段未删除或重写该用户已有改动。
- `docker compose -p ainterview build frontend` 与此前 backend 镜像构建均通过；`docker compose -p ainterview config --quiet` 通过，Compose 中 MySQL、Redis、backend、frontend、cloud-registry、cloud-gateway 和算法判题 Worker 均健康运行。实际 Nginx bundle 包含 AI 面试 UI，PDF.js `.mjs` 返回 `application/javascript`，`/api/v3/api-docs` 返回 200。
- 使用企业管理员与候选人演示账号完成真实闭环：企业公开题库接口返回 3 个题库，真实申请成功创建 AI 面试并落库 3 道题；企业和候选人详情接口返回同一 `interviewId`、预约时间和状态，原始 API 中 Snowflake ID 为字符串；企业端和候选人端浏览器详情均可打开，390px 移动视口无横向溢出。
- 尚未验证或交付：到达预约时间后的真实开始/结束事件、完整作答录制与 AI 报告生成、邮件或短信实际送达、生产服务器远程部署；本地 Compose 中新增的演示 AI 面试申请数据已保留，未删除数据卷。

### AInterview Features 产品能力页

- 2026-08-11 将站点根路径 `/` 调整为公开 Features 首屏；用户点击 Features 页中的“登录”或“开始体验”后仍进入 `/login`，登录成功后的角色工作台跳转保持不变。
- 2026-08-11 新增公开 `/features` 产品叙事页：保留现有色彩令牌、Inter 与 Source Han Serif CN 字体、React Router、Button/Badge/Lucide 组件和登录入口，不修改后端 API、权限或招聘业务页面。
- 以本地可交互 HTML/CSS/React 产品窗口呈现岗位匹配、简历解析、动态 AI 面试、评测报告、企业候选人审核抽屉、线下面试邀请通知和完整招聘流程；演示数据集中在 `frontend-react/src/features/mock-data.ts`，不调用生产接口。
- 增加 1440/1024/768/390px 响应式布局、`prefers-reduced-motion` 降级、语义导航、可见焦点状态、实时动态状态和无横向溢出约束；设计审计与令牌边界记录于 `.interface-design/system.md`。
- 2026-08-11 收尾补充页面级 `scroll-margin-top`，确保桌面与移动端锚点跳转不会被 sticky header 遮挡；未改变接口、业务状态或认证边界。
- 2026-08-11 完成 Features 第二轮保留式审查：将 ProductWindow、岗位匹配、简历分析、AI 面试、报告、企业审核、邀请通知、招聘流程和追问时间线中的用户可见英文统一为中文；补齐章节标题与 `aria-labelledby` 的关联；保留技术名词、品牌名、颜色、字体、路由和业务逻辑。审查记录见 `.interface-design/features-second-pass-audit.md`。
- 2026-08-11 将本轮 Features 文案与无障碍修复构建为 `ainterview-frontend:latest` 镜像，并仅重建/重启 `frontend` 服务；容器内生产 bundle 已包含“产品预览”和“岗位描述”，宿主机 `/features` 返回 HTTP 200；未重启后端、数据库、Redis 或其他 Compose 服务。
- 未修改 OpenTalking、avatar-skill 或讯飞链路；未执行 Git 暂存、提交或远程推送。

### 候选人岗位大厅第一轮 UI/UX 优化

- 2026-08-11 基于现有 AInterview 色彩、字体、路由、接口、权限和投递流程，完成 `/jobs` 首轮保留式改造：补齐城市、经验、学历、岗位类型和最低薪资筛选，筛选条件与页码同步到 URL，并加入分页。
- 优化岗位卡片的信息层级，优先呈现岗位、公司、薪资和关键元数据；补充真实 logo 尺寸、岗位状态、技能截断与详情弹窗，继续复用现有 Radix、Tailwind、Lucide、Button、Badge、Card 和 ResponsiveSelect。
- 增加骨架屏、空结果、岗位加载失败重试、简历接口独立降级、投递状态反馈和可用简历限制；未改变后端 API、数据权限、岗位路由或 OpenTalking/WebRTC 链路。
- 新增 `PRODUCT.md` 与 `.interface-design/system.md`，记录现有产品约束、受保护视觉令牌、候选人端密度/动效旋钮和本轮复用边界；未执行 Git 暂存、提交或远程推送。

### 候选人申请跟踪第一轮 UI/UX 优化

- 2026-08-11 完成 `/applications` 保留式改造：申请状态、岗位名称搜索和页码同步到 URL，接入后端既有 `pageNo`、`pageSize`、`keyword`、`status` 查询协议。
- 申请卡片补充岗位/企业/申请编号/投递时间/匹配度/下一步信息层级；详情继续展示 AI 匹配依据、简历状态、AI 面试、线下面试和申请时间线，不展示原始 JSON。
- 增加骨架屏、空结果、错误重试、筛选清除、分页、匹配状态自动刷新和移动端无障碍语义；未改变申请状态机、候选人数据边界、API 路径或 OpenTalking/WebRTC 链路。

### 候选人简历中心第一轮 UI/UX 优化

- 2026-08-11 完成 `/resumes` 保留式改造：增加独立初始加载骨架、接口失败重试和真实空状态，避免将加载失败误显示为“还没有上传简历”。
- 上传表单补充字段语义、文件帮助说明、10MB 前置提示和状态反馈；简历卡片继续展示解析状态、技能、默认简历、私有文件读取、失败重试和后端删除保护。
- 将原生 `window.confirm` 替换为可访问的确认对话框，保持删除 API、关联申请保护和候选人数据权限不变；移动端 390px 视口无横向溢出。

### 候选人工作台第一轮 UI/UX 优化

- 2026-08-11 在 `/workspace` 增加“招聘准备”业务模块，使用现有申请列表和简历接口展示岗位申请总数、可投递简历数量、最近申请状态及对应入口。
- 工作台首页同时并行加载面试、题库、能力报告、申请和简历数据；各模块独立降级，失败时保留可用内容并提供统一重试入口。
- 补充工作台加载语义、招聘状态徽章、简历/申请快捷入口和 390px 移动端宽度检查；未改变候选人权限、面试状态机、API 路径或 OpenTalking/WebRTC 链路。

### 候选人面试大厅第一轮 UI/UX 优化

- 2026-08-11 完成 `/candidate/interviews` 保留式改造：面试记录、练习题库和自由面试记录改为独立降级加载，补充骨架屏、失败提示、重试入口和真实空状态，避免接口失败误显示为无记录。
- 搜索关键词与状态筛选同步到 URL，增加状态计数、筛选清除、已取消状态和面试记录排序；继续使用既有面试开始、练习创建、报告、心得和自由面试路由/API。
- 优化面试卡片的日期、状态、类型、面试官风格和行动层级，补充对话框描述、装饰图标语义、按钮类型与加载状态；Impeccable 检测无新增问题。
- 未改变面试状态机、候选人权限、OpenTalking/WebRTC 链路或后端接口；本地浏览器验证因后端 8080 未启动仅覆盖降级界面、筛选 URL、弹窗和 390px 无横向溢出。

### AI 招聘面试平台第三阶段：JD 驱动 AI 岗位匹配

- 2026-08-11 新增迁移 `V30__add_job_application_ai_match.sql`：为岗位申请增加匹配状态、简历版本、错误信息、开始/完成时间和查询索引；已有历史匹配结果标记为 `MANUAL`，V27-V29 未修改，后续数据库变化从 V31 开始。
- 新增 `JOB_MATCH` 异步 AI 任务：以岗位名称、岗位介绍、任职要求、技能标签和候选人结构化简历为输入，复用 DeepSeek 审计链路，校验 0-100 分并持久化匹配摘要、匹配技能、优势、待核实差距、风险、证据和建议；沿用任务去重、失败重试和旧简历版本保护。
- 简历解析完成后自动为关联岗位申请补排匹配任务，解析失败会将等待中的匹配标记为失败；候选人端与企业端均提供匹配状态自动刷新和失败重试，企业端仍以当前登录用户的 `companyId` 执行租户隔离。
- 候选人“我的申请”和企业“候选人申请”页面展示 AI 分析状态及业务可读的匹配依据，不展示原始 JSON；移动端 390px 视口无横向溢出。
- 本阶段未修改 OpenTalking、avatar-skill 或讯飞链路；未执行 Git 暂存、提交或远程推送。

### AI 招聘面试平台第三阶段验证

- 根目录 `mvn -B -ntp test` 通过：backend 71/71、algorithm-judge-worker 18/18，cloud-registry 与 cloud-gateway Reactor 构建成功；Spring Boot 上下文确认 Flyway 校验 24 条迁移且 schema 当前为 V30。新增 `RecruitmentJobMatchServiceTest` 通过。
- `frontend-react` 的 `npm.cmd run lint` 通过，0 个错误，保留项目既有 24 条 Hook 依赖警告；`npm.cmd run build` 通过，候选人/企业申请页生成新的生产分包。backend、frontend Docker 镜像均构建成功，frontend 首次构建的 Docker Hub 临时鉴权断连已重试恢复。
- `docker compose -p ainterview config --quiet` 通过；backend、frontend、MySQL、Redis、cloud-registry、cloud-gateway 和两实例算法判题 Worker 均健康运行，未删除数据卷；宿主机 `/api/v3/api-docs` 返回 200，岗位申请前端分包返回 200，PDF.js `.mjs` worker 返回 `application/javascript`。
- 使用企业演示账号对申请详情执行匹配重试，真实状态从 `PENDING` 经 `PROCESSING` 变为 `SUCCESS`，匹配度 80；数据库确认 `JOB_MATCH` 任务成功，候选人端接口同步返回结构化匹配摘要、技能、优势和差距信息。
- 浏览器完成候选人申请详情桌面与 390px 移动端验收：AI 分析完成徽章、80% 匹配度、岗位匹配依据和差距列表可见；移动端详情宽度 356px、页面滚动宽度 370px，小于 390px 视口，无横向溢出。
- 尚未验证或交付：生产服务器远程部署、真实 PDF/DOCX 简历与真实 JD 的完整解析到匹配链路、外部 AI 服务长期稳定性、邮件或短信实际送达；未执行 Git 暂存、提交或远程推送。

### AI 招聘面试平台第二阶段：真实简历中心

- 2026-08-11 新增已发布迁移 `V28__create_candidate_resume_analysis.sql`：为候选人简历增加解析状态、版本、错误信息、时间和内容哈希，新增简历解析历史表并关联 `ai_task`；新增 `V29__allow_resume_media_type.sql`，允许私有媒体存储使用 `resume` 媒体分类。V27 未修改，后续数据库变化继续从 V30 开始。
- 新增候选人简历中心 API：支持 PDF、DOCX、TXT、Markdown 上传，单文件默认 10MB 限制，扩展名与文件签名双重校验、路径穿越文件名清洗、私有对象存储、内容查看、默认简历切换、删除保护、解析任务查询和失败重试；仅简历所有者可读取，已关联岗位申请的简历禁止删除。
- 新增 `RESUME_PARSE` AI 任务类型，使用去重键避免同一简历版本重复排队；任务执行时从私有媒体提取文本，交给 DeepSeek 生成候选人画像与技能，解析成功/失败状态、错误信息、摘要和技能均持久化，失败沿用现有 AI 任务重试机制。
- 招聘投递流程仅允许使用 `SUCCESS` 或历史 `MANUAL` 简历；解析中的简历可以保存但匹配结果延迟生成，候选人岗位大厅隐藏不可投递的解析中/失败简历，避免把未完成的结构化资料用于岗位匹配。
- 新增候选人端 `/resumes` 页面和“我的简历”导航入口，支持上传进度、解析状态自动刷新、失败重试、默认切换、私有文件查看和删除；使用现有 Radix/Tailwind 组件体系补充标签语义、实时状态提示、触摸目标和移动端 `min-w-0` 布局约束。
- 本阶段未修改 OpenTalking、avatar-skill 或讯飞链路；未修改任何已发布迁移；未执行 Git 暂存、提交或远程推送。

### AI 招聘面试平台第二阶段验证

- backend `mvn -B -ntp -pl backend -am test` 通过：69/69 测试通过；Spring Boot 上下文测试确认 Flyway 从 V28 执行到 V29，未修改 V27。
- `frontend-react` 的 `npm.cmd run lint` 通过，0 个错误，保留项目原有 24 条 Hook 依赖警告；`npm.cmd run build` 通过，候选人简历页面生成独立生产分包。前端 Docker 镜像首次重建遇到 Docker Hub 临时鉴权断连，重试后构建成功。
- `docker compose -p ainterview config --quiet` 通过；backend、frontend、MySQL、Redis、cloud-registry、cloud-gateway 和两实例算法判题 Worker 均恢复运行，backend/frontend 镜像重建未删除数据卷；backend 健康检查为 UP，宿主机 `/api/v3/api-docs` 返回 200，PDF.js `.mjs` 返回 `application/javascript`。
- 使用演示候选人完成真实运行态验收：简历上传返回 200，私有内容读取返回 200，解析任务从 PENDING 自动变为 SUCCESS，解析版本为 1 并落库技能；另一候选人读取该简历详情和内容均返回 404，已关联岗位申请的简历删除返回 409。
- 浏览器完成 `/resumes` 桌面与 390px 移动端检查：上传表单、解析完成徽章、技能标签和默认简历操作可见，移动端 `bodyScrollWidth=380`、视口宽度 390，无横向溢出；页面控制台错误为 0。
- 尚未验证或交付：真实 PDF/DOCX 文件上传的完整 AI 解析链路、生产服务器远程部署、邮件或短信实际送达；TXT 真实 AI 解析和 PDF/DOCX 文件签名单测已验证。

### AI 招聘面试平台第一阶段

- 2026-08-11 新增已发布迁移 `V27__create_recruitment_platform.sql`：建立企业、候选人简历、岗位申请、申请状态历史和线下面试表，为用户与岗位补充企业归属及招聘字段，新增 `COMPANY_ADMIN` 角色、招聘权限和可直接联调的演示数据。V27 已分别应用到本机与 Compose MySQL，后续不得修改，新的数据库变化从 V28 开始。
- 新增候选人招聘 API，支持公开岗位筛选/详情、简历列表、岗位投递、重复投递拦截、申请列表与申请详情；新增企业端招聘 API，支持企业仪表盘、岗位新增/编辑/发布、候选人申请筛选/详情、状态推进和线下面试邀约。
- 企业端数据访问以登录用户的 `companyId` 为唯一租户边界，不接受前端传入企业 ID；补充申请状态机、乐观版本更新、状态历史审计和候选人持久化通知。无权限访问统一返回 403，跨企业读取返回 404，非法状态回退返回 400。
- 当前岗位匹配度采用技能标签交集的可解释规则评分，作为第一阶段可运行基线；真实简历解析、JD 大模型匹配和异步 AI 任务编排尚未接入。
- 新增候选人“招聘岗位”“我的申请”页面和企业端“招聘概览”“岗位管理”“候选人管理”页面，接入角色路由与登录后跳转；页面沿用 React、Radix UI 与 Tailwind 组件体系，桌面端使用业务表格/卡片，移动端使用单列卡片、抽屉导航和响应式选择器。
- Spring Boot 4 环境补充显式 Flyway 初始化配置，恢复启动时迁移、校验和基线行为；本阶段没有改动任何已发布迁移，也未修改 OpenTalking、avatar-skill 或讯飞链路。

### AI 招聘面试平台第一阶段验证

- 根目录 `mvn -B -ntp test` 通过：backend 68/68、algorithm-judge-worker 18/18，cloud-registry 与 cloud-gateway Reactor 构建成功；Flyway 成功校验 21 条迁移并确认 schema 当前为 V27。
- `frontend-react` 的 `npm.cmd run lint` 通过，0 个错误，保留 24 条既有 Hook 依赖警告；`npm.cmd run build` 通过，招聘候选人端和企业端页面均生成独立生产分包，既有 Monaco CodeEditor 大分包警告仍存在。
- backend 与 frontend Docker 镜像重建并重建容器后，backend 保持 healthy、frontend 正常提供服务；`docker compose -p ainterview config --quiet` 通过，Compose MySQL 启动日志确认 V27 成功执行，未删除或重建数据卷。
- 使用候选人与两家企业管理员演示账号完成真实接口验收：公开岗位 3 条；候选人投递成功、重复投递返回 409；企业可查看本企业岗位/申请；候选人访问企业 API、企业访问候选人 API 均返回 403；跨企业申请读取返回 404；状态从待面试推进到筛选中、线下面试成功，非法回退返回 400，候选人详情和通知同步更新。
- 浏览器完成候选人岗位大厅/申请列表、企业仪表盘/岗位管理/候选人详情的桌面与 390px 移动端检查，未发现横向溢出，详情以业务字段展示而非原始 JSON；采用现有 Radix 对话框、移动端选择器和焦点语义。
- 尚未验证或交付：真实简历 PDF 上传与私有媒体绑定、简历解析、真实 AI 岗位匹配、招聘申请与 AI 面试/报告自动关联、邮件或短信实际送达、生产服务器远程部署。未执行 Git 暂存、提交或远程推送。

### Spring Cloud 第一阶段

- 2026-08-10 新增可回滚的 Spring Cloud 基础设施骨架：根 Maven 聚合工程、Eureka 注册中心 `cloud-registry` 和基于 WebFlux 的 `cloud-gateway`，使用 Spring Boot 4.0.7 / Spring Cloud 2025.1.2；现有业务后端暂保持 Spring Boot 3.3.2，并通过 Spring Cloud 2023.0.3 Eureka Client 作为兼容桥接，未迁移业务包、数据库或算法判题 Worker。
- 后端应用名统一提供 `backend-core` 注册标识；本地默认关闭 Eureka，Compose 默认启用注册到 `cloud-registry:8761`。Gateway 仅将 `/api/**` 通过 `lb://backend-core` 转发到现有后端，JWT、安全、Flyway、Redis Stream 和 OpenTalking 业务边界保持在 backend。
- Docker Compose 新增注册中心和 Gateway 服务，前端 Nginx 默认将 `/api/` 转发到 Gateway；通过 `.env` 的 `API_UPSTREAM=backend:8080` 可以临时回退为直连后端，`/opentalking/` 仍保持独立直连链路。

### Spring Cloud 第一阶段验证

- 修改前后端基线 `mvn test` 通过；修改前测试使用 Java 21 运行，Flyway 校验 20 条迁移且数据库版本为 V26。
- 根目录 Maven 聚合 `mvn -B -ntp test` 通过：backend 55/55 测试通过，cloud-registry 与 cloud-gateway 无测试失败；`git diff --check` 通过。
- 前端 `npm.cmd run lint` 通过，0 个错误，保留既有 24 条 Hook 依赖警告；Docker 内 `npm run build` 通过并生成 PDF.js worker。
- `docker compose -p ainterview config --quiet` 通过；Cloud 镜像首次并行构建曾因大依赖网络传输中断，利用 BuildKit 缓存重试后 cloud-registry、cloud-gateway、backend、frontend 镜像均构建成功，未删除任何数据卷。
- Compose 实际启动后 MySQL、Redis、backend、cloud-registry、cloud-gateway、frontend 均健康；Eureka 查询确认 `BACKEND-CORE/backend:8080` 与 `CLOUD-GATEWAY/cloud-gateway:8082` 状态为 `UP`。
- Gateway `/actuator/health`、`/actuator/gateway/routes` 返回 200；容器内通过 `lb://backend-core` 访问 `/api/v3/api-docs` 返回 200，宿主机经前端 Nginx 访问 `/` 和 `/api/v3/api-docs` 均返回 200；PDF.js `.mjs` worker 返回 `application/javascript`。
- `/opentalking/` 仍由前端 Nginx 独立代理到 `OPENTALKING_UPSTREAM`，本机验收时上游 `192.168.65.254:8210` 未运行，因此 `/opentalking/runtime-config` 返回 502；未修改 OpenTalking、avatar-skill 或讯飞链路。

### Spring Cloud 第二阶段：后端 Boot 4 迁移

- 2026-08-10 将 `backend` 从 Spring Boot 3.3.2 迁移到 4.0.7，并将 Spring Cloud 依赖管理统一到 2025.1.2，与第一阶段的 `cloud-registry`、`cloud-gateway` 版本线对齐；业务仍保持模块化单体，不抽取算法判题 Worker。
- MyBatis-Plus 切换到 Boot 4 starter 3.5.17，并补充 3.5.9+ 独立拆出的 `mybatis-plus-jsqlparser` 分页依赖；Springdoc OpenAPI 升级到 3.0.3。
- 为 Boot 4 补充 `spring-boot-starter-restclient`，修复 `RestClient.Builder` 自动配置缺失；Jackson 2 自定义序列化暂通过 Boot 4 兼容包接入，当前编译会提示弃用告警，后续待 Jackson 3 迁移窗口再处理。
- 按 MyBatis-Plus 3.5.17 的 Lambda 元数据要求补充评分并发单测夹具初始化；评分业务实现和并行评分线程池未被放宽或改为串行。

### Spring Cloud 第二阶段验证

- backend `mvn -B -ntp test` 通过，55/55 测试通过；根目录 Maven Reactor `mvn -B -ntp test` 通过，backend、cloud-registry、cloud-gateway 全部成功。
- `docker compose -p ainterview build backend` 成功；未删除 MySQL/Redis 数据卷。实际 backend 容器以 Spring Boot 4.0.7、Java 17.0.19 启动，健康检查为 healthy，Flyway 仍使用已发布 V26 基线，未新增或修改迁移。
- Eureka 实际查询确认 `BACKEND-CORE/backend:8080` 与 `CLOUD-GATEWAY/cloud-gateway:8082` 均为 `UP`；Gateway 在后端重建后的注册表刷新窗口内曾返回一次 503，刷新后宿主机经前端 Nginx 访问 `/api/v3/api-docs` 返回 200。
- 前端 `npm.cmd run lint` 通过，0 个错误，保留既有 24 条 Hook 依赖警告；宿主机首页返回 200，PDF.js worker 返回 200 和 `application/javascript`。
- 本阶段未使用真实账号完成登录、算法真实提交、学习资料上传/授权/批注、真实 AI 调用、OpenTalking WebRTC 或实际邮件发送；OpenTalking 上游仍未运行时的 502 不属于本阶段迁移修复范围。未执行 Git 暂存、提交或远程推送。

### Spring Cloud 第三阶段：算法判题 Worker 运行时隔离

- 2026-08-10 新增 Compose 服务 `algorithm-judge-worker`，使用独立 Spring Boot 4.0.7 进程和 Eureka 注册名 `algorithm-judge-worker`；backend 继续负责 API、提交记录和 Redis Stream 发布，Worker 独立消费 `algorithm:judge:stream`。
- backend 默认关闭 `ALGORITHM_JUDGE_CONSUMER_ENABLED`，不再挂载宿主机 Docker Socket；`algorithm-judge-worker` 默认开启消费者并独占 `/var/run/docker.sock`，Java 判题沙箱的 Docker 调用边界从 API 容器移出。
- Worker 实例关闭 AI 任务定时轮询，避免同一共享数据库中的 AI 任务被重复消费；新增 `ALGORITHM_JUDGE_BACKEND_CONSUMER_ENABLED`、`ALGORITHM_JUDGE_WORKER_CONSUMER_ENABLED`、`AI_TASK_SCHEDULER_ENABLED` 配置边界。
- Redis Stream consumer 名称默认读取容器 `HOSTNAME`，支持多个 Worker 副本使用不同 consumer；保留旧的空闲 `judge-consumer-1` 历史记录，未删除 Redis 消费组数据。
- 本阶段是可回滚的运行时隔离基线，Worker 暂复用已验证的 backend 镜像和共享数据库；下一阶段再将判题代码、模型和 Mapper 拆为独立 Maven 服务，避免一次性复制整套业务上下文。

### Spring Cloud 第三阶段验证

- 根目录 `mvn -B -ntp test` 通过：backend 55/55 测试通过，cloud-registry、cloud-gateway 通过；`mvn -B -ntp -DskipTests compile` 和 Compose 配置检查通过。
- backend 与 `algorithm-judge-worker` 镜像重建成功；Compose 中 backend、Worker、MySQL、Redis、cloud-registry、cloud-gateway、frontend 均 healthy，未删除任何数据卷，Flyway 仍保持 V26。
- 实际容器验证：backend 的 `ALGORITHM_JUDGE_CONSUMER_ENABLED=false` 且 Docker Socket 不存在；Worker 的消费者开关为 true、AI 定时任务为 false、Docker Socket 存在，并通过 Unix Socket 调用 Docker API `/_ping` 返回 `OK`。
- Eureka 查询确认 `BACKEND-CORE/backend:8080`、`ALGORITHM-JUDGE-WORKER/algorithm-judge-worker:8084` 均为 `UP`；Redis Stream 判题组有 1 个活动 Worker consumer，pending 为 0；Gateway `/api/v3/api-docs` 和宿主机首页均返回 200。
- 本阶段未使用真实账号发起算法提交，因此未验证真实提交记录、Java Runner 执行、判题结果落库和前端轮询；未执行 Git 暂存、提交或远程推送。OpenTalking、avatar-skill 和讯飞链路未修改。

### Spring Cloud 第四阶段：算法判题 Worker 代码级拆分

- 2026-08-10 新增独立 Maven 模块 `algorithm-judge-worker`，使用 Spring Boot 4.0.7、Spring Cloud 2025.1.2、MyBatis-Plus Boot 4 3.5.17、Redis Stream 和 Docker Java；根目录 Maven 聚合新增该模块。
- 判题 Worker 独立拥有算法题目/提交/测试用例/用例结果/用户进度 Mapper、判题状态流转、Redis Stream 消费者、输入/结果 tar 归档安全边界和 Java 17 Docker 沙箱；异步提交仍与 backend 共享现有数据库表，不新增或修改 Flyway 迁移，当前数据库基线仍为 V26。
- backend 删除 Docker 沙箱、判题消费者和 tar 归档实现及对应 Docker Java/压缩依赖，仅保留题目权限校验、RUN 提交记录、Redis Stream 发布和 `AlgorithmJudgeWorkerClient`；同步自定义输入运行通过私有 `POST /api/internal/algorithm-judge/run` 调用 Worker，backend 不再具有 Docker Socket 访问代码路径。
- Compose 的 `algorithm-judge-worker` 改为从 `./algorithm-judge-worker` 独立构建 `ainterview-algorithm-judge-worker` 镜像；只有该服务挂载 `/var/run/docker.sock`，backend 只挂载媒体卷。内网接口支持可选 `ALGORITHM_JUDGE_INTERNAL_TOKEN`，生产环境应注入非空 Token 并继续保持 Worker 不发布宿主端口。
- 运行时发现外置 `docker-java-transport-httpclient5` 与 Unix Socket 调用链存在实际兼容问题：Worker 改用同版本 `docker-java-transport-zerodep`，并显式锁定 `commons-io:2.20.0` 以匹配 `commons-compress:1.28.0`，修复 tar 归档阶段 `NoSuchMethodError` 和 Docker Socket 调用失败。

### Spring Cloud 第四阶段验证

- 根目录 `mvn -B -ntp test` 通过：backend 55/55 测试通过，algorithm-judge-worker 编译成功（当前暂无 Worker 专属测试源），cloud-registry 与 cloud-gateway 均成功；Worker 独立 `mvn -B -ntp -f algorithm-judge-worker/pom.xml clean test` 通过。
- `docker compose config --quiet` 通过；backend 与独立 Worker 镜像均重建成功，Compose 中 backend、Worker、MySQL、Redis、cloud-registry、cloud-gateway、frontend 均为 healthy，未删除数据卷。
- 容器边界实测：backend 仅有 `/app/data/media` 挂载，Worker 仅有 `/var/run/docker.sock` 挂载；Worker 内 curl 访问 Docker Unix Socket `/_ping` 返回 `OK`。
- 从 backend 容器实际调用 Worker 内网运行接口，使用 Java 17 Runner 执行 `System.out.print(42)` 返回 `status=ACCEPTED`、输出 `42`，并返回耗时和内存数据；Eureka 查询确认 `ALGORITHM-JUDGE-WORKER/algorithm-judge-worker:8084` 为 `UP`，Redis 判题消费组 pending 为 0。
- 本阶段未使用真实账号访问 backend `/api/algorithm/run`，因此未制造持久化 RUN 提交记录；backend 公共 API 到 Worker 客户端的真实登录态链路、异步 SUBMIT 结果落库和前端轮询仍待下一阶段验收。前端本阶段无源码修改，未重复执行 lint/build；未执行 Git 暂存、提交或远程推送。OpenTalking、avatar-skill 和讯飞链路未修改。

### Spring Cloud 第五阶段：Worker 启动依赖与契约验证

- Compose 为 backend 增加 `algorithm-judge-worker` healthy 依赖，确保 backend 对 Worker 的同步调用和 Redis Stream 判题消费不会因服务启动竞态而在冷启动窗口失败；Worker 仍不发布宿主端口，Docker Socket 仍只挂载给 Worker。
- 为独立 Worker 增加 7 个自动化测试，覆盖同步运行结果映射、编译错误信息、源码长度边界、内部 Token 鉴权、授权请求转发和结果 tar 归档路径穿越防护；不依赖真实 Docker 容器或业务数据库。
- 根目录 `mvn -B -ntp test` 通过：backend 55/55、Worker 7/7，cloud-registry 与 cloud-gateway 均成功；`docker compose config --quiet` 通过，更新后的 backend、Worker 及其依赖容器均保持 healthy。
- 从 backend 容器实际请求 Worker 内部接口，再由 Worker 调用 Java 17 Runner 执行 `System.out.print(42)`，返回 `status=ACCEPTED`、输出 `42`、耗时和内存数据；backend 仍只有媒体卷，Worker 仍只有 Docker Socket 挂载。
- 本阶段仍未使用真实账号调用 backend `/api/algorithm/run`，因此未验证带 JWT 的公共 API、异步 SUBMIT 结果落库和前端轮询；未执行前端 lint/build、Git 暂存、提交或远程推送。数据库迁移仍保持 V26，OpenTalking、avatar-skill 和讯飞链路未修改。

### Spring Cloud 第六阶段：判题状态流转契约验证

- backend 对 Worker 返回空状态增加防护：记录并返回 `SYSTEM_ERROR`，避免异常响应把空状态写入 `algorithm_submission.status`；正常 Worker 通信异常仍保留技术错误信息，便于排查容器网络或服务故障。
- 新增 backend 判题单测 6 个，覆盖同步 `RUN` 成功结果落库、Worker 通信失败、Worker 空状态、源码校验、异步 `SUBMIT` 创建 `QUEUED` 记录并发布任务，以及停用题目拒绝入队；Worker 仍保留 7 个契约测试。
- 根目录 `mvn -B -ntp test` 通过：backend 61/61、algorithm-judge-worker 7/7，cloud-registry 与 cloud-gateway 均成功；`docker compose config --quiet` 通过，backend 镜像重建成功，未删除数据卷。
- 运行态验证：重建后的 backend、Worker、Gateway、注册中心、MySQL、Redis 和 frontend 均 healthy；从 backend 容器请求 Worker 实际执行 Java 17 Runner 返回 `status=ACCEPTED`、输出 `42`；宿主机经 frontend Nginx/Gateway 未登录调用 `/api/algorithm/run` 返回 `403`。
- 本阶段未使用真实账号发起持久化算法提交，因此异步 `SUBMIT` 经 Redis Stream 消费后结果落库、JWT 登录态公共提交和前端轮询仍未做真实数据验收；前端源码未修改，未重复执行 lint/build；数据库迁移保持 V26，未执行 Git 暂存、提交或远程推送。OpenTalking、avatar-skill 和讯飞链路未修改。

### Spring Cloud 第七阶段：真实异步判题链路验收

- 使用现有演示账号 `candidate_liu` 登录前端入口对应的 Gateway，JWT 登录成功并识别为 `CANDIDATE`；使用题目 1 的 Java17 A+B 代码提交异步判题任务，提交记录 ID 为 `2086814697240965121`。
- 实际轮询 `GET /api/algorithm/submissions/{submissionId}`：记录先处于 `QUEUED`，随后变为 `ACCEPTED`，6/6 测试用例通过；MySQL 已确认 `algorithm_submission`、6 条 `algorithm_case_result` 和 `algorithm_user_progress` 均完成持久化更新。
- Redis 只读检查确认 `algorithm-judge-group` 的 `pending=0`、`lag=0`；这证明本次任务已经从 backend 发布、被 Worker 消费、执行 Java17 Runner 并完成结果落库。
- 本次真实验收新增 1 条候选人演示提交记录，未删除或覆盖任何既有数据；未修改前端源码、数据库迁移或 OpenTalking/avatar-skill/讯飞链路，未执行 Git 暂存、提交或远程推送。前端 lint/build 本阶段未重复执行。

### Spring Cloud 第八阶段：前端异步判题状态交付

- 前端算法题详情页统一复用 `algorithmTerminalStatuses`，提交轮询继续覆盖排队、编译、运行和全部终态；当 90 秒轮询仍未结束或轮询请求中途失败时，页面退出“正在判题”加载语义，提示用户到提交记录查看后台最新状态。
- `frontend-react` 执行 `npm.cmd run lint` 通过，0 个错误，保留既有 24 条 Hook 依赖警告；Docker frontend 镜像重新构建成功并重启，实际 Nginx bundle `ProblemDetailPage-Bile8BKx.js` 已包含新的超时提示文案。
- 运行态验证：宿主机首页和 `/algorithm/problems` SPA 路由均返回 200；PDF.js worker 继续返回 `application/javascript`；Compose 配置检查和 `git diff --check` 通过，所有服务保持正常运行。
- 本阶段未执行可视化浏览器登录、点击提交和页面截图验收；真实 JWT/异步判题链路已在上一阶段通过接口和数据库验证。前端构建仍有 CodeEditor 约 3.98MB 大 chunk 警告，后续可单独进行代码分包优化；未修改数据库迁移，未执行 Git 暂存、提交或远程推送。

### Spring Cloud 第九阶段：Worker 抢占幂等与短事务落库

- 2026-08-10 为 `algorithm-judge-worker` 增加数据库条件抢占：只有 `SUBMIT + QUEUED` 的提交可以原子切换到 `COMPILING`；已完成、已失败或被其他 Worker 抢到的任务直接跳过，避免 Redis 重复投递导致重复 Docker 执行、重复用例结果和重复提交统计。
- 将 Docker 编译/执行从原有长事务中移出；新增独立的 `AlgorithmJudgeResultPersistenceService`，仅在结果写入、提交状态、用例结果和用户进度更新阶段开启短事务，事务失败时用例结果与提交摘要一起回滚。
- Redis Stream pending 回收改为 `XCLAIM`，仅在消息空闲达到 10 分钟后转移所有权；恢复路径允许 stale 的 `COMPILING/RUNNING` 提交重新抢占，覆盖 Worker 崩溃后的未完成任务。正常消费、重试和 stale 恢复均继续在成功处理后 ACK。
- Worker 新增条件抢占、终态幂等、抢占竞争、stale 恢复和消费者一次重试/失败转 `SYSTEM_ERROR` 测试；本阶段未新增或修改 Flyway 迁移，数据库基线仍为 V26。

### Spring Cloud 第九阶段验证

- 根目录 `mvn -B -ntp test` 通过：backend 61/61、algorithm-judge-worker 12/12，cloud-registry 与 cloud-gateway 均成功；`git diff --check` 通过。消费者重试单测会输出预期的 ERROR 堆栈日志，但测试结果无失败。
- `npm.cmd run lint` 通过，0 个错误，保留既有 24 个 Hook 依赖警告；`npm.cmd run build` 通过，仍提示 CodeEditor 约 3.98MB 大 chunk，未在本阶段扩大前端改动范围。
- `docker compose config --quiet` 通过；backend 与 Worker 镜像均重建成功，最终 Worker 容器健康，容器内 Worker Actuator 返回 `UP`，Redis、MySQL、backend、cloud-registry、cloud-gateway 和 frontend 保持运行；未删除或重建任何数据卷。
- 使用真实演示账号完成一次异步提交，记录 ID 为 `2086820579034128386`：接口轮询从 `COMPILING` 到 `ACCEPTED`，6/6 用例通过、分数 60；MySQL 只读核对到该提交有 6 条 `algorithm_case_result`，Redis `algorithm-judge-group` pending 为 0。
- 本阶段未做可视化浏览器页面截图、真实 stale 崩溃恢复等待 10 分钟、生产环境多副本并发压测、OpenTalking WebRTC 或实际邮件发送；未执行 Git 暂存、提交或远程推送。OpenTalking、avatar-skill 和讯飞链路未修改。

### Spring Cloud 第十阶段：Worker 可观测性

- 2026-08-10 为 `algorithm-judge-worker` 接入 Micrometer Prometheus Registry，增加判题活动数、Redis Stream pending 数、任务抢占、判题结果、重试、错误和 Docker 执行耗时指标；执行耗时发布 P50/P95/P99 分位数，指标统一带应用名标签。
- 将指标埋点接入新消息消费、stale 任务恢复、Docker 执行、结果落库、消费者重试/耗尽和异常路径；pending gauge 只读 Redis pending summary，Redis 暂时不可用时不影响 Worker 主流程。
- 本阶段未新增或修改 Flyway 迁移，未修改前端、OpenTalking、avatar-skill 或讯飞链路；仍保留模块化单体 backend + 独立隔离判题 Worker 的部署边界。

### Spring Cloud 第十阶段验证

- 根目录 `mvn -B -ntp test` 通过：backend 61/61、algorithm-judge-worker 14/14，cloud-registry 与 cloud-gateway 均成功；`docker compose config --quiet` 和 `git diff --check` 通过。
- 重建并重启 Worker 镜像后，容器保持 healthy，Worker Actuator health 返回 `UP`；容器内 `/actuator/prometheus` 实际暴露 `ai_interview_algorithm_judge_active`、`ai_interview_algorithm_judge_stream_pending`、`ai_interview_algorithm_judge_claims_total`、`ai_interview_algorithm_judge_outcomes_total` 和执行耗时指标。
- 直接调用 Worker Java17 判题接口返回 `status=ACCEPTED`、输出 `42`；随后使用真实演示账号完成异步提交，记录 ID 为 `2086823369487802369`，状态从 `COMPILING` 变为 `ACCEPTED`，6/6 用例通过、分数 60。该次运行后 Prometheus 显示 active=0、stream pending=0、new claim=1、accepted outcome=1、execution count=2，Redis `algorithm-judge-group` pending=0。
- 本阶段未接入生产 Prometheus/Grafana 抓取与告警，未等待 10 分钟执行真实 stale 崩溃恢复，也未制造真实 Docker 编译失败或消费者重试；失败、重试和指标计数由单元测试覆盖。未执行 Git 暂存、提交或远程推送。

### Spring Cloud 第十一阶段：可选监控采集与告警基础

- 2026-08-10 新增可回滚的 Compose `monitoring` profile：Prometheus 抓取 backend `8081`、algorithm-judge-worker `8085` 和 cloud-gateway `8083` 的 `/actuator/prometheus`，Alertmanager 接收 Prometheus 告警；默认 `docker compose up` 不启动监控服务，业务默认启动路径不变。
- 新增判题告警规则：backend/Worker/Gateway 指标端点不可用、判题 pending 持续堆积、判题 SYSTEM_ERROR、Docker 平均执行耗时持续超过 30 秒；Prometheus 和 Alertmanager 端口仅绑定宿主机 `127.0.0.1`，不直接暴露公网。
- 为 cloud-gateway 补充 Micrometer Prometheus Registry 依赖和 Prometheus 导出配置，修复监控 profile 初次验收时 Gateway `/actuator/prometheus` 返回 404 的问题。
- 监控镜像版本固定为 `prom/prometheus:v2.55.1` 和 `prom/alertmanager:v0.27.0`；Alertmanager 配置不携带邮件、Webhook 或其他外部通知凭据，生产环境需要在部署侧补充通知接收器。

### Spring Cloud 第十一阶段验证

- 默认 Compose 配置和 `--profile monitoring` 配置均通过 `docker compose config --quiet`；监控服务列表正确包含 `prometheus`、`alertmanager`，三份监控 YAML 均通过 UTF-8 YAML 解析，Alertmanager 官方 `amtool check-config` 返回 `SUCCESS`。
- 监控镜像首次拉取曾超时，随后重试完成；Prometheus 和 Alertmanager 均启动为 healthy，端口分别绑定 `127.0.0.1:9090`、`127.0.0.1:9093`。Prometheus `/-/ready` 返回 `Prometheus Server is Ready`，Alertmanager `/-/ready` 返回 `OK`，Alertmanager API 版本为 `0.27.0`。
- Prometheus targets 实测 backend、algorithm-judge-worker、cloud-gateway 全部 `up` 且无抓取错误；6 条可用性/判题告警规则均加载成功、health=ok、当前 inactive；Prometheus 查询到 Worker `ai_interview_algorithm_judge_stream_pending=0` 和 `ai_interview_algorithm_judge_claims_total{recovery="new"}=1`。
- `mvn -B -ntp -pl cloud-gateway -am test`、cloud-gateway 镜像重建和容器重启通过；`git diff --check` 通过（仅有工作区既有 LF/CRLF 转换提示）。未修改 Flyway、未删除数据卷、未执行 Git 暂存/提交/远程推送。
- Gateway 修复后再次执行根目录 `mvn -B -ntp test`，Reactor 构建成功；backend 61/61、algorithm-judge-worker 14/14，cloud-registry 与 cloud-gateway 均成功。测试日志中保留既有预期异常/告警输出，但失败数和错误数均为 0。

### Spring Cloud 第十二阶段：Grafana 监控仪表盘

- 2026-08-10 为 Compose `monitoring` profile 增加 Grafana 服务，预置 Prometheus 数据源和 `AInterview 运行监控` dashboard，展示核心服务可用性、Redis 判题 pending、任务抢占/结果/错误速率和 Docker 平均执行耗时。
- Grafana 默认仅绑定宿主机 `127.0.0.1:3000`，关闭匿名访问和自助注册；管理员用户名、密码、根 URL 和端口均可通过 `GRAFANA_ADMIN_USER`、`GRAFANA_ADMIN_PASSWORD`、`GRAFANA_ROOT_URL`、`GRAFANA_PORT` 覆盖。`.env.example` 仅增加占位配置，未写入真实凭据；本机 Windows 的 2940-3039 端口排除范围导致验收临时使用 `GRAFANA_PORT=4000`，仓库默认值未改变。
- Dashboard 使用固定 Prometheus 数据源 UID `ainterview-prometheus`，采用文件 provisioning 且禁止 UI 覆盖，避免容器重启后数据源和面板漂移。

### Spring Cloud 第十二阶段验证

- 默认 Compose 配置和 `--profile monitoring` 配置均通过 `docker compose config --quiet`；Grafana dashboard JSON、数据源 YAML、dashboard provider YAML 均通过本地解析。
- Dashboard 中 6 条 PromQL 已直接通过 Prometheus API 验证，均返回 `status=success`、`resultType=vector`；Prometheus、Alertmanager 继续 healthy，现有 backend、Worker、Gateway targets 不受影响。
- Grafana 镜像拉取完成后，以临时 `127.0.0.1:4000` 端口启动并保持 healthy；`/api/health` 返回 database=ok、version=11.3.0。Grafana API 确认 `AInterview Prometheus` 数据源 UID 为 `ainterview-prometheus`、URL 为 `http://prometheus:9090`、isDefault=true、readOnly=true；dashboard API 确认 `AInterview 运行监控` 位于 `AInterview` 文件夹，UID 为 `ainterview-overview`、`provisioned=true`、包含 6 个面板。
- Grafana 日志中的 xychart duplicate registration 为官方镜像插件初始化告警，不影响服务健康、Prometheus 数据源或 dashboard provisioning；本阶段未引入真实通知凭据。
- 未修改 Flyway、未删除既有数据卷，未执行 Git 暂存、提交或远程推送；Grafana 生产部署必须覆盖占位管理员密码并配置真实通知渠道。

### Spring Cloud 第十三阶段：注册中心可观测性

- 2026-08-10 为 `cloud-registry` 增加 Micrometer Prometheus Registry、`/actuator/prometheus` 暴露配置和应用名指标标签；Eureka 注册中心现在与 backend、Worker、Gateway 使用同一套 Prometheus 监控链路。
- Prometheus 新增 `cloud-registry:8761` 抓取目标和 `AInterviewRegistryDown` 可用性告警；Grafana 核心服务可用性面板同步纳入 cloud-registry。

### Spring Cloud 第十三阶段验证

- `mvn -B -ntp -pl cloud-registry -am test` 通过，cloud-registry Docker 镜像重建并重启成功；容器 healthcheck 返回 healthy，容器内 `/actuator/prometheus` 实际返回 `application="cloud-registry"` 指标。
- Prometheus reload 返回 HTTP 200；4 个 targets（backend、algorithm-judge-worker、cloud-gateway、cloud-registry）全部 `up` 且无抓取错误，`cloud-registry` 查询值为 `1`；7 条告警规则全部加载成功、health=ok、当前 inactive。
- Grafana API 验证 dashboard `ainterview-overview` 已同步包含 `cloud-registry` 的核心服务可用性查询；Prometheus、Alertmanager、Grafana 和业务容器继续 healthy。未修改 Flyway、未删除数据卷、未执行 Git 暂存/提交/远程推送。

### Spring Cloud 第十四阶段：Grafana 页面视觉验收

- 2026-08-11 使用本地 Grafana 登录态浏览器实际打开 `AInterview 运行监控` dashboard，页面正常渲染核心服务可用性、判题队列堆积、判题结果速率、任务抢占速率、错误速率和 Docker 平均耗时 6 个面板。
- 视觉验收结果：核心服务可用性显示 `1`，Redis pending 显示 `0`，accepted 结果和 new 抢占曲线可见，Docker 平均耗时显示 `0 ms`；页面无阻断性布局或加载问题。
- 浏览器控制台仅记录 Grafana 官方前端的日期格式弃用警告和 `loadDashboardScene` 性能标记错误，未发现项目 dashboard 查询或渲染失败；该问题未修改第三方镜像内容。未修改 Flyway、未删除数据卷、未执行 Git 暂存/提交/远程推送。

### Spring Cloud 第十五阶段：Worker 服务发现与内部调用治理

- 2026-08-11 为 backend 的 `AlgorithmJudgeWorkerClient` 接入 Spring Cloud LoadBalancer：Compose 环境通过 Eureka 服务名 `algorithm-judge-worker` 选择健康 Worker，本地或显式关闭发现时保留 `ALGORITHM_JUDGE_WORKER_BASE_URL` 直连回退，不改变现有同步 RUN/异步 SUBMIT 业务边界。
- 为 Worker 调用增加可配置连接超时（默认 2 秒）和读取超时（默认 30 秒）；未发现实例、连接失败、响应异常和非法配置统一转换为 `SYSTEM_ERROR`，避免请求线程无限等待。新增客户端单测覆盖 Eureka 选址、直连回退、无实例故障和必需 Token 配置。
- 内部接口鉴权增加严格开关 `ALGORITHM_JUDGE_REQUIRE_INTERNAL_TOKEN`；启用后必须配置非空 Token，并使用常量时间比较校验 `X-Internal-Token`。当前 Compose 默认保持兼容模式关闭严格校验，生产部署应同时注入非空 Token 并打开该开关。
- 移除 Compose 对 Worker 的固定 Eureka instance-id，Worker 默认使用 `应用名:端口:随机 UUID` 注册，支持多个副本同时注册而不互相覆盖；backend Compose 同时启用 `EUREKA_FETCH_REGISTRY=true`，确保 LoadBalancer 能读取 Worker 注册表。
- 本阶段未新增或修改 Flyway 迁移，数据库基线仍为 V26；未修改 OpenTalking、avatar-skill 或讯飞链路。

### Spring Cloud 第十五阶段验证

- `mvn -B -ntp -pl algorithm-judge-worker test` 与 `mvn -B -ntp -pl backend test` 均通过：Worker 16/16、backend 65/65，失败数和错误数均为 0。测试日志中的消费者重试、Redis 不可用和 Mockito/JDK agent 告警均为既有预期输出。
- Worker 镜像重新构建成功并重启为 healthy；随后使用 `docker compose --scale algorithm-judge-worker=2` 启动两个副本，两个容器均 healthy。Eureka 实际查询确认两个 `ALGORITHM-JUDGE-WORKER` 实例均为 `UP`，instance-id 分别带有不同随机 UUID。
- 使用真实演示账号经宿主机 frontend/Nginx → cloud-gateway → backend 调用 `POST /api/algorithm/run`，题目 1 的 Java 17 程序返回 `ACCEPTED`、输出 `42`，提交记录 ID 为 `2086977488561799170`；该请求验证了 backend 通过 Eureka/LoadBalancer 调用 Worker 的真实同步链路。
- `docker compose -p ainterview --profile monitoring config --quiet` 和 `git diff --check` 已执行；未删除数据卷、未执行 Git 暂存/提交/远程推送。生产真实 Token 轮换、Worker 故障期间的长时压测和熔断策略尚未在本阶段实施，列为后续可靠性阶段。

### Spring Cloud 第十六阶段：Worker 熔断、安全凭据与并发隔离

- 2026-08-11 backend 接入 `spring-cloud-starter-circuitbreaker-resilience4j` 5.0.2（Resilience4j 2.3.0），为同步 Worker 调用配置 20 次滑动窗口、最少 5 次调用、50% 失败/慢调用阈值、10 秒 OPEN 等待和 3 次 HALF_OPEN 探测；调用异常会参与熔断统计，编译错误和运行错误等正常判题结果不会误触发熔断，未增加盲目 POST 重试。
- backend 同步 RUN 增加公平信号量并发隔离，默认最多 8 个在途 Worker 调用、默认不排队；达到上限时快速返回 `SYSTEM_ERROR`。新增 Bulkhead 活跃数和拒绝总数指标，Resilience4j 自动暴露熔断状态与调用结果指标。
- backend 与 Worker 的内部 Token 均支持优先读取 `ALGORITHM_JUDGE_INTERNAL_TOKEN`，为空时读取默认 `/run/secrets/algorithm-judge-internal-token` 文件；严格模式下任一侧缺少有效 Token 都会在启动期失败。Worker 继续使用常量时间比较校验请求头，Compose 本地默认仍保持兼容模式，生产环境应通过 mode-0600 Secret 文件或受控环境注入后启用严格模式。
- Prometheus 增加 Worker 熔断 OPEN 和并发隔离拒绝两条告警，总规则数增至 9；Grafana `AInterview 运行监控` dashboard 增加熔断状态和近 5 分钟并发拒绝两个面板，总面板数增至 8。

### Spring Cloud 第十六阶段验证

- 根目录 `mvn -B -ntp test` 通过：backend 68/68、algorithm-judge-worker 18/18，cloud-registry 与 cloud-gateway 均成功；新增测试覆盖 Secret 文件读取、严格模式空凭据启动拒绝、熔断打开和并发隔离快速拒绝。
- backend 与 Worker Docker 镜像重建成功；严格模式首次传入空 Token 时两个 Worker 按设计启动失败，随后使用临时随机 Token 重新创建后 backend 和两个 Worker 均 healthy。缺失 Token、错误 Token 访问 Worker 内部接口均返回 HTTP 401，backend 携带正确 Token 的真实 RUN 返回 `ACCEPTED/42`，提交 ID 为 `2086984789821210625`。
- 单 Worker 下线时 Eureka 仅保留 1 个 UP 实例，真实 RUN 仍返回 `ACCEPTED/43`，提交 ID 为 `2086985287454408705`；恢复后两个 Worker 均重新注册为 UP。
- 两个 Worker 全部下线后，前 3 次真实 RUN 因无可用实例在 55-69ms 内返回 `SYSTEM_ERROR`，达到窗口失败率后熔断器 OPEN，后续 3 次在 48-49ms 内以 `CallNotPermittedException` 快速失败；恢复 Worker 后熔断器自动进入 HALF_OPEN，3 次真实 RUN 均返回 `ACCEPTED/45`，随后指标恢复为 CLOSED。故障演练共生成 6 条 SYSTEM_ERROR 提交和 3 条恢复成功提交，未删除或覆盖既有业务数据。
- 测试结束后已同时重建 backend 与两个 Worker，恢复 Compose 默认 `ALGORITHM_JUDGE_REQUIRE_INTERNAL_TOKEN=false`，最终三个容器均 healthy；最终真实 RUN 返回 `ACCEPTED/46`，提交 ID 为 `2086987979965739010`。Prometheus reload 返回 200，9 条规则已加载，Grafana dashboard 文件通过 JSON 校验并包含 8 个面板。
- 本阶段未新增或修改 Flyway 迁移，未修改前端业务代码、OpenTalking、avatar-skill 或讯飞链路，未删除数据卷，未执行 Git 暂存、提交或远程推送。

### 交付基线

- 2026-08-10 统一前端交付运行时：`package.json` 的 Node.js 引擎约束、GitHub Actions 与 Docker 构建均采用 Node.js 22.13 基线，避免 PDF.js 在 CI 使用 Node.js 20 造成环境漂移。
- Compose 为 backend 增加仅通过容器内部 8081 管理端口访问 Actuator 的健康检查，frontend 等待 backend 健康后再启动；管理端口仍不向宿主机或 Nginx 发布。
- 邮件作为可选通知渠道，默认关闭 Spring Boot Mail Health，并新增 `MAIL_HEALTH_ENABLED` 开关，避免 SMTP 暂时不可用导致核心 API 健康状态误报；新环境验证码邮件标题统一为“AInterview 验证码”。

### 交付基线验证

- 使用 Java 17、Maven 3.9 容器执行完整 `mvn test`，55/55 测试通过；Mail Health 配置变更后 `ActuatorEndpointTest` 1/1 再次通过；Flyway 验证 20 条迁移记录，当前版本为 V26。
- 前端 `npm ci`、`npm run build` 与 `npm run lint` 通过，lint 为 0 个错误并保留既有 24 条 Hook 依赖警告；Node.js 22.13 Docker 构建已生成 PDF worker、学习资料和算法可视化分包。
- `docker compose -p ainterview config --quiet`、Nginx 配置检查和 java-runner Java 17 验证通过；已重建 runner、backend、frontend 镜像，MySQL、Redis、backend、frontend 四个基础服务正常运行，backend 连续 6 次内部健康检查均返回 HTTP 200。
- backend、frontend、java-runner 当前镜像摘要分别为 `sha256:0557205270f1445e00b57056318c7aa26eb978fadc20dc5ed04e8e1821e80359`、`sha256:9a1cc303301a046a350d01fd0bd48a30b6dbf587e5a11da2bba80be5a718921b`、`sha256:3a0462d3b2ef5e10665645576c3fc04648fc37751a2dfde7d27dae863d3b0e63`，运行容器使用的 backend/frontend 镜像与本轮构建一致。
- 宿主机首页、登录页、学习资料页、算法可视化首页和详情页均返回 HTTP 200；未登录访问学习资料与算法题接口返回 403；OpenAPI 包含 10 条学习资料路径；PDF.js `.mjs` worker 返回 `application/javascript`。
- 本轮未使用真实账号验证资料上传/授权/批注、算法真实提交、OpenTalking WebRTC 与实际邮件发送，避免修改现有业务数据；未执行远程镜像仓库推送、Git 暂存、提交或推送。

### 算法可视化实验室

- 2026-08-07 新增候选人端“算法可视化”独立入口 `/algorithm/visualizer`，按数组排序、查找、数据结构和图算法分类展示实验；算法练习首页新增可视化跳转入口，候选人导航新增“算法可视化”。
- 新增浏览器本地 Step 引擎和共享播放器：每一步同步保存当前数据、执行代码行、活动元素、指针/队列状态和中文操作说明，支持播放、暂停、第一步、上一步、下一步、最后一步、重新开始和 0.5x–2x 速度切换。
- 第一版新增冒泡排序、选择排序、插入排序、快速排序、二分查找、链表反转、广度优先搜索和深度优先搜索；提供数组柱状图、链表指针、图节点/边、访问顺序和复杂度展示，支持自定义数组/链表输入与二分查找目标值。
- 可视化算法代码、Step 生成器和数组/链表/图渲染组件均位于 React 前端，不新增后端接口、数据库表或 Flyway 迁移；为后续树、堆、Dijkstra 和 Java 源码实验保留统一 kind/step 扩展边界。

### 算法可视化验证

- React `npm run lint` 通过（0 错误，保留项目既有 24 条 Hook 依赖警告）；`npm run build` 通过，生成独立的算法可视化首页和详情懒加载分包。
- 已在本地开发服务的登录态浏览器中验证 `/algorithm/visualizer`、冒泡排序、BFS、链表反转和 DFS 页面；下一步、输入重算、导航跳转和浏览器本地步骤生成正常，浏览器控制台无错误。
- 已在默认桌面视口和 390px 窄屏验证布局；窄屏页面 `document.body.scrollWidth` 未超过视口宽度，代码区保持内部横向滚动，主页面无横向溢出。
- 本次未执行后端 Maven 或生产环境验证；本机 Docker Compose 与运行容器验收结果见下方“算法可视化 Docker 发布”。

### 算法可视化 Docker 发布

- 2026-08-07 使用 Docker Desktop `desktop-linux` 引擎和 Compose 项目 `ainterview` 重建 `ainterview-frontend:latest`，仅重建并重启 frontend，保留 MySQL、Redis、媒体卷和 backend 容器。
- 容器实际运行镜像摘要为 `sha256:838e69af197b0464666e525073518b724b5a38cb41c7bf01c958f888fa2c8a03`；`/usr/share/nginx/html/assets` 已包含 `AlgorithmVisualizerPage`、`AlgorithmVisualizerDetailPage` 和算法运行时编译分包。
- 通过宿主机 `http://127.0.0.1/algorithm/visualizer` 及冒泡排序详情页验证 Nginx 实际用户路径返回正常，详情页播放控件可加载，容器浏览器控制台无错误。
- 本次未推送到远程 Docker Registry；当前完成的是本机 Docker Desktop 镜像构建与 Compose 容器更新。

### 学习资料中心

- 2026-08-06 新增 Flyway V26 学习资料模块：管理员上传私有 PDF，保存资料版本、页数、文件摘要、发布状态和下载策略；原始 PDF 继续复用现有私有媒体存储、文件签名校验、SHA-256 与可选 ClamAV 扫描，不生成公开文件 URL。
- 新增按用户/角色授权的数据模型和管理端接口；候选人列表只返回已发布且当前账号拥有查看权限的资料，PDF 内容接口在服务端再次校验权限，未授权请求不会暴露资料是否存在。
- 新增管理员学习资料页面：上传 PDF、填写标题和说明、发布/下线、删除资料、设置候选人查看权限及批注权限；管理端菜单新增“学习资料”。
- 新增候选人学习资料中心和 PDF 阅读器：使用 PDF.js worker 渲染页面和文本层，支持翻页、缩放、文字选择高亮、页面便签、个人笔记编辑/删除和权限控制；批注以归一化页面坐标叠加保存，不修改原始 PDF，默认仅本人可见。
- 新增批注持久化表，支持 HIGHLIGHT、NOTE、UNDERLINE、STRIKEOUT、RECTANGLE、INK 类型、版本号和逻辑删除；更新批注时使用版本号校验，避免并发覆盖。
- 新增下载接口并在后端执行 `allow_download` 校验；管理员可下载，候选人只有在资料允许下载且具备查看权限时才可下载。
- 前端新增 `pdfjs-dist` 依赖、资料路由、PDF worker 资源和资料中心导航入口；本次在用户明确要求后同步更新项目结构和相关总结文档。

### 学习资料中心验证

- Maven 编译和 `mvn test` 通过；Spring Boot 测试实际连接本地 MySQL，Flyway 校验 20 个迁移并确认数据库版本为 V26。
- React `npm run lint` 通过（0 错误，保留项目已有 24 条 Hook 依赖警告），`npm run build` 通过并成功生成 PDF.js worker 与学习资料页面分包。
- 已使用 Docker Desktop 的 `desktop-linux` 引擎，以 Compose 项目名 `ainterview` 重建 `backend` 与 `frontend`，保留 MySQL/Redis 数据卷；四个基础服务均正常运行。
- 后端启动日志确认 Flyway 从 V25 成功迁移到 V26；`http://127.0.0.1/learning-resources` 返回新 Nginx 前端，主 bundle 已包含“学习资料”导航，资料懒加载分包可正常加载；未登录访问资料 API 返回 403，服务端权限拦截生效。

### 运行时可靠性与媒体访问

- 2026-08-06 将前端 Docker 构建基线从 Node 20 调整为 Node 22.13，以匹配 `pdfjs-dist@6.2.108` 的运行要求；AInterview Compose 镜像重新构建并启动验证通过。
- 学习资料删除改为同步逻辑删除关联媒体记录，撤销已删除资料的直接媒体访问；原始文件暂不做物理删除，保留后续回收站和恢复能力，避免不可恢复的数据丢失。
- 管理端学习资料删除操作改用共享 `AdminConfirmDialog` 站内确认弹窗，移除该页面的浏览器原生 `window.confirm`，统一按钮、处理中状态和危险操作交互。
- 后端编译、Maven 测试、前端 lint、Docker 构建、容器启动和 localhost HTTP 验证均通过；本次没有修改项目结构总结文档。

### 学习资料中心性能与交互

- 新增候选人资料分页接口 `/v1/learning-resources/page`，服务端先按用户/角色和有效期过滤查看权限，再进行数据库分页，避免无权限资料影响页码统计。
- 候选人学习资料中心新增总数、当前页码、上一页/下一页控制；保留原列表接口以兼容已有调用方。
- 本轮分页代码通过 Maven 编译、React lint/build 和 AInterview Docker 重建；容器启动后 Flyway 保持 V26，localhost 页面返回 200，未登录访问分页 API 返回 403。

### 学习资料 PDF 传输

- PDF 在线阅读和下载接口现在返回上传时的安全原始文件名、准确 `Content-Length`、`Content-Disposition` 和 `X-Content-Type-Options: nosniff`；在线阅读仍只校验查看权限，下载仍额外校验下载开关。
- 后端 Maven 编译和测试通过，AInterview backend/frontend 镜像已重建并启动；未使用真实账号和测试 PDF 做上传/下载链路回归，避免修改现有业务数据。

### 文档与结构总结

- 2026-08-07 按当前工作区真实代码集中更新根 README、文档索引、项目结构、当前迭代计划、需求/系统/API/数据库设计、数据字典、Flyway 指南、知识库、仓库接管和部署更新说明；补充 V26 学习资料中心、PDF 版本/权限/批注、PDF.js worker 与 Node 22.13 构建边界，不新增文档。
- 2026-08-05 按当前 `main@6d6301e` 集中更新项目结构和阶段总结：同步根 README、文档索引、项目结构、当前迭代计划、需求/系统/API/数据库设计、数据字典、Flyway 指南、仓库接管与部署说明；不新增文档。
- 将数据库当前基线统一为 Flyway V1、V9–V25，补充 V25 反馈工单、不可修改时间线、工单附件、持久化站内通知和阅读位置，明确后续结构迁移从 V26 开始且生产版本必须以服务器 `flyway_schema_history` 为准。
- 将架构总结由旧 Vue/Pinia、MinIO、OpenAI 设想修正为当前 React/TypeScript/Vite、Spring Boot、MySQL、Redis、DeepSeek、OpenTalking、私有媒体卷和 Java 17 Docker 判题实现；补充工单状态机、权限、幂等、并发锁和通知接口说明。
- 固化文档维护约定：日常更新只持续登记本更新日志，不创建新的更新文档；项目结构、设计总览和阶段总结仅在用户明确要求时集中同步。

### 算法题目与反馈工单统一

- 以“面试管理”为交互基准统一算法题目与反馈工单的列表、详情布局，按钮层级、尺寸、焦点态、禁用态和异步加载反馈；反馈工单状态处理改用站内确认弹窗，移除浏览器原生 `prompt`。
- 算法题目筛选改用共享 `ResponsiveSelect`，行内编辑/启用/停用改用共享次按钮；原始数字 ID 改为 `ALG-0001` 样式展示，真实 ID 仍用于接口调用、搜索和路由。
- 修正管理端反馈工单标题栏结构：将“服务与支持”、主标题和说明文字统一收纳到同一内容块，恢复与“面试管理”一致的左对齐层级；同步补齐“处理”按钮的间距、焦点环和过渡样式。
- 将反馈工单桌面端“处理”操作改为共享 `Button` 组件，通过管理端统一的次按钮样式和导航逻辑进入工单详情；移动端工单卡片继续保留整卡链接导航。
- 统一候选人管理与算法题目管理中的“停用/启用”按钮：共享电源图标、间距、字号、阴影、加载动画和禁用反馈，保持两处状态切换操作一致。

### 管理端界面统一

- 以“面试管理”为交互基准，统一候选人、题库、题目、面试回顾、提示词版本、系统设置、算法题目、AI 调用审计、操作日志和工作台等管理页面的主按钮、次按钮、危险操作按钮、按钮尺寸、焦点态与禁用态；涉及异步操作时补充加载中状态和防重复点击逻辑。
- 将管理端删除、提示词版本激活/回滚等浏览器原生确认改为站内确认弹窗，统一取消、关闭、处理中和危险确认的交互反馈；保留题库可见范围、算法标签、面试时间轴等选择型控件的业务语义，并补充 `type`、`aria-pressed` 和键盘焦点反馈。
- 未更新项目结构总结文档；本次变更仅同步到更新日志。

### 新增

- 新增 Flyway V25 持久化“问题反馈工单”模块：创建 `feedback_ticket`、`feedback_ticket_activity`、`feedback_ticket_attachment`、`site_notification`、`feedback_ticket_read_state` 五张表，保存工单、不可修改时间线、附件授权、站内通知和用户阅读位置。
- 候选人端新增问题反馈列表、创建/编辑草稿、提交、详情、截图上传、时间线留言和增量刷新；管理端新增工单队列、筛选、详情、管理员转派、状态处理和回复。角色沿用 `CANDIDATE` 与 `ADMIN`，入口分别为 `/candidate/tickets` 与 `/admin/tickets`。
- 工单类型使用稳定代码 `INTERVIEW_FAILURE`、`FEATURE_SUGGESTION`、`BUG_REPORT`；状态支持 `DRAFT → PENDING → PROCESSING → RESOLVED/CLOSED`，并允许 `RESOLVED → PROCESSING`。关闭后由后端禁止留言和附件上传。
- 工单附件复用现有媒体存储、文件签名校验、SHA-256 与可选 ClamAV 扫描；每张截图最大 10MB、每个工单最多 5 张、总大小最大 30MB，访问权限改为基于工单参与者授权。
- 通知中心的活动通知改为读取后端 `site_notification` 持久化接口，新增通知列表、未读数量、单条已读和全部已读接口；工单提交、转派、留言、状态变更会通知对应候选人或管理员。

### 变更

- 管理端主菜单将“反馈工单”调整到“算法题目”之后，当前顺序为“工作台 → 面试管理 → 候选人 → 题库管理 → 算法题目 → 反馈工单 → 提示词版本 → AI 调用审计 → 操作日志 → 系统设置”。
- 管理端“反馈工单”页面统一接入管理端列表页的内容容器、中文标题区、字重、筛选卡片、间距和移动端工单卡片样式。
- 候选人端“问题反馈”列表、创建/编辑和详情路由统一接入候选人公共页面壳，恢复侧边菜单、顶部工具栏及移动端菜单；列表页采用与其他主菜单页面一致的中文栏目标题、标题字重、说明文案和右侧操作区，“返回面试大厅”与“提交反馈”改为并列主次按钮。候选人主菜单按使用优先级调整为“工作台 → 面试大厅 → 面试日历 → 算法练习 → 专项练习 → 能力报告 → 面试心得 → 问题反馈 → 账户中心”。
- 候选人端“问题反馈”列表页新增“返回面试大厅”入口，补充返回图标、键盘焦点样式和移动端适配。
- 工单状态变更、转派和留言统一在后端事务中锁定工单行；管理员状态/转派使用 `version` 乐观并发校验，留言使用 `clientRequestId` 幂等，避免并发关闭、重复点击和重复时间线记录。
- 管理端面试通知调整为先写入站内通知、再执行邮件同步，邮件服务未配置或暂时失败时不影响站内通知落库。
- 将当前有效项目迁移为 `D:\Ainterview` 下的独立源码副本：保留后端、React 前端、Flyway V1/V9–V24、OpenTalking、算法判题、Docker Compose、CI、测试与运维脚本；不迁移旧 `.git`、私密 `.env`、历史 Vue、归档、旧变更报告、本地媒体、依赖、日志和构建产物。新增 `docs/REPOSITORY_MIGRATION.md`，并同步更新 README、项目结构、数据库版本和本地路径。
- 停用讯飞虚拟人接入及 `avatar-skill`：讯飞后端客户端、Web SDK、知识库、专属报告和指南保留在原工作区归档且未迁入当前仓库；前端与后端运行入口统一保留 OpenTalking，浏览器朗读仅作为降级方案。

### 运维

- 完成本次工单模块验证：Maven 55 项测试全部通过，Flyway 已校验 19 个迁移且当前版本为 V25，React `npm run build` 通过，ESLint 为 0 错误（24 个 Hook/既有警告）；Docker backend/frontend 镜像重建并重新创建容器，首页与 OpenAPI 返回 200，MySQL、Redis、后端、前端容器均正常运行。
- 完成本地 API 闭环烟测并清理唯一标记的测试工单：候选人创建/提交、管理员查询/转派/受理/回复/解决/关闭、候选人回复与站内通知、关闭后留言拒绝均验证通过，测试数据清理后按工单号查询为 0。
- 在当前机器从原项目将 `.env` 原样复制到 `D:\Ainterview\.env`，SHA-256 校验一致；Compose 四项必填密钥均存在、非空且不是示例占位值，文件继续由 `.gitignore` 排除，未将任何密钥写入文档或仓库。
- 使用 Docker Desktop 29.6.2 / Docker Compose 5.3.1 完成 `docker compose config --quiet`、Java 17 判题镜像、后端和 React/Nginx 前端镜像构建；启动 MySQL、Redis、后端和前端，验证首页、OpenAPI、Actuator、Redis、Flyway V24 和容器状态。
- 启动 WSL OpenTalking mock（API 8210、Web 5280），验证直连及 `/opentalking` 代理的 health、runtime-config、avatars、ICE、会话创建/删除；在浏览器模拟面试中完成 Provider、WebRTC、TTS 朗读、媒体播放及朗读结束状态验证。
- 后端 Dockerfile 增加 BuildKit Maven 依赖缓存，避免源码变更后重复从零下载依赖；首次网络中断重试后镜像成功构建。

### 新增

- 原工作区曾建立 `docs/archive/` 并归档历史构建包、运行日志和未使用 SDK；这些归档载荷、历史 Vue、本地媒体和 IDE 配置均未迁入 `D:\Ainterview`，算法判题镜像源码因仍被当前 Docker Compose 使用而保留。
- 新增 Flyway V20 候选人面试心得：一场面试保存一份可持续编辑的复盘，包含自评分、信心程度、核心心得、表现亮点、待改进项和下一步计划；服务端仅允许本场候选人在面试结束后读写。
- 候选人端新增“面试心得”页面：提供心得数量、最新自评分、平均信心程度、AI 平均分、复盘完成度和待复盘列表；少于四次时使用评分卡片，四次及以上以实线自评分和虚线 AI 评分展示最近六次成长趋势，并保留可访问的文本明细。
- 面试大厅、报告生成等待页、评测报告、候选人工作台和主导航新增心得入口，面试结束后可直接记录或编辑本场复盘。
- 新增 Flyway V19 面试录制与时间轴：以 `interview_recording` 保存文字/语音/视频模式，以按题媒体分段和时间轴事件对齐题目、回答、追问、过渡与录制状态。
- 面试入场新增阻塞式方式选择：文字面试不录制；语音面试录制麦克风；视频面试按 1280×720、15fps 目标录制麦克风与摄像头。语音/视频录制开始后只允许完成当前题并自动顺序进入下一题。
- 管理端面试回顾新增受保护的按题音视频回放与事件时间轴，可从题目卡片加载相应录制分段，并点击回答/追问事件定位到对应媒体时刻。
- P0 面试恢复：新增 Flyway V18，持久化当前题号与进度更新时间；新增面试进度读写接口，由服务端按实际开始时间计算剩余时间，并返回当前题追问次数与可恢复 AI 任务。
- P0 答案可靠保存：面试页新增“草稿 / 保存中 / 已保存 / 失败 / 离线”状态、两次自动重试、联网自动补交、手动重试与未保存离开保护。
- P0 基线：新增 Spring Boot Actuator 与 Prometheus 指标端点；提供 AI 任务待处理、运行、失败数量及最老待处理任务时长指标。
- P0 基线：新增文件签名校验、原始文件名净化、可选的 ClamAV 同步扫描，以及媒体元数据/内容的所有者鉴权。
- P0 基线：新增 GitHub Actions 质量门禁，覆盖 Maven 测试、React 构建和 Docker Compose 配置校验。
- P0 基线：新增生产 MySQL 与媒体卷备份、备份完整性校验脚本，以及 P0 运维实施手册。
- 新增项目结构、云服务器端口与文件存放、技术知识库等维护文档。
- 新增 `docs/iterations/2026-07-30-iteration-summary.md`，集中记录本次迭代范围、Flyway 版本、验证结果、已知风险、生产验收重点与下一阶段优先级。
- 新增认证接口 Redis 限流：登录 10 次/分钟、注册 5 次/分钟、验证码发送 3 次/分钟且 20 次/天、验证码登录 10 次/分钟（按客户端 IP），阈值可通过 `AUTH_RATE_LIMIT_*` 环境变量调整；429 响应沿用统一请求包络与 `Retry-After` 头。
- 新增 JWT 密钥启动校验：`JWT_SECRET` 缺失、长度不足 32 或仍为默认/示例占位值时拒绝启动（`APP_REQUIRE_STRONG_JWT_SECRET=false` 仅限本地临时放行）。
- 前端新增路由级代码分割与 vendor 分包：首屏 JS 由约 840KB 降至约 226KB（gzip 246KB→70KB），面试房间等重型页面按需加载。
- 前端新增 ESLint（TypeScript + React Hooks）与 Prettier 配置及脚本，CI 质量门禁增加 `npm run lint`。
- 新增算法练习中心模块（Flyway V21–V22）：题库、难度与标签、示例/隐藏测试用例、代码提交与逐用例结果、用户进度、收藏、个人笔记、错题本、练习统计与管理员题目管理；与面试模块完全独立。
- 新增 Java 17 Docker 判题沙箱：单容器一次编译并循环执行全部用例，输入/输出通过 tar 归档（docker cp）传输，网络禁用、CPU/内存/进程数限制、超时强杀与输出截断，全程不使用 docker exec；判题任务通过 Redis Stream 异步消费。
- 算法练习前端：题库筛选、Monaco 编辑器（本地打包）、自定义输入运行、隐藏用例提交轮询、提交记录与详情、错题本、练习首页统计、管理端题目/测试用例维护。
- 算法题库扩充至 10 道题（Flyway V23）：新增数组最大值、回文判断、二分查找、有效括号、最大子数组和、爬楼梯、单词计数 7 道题，并为 A+B、两数之和、反转字符串补充标准答案与标签；新增 `solution_code` 标准答案字段，仅管理端接口可见与维护，用户端不返回。
- 算法题目模板统一（Flyway V24）：全部 10 道题的 `starter_code` 与题目文件的“用户答题模板”对齐，移除模板中直接给出的答案逻辑（A+B、反转字符串、两数之和等），用户打开题目时只能看到输入骨架与 TODO 提示；标准答案仅保留在 `solution_code` 供管理端查看。

### 变更

- 历史 Vue 前端（`frontend/`）正式标记为已归档，仅作维护参考；README 技术栈与系统设计文档同步对齐到 React 生产前端。
- 面试回顾页媒体播放重构为 video/audio 独立 ref，移除 `as any` 强转；浏览器语音识别补充最小类型声明并抽取为 `lib/browser-speech.ts` 共享。
- 判题运行与提交分离：运行仅执行用户自定义输入（`submit_type=RUN`，不计入统计），提交执行全部隐藏用例（`submit_type=SUBMIT`）并更新完成状态与练习统计。
- 题库列表查询优化：提交数/通过数由逐行相关子查询改为一次 `GROUP BY` 批量统计，减少每页查询次数；做题页新增“提交记录”标签页、示例输入一键填入、`Ctrl+Enter` 快速运行、编辑器深色主题适配；题库列表加载骨架屏与标签徽章化。
- 管理端面试回顾将选择题原始 JSON 改为结构化选项卡：题型统一显示为中文，单选/多选答案按“选项键 + 选项内容”展示，判断题使用“正确/错误”，未作答改为独立空状态；桌面端使用双列选项，手机端自动收敛为单列。
- 管理端模型服务卡片统一完成手机端适配：服务字段在窄屏使用上下布局，URL、模型名和密钥可安全换行，状态标签与操作按钮不再被裁切；新增/编辑服务弹窗在手机端改为底部面板。
- 前端核心业务界面完成手机端响应式适配：候选人端与管理端固定侧栏在窄屏改为可关闭抽屉，表格在手机端改为信息卡片，并统一补充触控尺寸、底部安全区、长文本换行和横向溢出保护。
- 通知中心在手机端改为全宽底部抽屉，增加遮罩与关闭按钮、页面滚动锁定、未读数量和状态点、独立滚动通知列表及安全区；桌面端继续使用右上角浮层。
- 候选人端页眉头像改为账户中心入口，手机端点击右上角头像可直接进入账户资料与安全设置。
- 候选人面试大厅重构手机端卡片层级与操作区，状态标签、时间信息和主操作不再被裁切；“创建模拟练习”改为移动端底部抽屉，内容区独立滚动，关闭和底部操作始终可见。
- 能力趋势页面为手机端提供独立的最近六场折线图，保留真实场次、分数和日期且不再横向滚动；四维能力雷达图和分项数值列表同步限制在卡片边界内，支持 320px 窄屏。
- 面试房间在手机端优先展示当前题目与作答区域，题目目录后置，录制媒体、切题控制和结束确认弹窗改为窄屏布局；面试日历改为原生七列移动网格。
- 心得趋势同时展示候选人主观自评分与已发布 AI 报告评分，不在心得表重复保存 AI 分数；页面支持桌面和移动端布局、空状态、加载、错误重试、字符计数与键盘关闭弹窗。
- 候选人工作台升级为行动优先的训练看板：优先展示可继续或待开始的面试，基于最近报告生成训练重点，并补充真实本周练习、待办、报告、最新能力分、最近六次趋势、近期记录和日历/专项练习/能力报告快捷入口；少于四份报告时改用数值卡片，避免用折线图放大稀疏数据。
- 报告 PDF 打印版重构为单页 A4：压缩无效留白、保留摘要/可信度提示/四维评分/能力画像/优势/待提升项/行动建议，并在打印前按实际内容高度动态缩放；打印容器固定为 A4 可用区域，避免内容溢出到第二页或产生空白页。
- 管理员工作台升级为行动型运营看板：基于真实面试与 AI Provider 数据新增今日安排、待处理事项、近 7 天趋势、系统运行概览、快捷入口和手动刷新，并优化无数据、部分接口失败及响应式展示状态。
- 前端品牌名称由 `InterviewOS` 统一更新为 `AInterview`；登录页主标题调整为“每一面，都算数。”；全站标题与副标题（`h1`–`h6`），以及“管理工作台”等使用 `text-[var(--accent)]` 的文本栏目标题统一使用 `SourceHanSerifCN SemiBold` 字体族；文本栏目标题颜色统一为 `#7d4929`，其余正文、强调文字、按钮、标签、图标和表单内容保持默认样式。
- 大模型调用统一优先读取管理端“已启用且文字默认”的数据库 Provider，配置保存后无需重启应用即可用于提问、追问、评分和报告生成；未找到可用数据库配置时仍兼容 `DEEPSEEK_API_KEY` 环境变量。
- 统一候选人端、管理端、登录页、面试页与公共组件的界面文案，页眉、状态、加载、空状态、错误提示和操作按钮改为正式、简洁的中文表达。
- 前端图标统一沿用 Lucide 矢量图标库，并按“大模型、候选人、报告、训练计划、录制、虚拟人”等业务语义重新映射；移除跨场景重复使用的装饰性星光图标，为图标按钮补充无障碍名称。
- 进入面试房间后不再自动调用浏览器朗读题目；文字模式仅在用户点击“重读题目”时使用浏览器朗读，启动虚拟人后由虚拟人朗读。语音和视频模式保留相同虚拟人能力，但虚拟人实时媒体流不复制进候选人录制文件。
- 媒体 SHA-256 改为流式计算，避免大录制文件一次性读入后端内存；单题录制上传默认上限提高并统一为 100MB，仍经过 MIME/文件签名、所有者权限和可选 ClamAV 校验。
- 模拟面试追问携带最近 8 条同题对话和历史追问；DeepSeek 与 OpenTalking 输出增加单问题、题目边界和重复度校验，未通过时仅重试一次，仍失败则使用确定性追问兜底。
- 面试房间的题号与倒计时改为服务端恢复并每 15 秒校准；刷新后会继续轮询尚未完成的追问任务，不再重新创建同一轮任务。
- 手动结束面试会先保存当前回答；答案仍在保存或等待重试时禁止切题和结束，防止旧请求覆盖新回答。
- Actuator 监听容器内部 `8081` 端口；不通过 Nginx 或宿主机端口发布。
- 上传的 PDF、图片、音视频改为“声明 MIME 类型与文件签名必须匹配”；下载使用附件方式返回，降低浏览器直接执行不可信内容的风险。
- 模拟面试在达到当前题目追问上限后，使用确定性的自然收尾并进入下一题，不再让模型生成可能夹带新问题的过渡语。
- 模拟面试每道主观题的追问上限由随机 `2–5` 次统一为固定 `3` 次，使刷新前后、验收口径和候选人预期保持一致。
- 新增 Flyway V17：优化 `simulation.follow_up` 内置提示词；仅升级仍在使用系统初始版本的实例，不覆盖管理员已自定义的启用版本。

### 修复

- 修复学习资料 PDF 阅读器在 Docker/Nginx 环境中加载 PDF.js worker 失败的问题：为前端 Nginx 显式配置 `.mjs` 为 `application/javascript` 并返回 404 以外的有效静态文件，避免浏览器因收到 `application/octet-stream` 而拒绝动态导入 `pdf.worker.min-*.mjs`；已重建并重启 `ainterview-frontend-1` 验证 worker 文件存在且响应为 `200`、JavaScript MIME 类型。
- 修复 OpenTalking 偶发“虚拟人声音与对话文字不一致”：模拟面试不再把追问提示词交给 OpenTalking 边生成边播，而是由后端生成并完成边界/重复度校验，再将页面最终展示的同一字符串以只朗读模式交给 OpenTalking；选择题讲评、收尾语和自由面试问题同样统一为只朗读。新增会话级朗读序列，新的朗读会先中断旧音频并淘汰尚未提交的过期请求，候选人提交回答时立即停止上一段声音，避免旧语音排队后覆盖当前文字。
- 修复 Docker Desktop 同时为 `host.docker.internal` 写入 IPv4/IPv6、但 WSL OpenTalking 仅能通过 IPv4 到达时，Nginx 每次朗读先连接不可达 IPv6 并产生间歇延迟、502 或无声的问题；前端容器启动时从 `/etc/hosts` 提取上游的首个 IPv4 地址再生成 Nginx 配置，不依赖硬编码网关 IP，迁移到其他 Docker 主机时仍可沿用 `OPENTALKING_UPSTREAM`。移除未被镜像引用且仍指向旧 8000 端口的 `frontend-react/nginx/default.conf`，将 `*.sh`/`*.envsh` 固定为 LF 行尾，并允许 Markdown 使用双空格强制换行，避免 Windows 检出后破坏容器脚本或产生无效空白告警。
- 修复管理端 OpenTalking Provider 使用浏览器相对地址 `/opentalking` 时，服务端“测试”按钮因 URL 缺少 scheme 而误报失败的问题：后端通过 `OPENTALKING_UPSTREAM` 解析健康检查地址，Compose 为后端补充上游变量和 `host.docker.internal:host-gateway` 映射，并新增 3 项 URL 解析回归测试。
- 修复面试房间已收到 OpenTalking `speech.ended`、服务端会话也已恢复 `ready`，但状态仍长期显示“朗读中”的问题；事件结束和 45 秒兜底现在都会释放朗读锁并恢复“已就绪”，避免造成文字与语音仍不同步的误判。
- 修复手机端长列表卡片已加载且可以点击、但内容保持透明不可见的问题：公共卡片的视口动画由固定 12% 可见阈值改为任意相交即显示，管理端面试数据卡片首屏禁用初始透明动画，避免超长列表永远无法达到动画触发比例。
- 修复管理员登录后首次进入手机端“面试管理”时因候选人、题库或报告辅助接口任一请求较慢/失败而不显示面试列表的问题；面试列表现在独立优先加载，辅助数据分项容错，页面恢复到前台会静默刷新，并提供明确的重试按钮。
- 修复面试心得编辑弹窗点击“关闭”或“取消”后，被尚未清除的 `interviewId` 查询参数立即再次打开的问题；增加查询参数消费标记，关闭、取消和 Esc 现在均可稳定退出弹窗。
- 修复候选人工作台“本周练习”统计了全部历史练习、任一接口失败导致整页数据不可用的问题；三个数据源改为独立降级并支持重试。候选人端固定侧栏改为桌面侧栏与移动抽屉自适应布局，消除 375px 窄屏横向溢出，并为页面底部预留悬浮助手安全间距。
- 修复新模拟面试报告生成耗时过长：报告类 AI 任务改为高优先级并使用有界工作池并发处理；同一报告内的主观题由逐题串行评分改为最多 3 路并行评分，同时批量读取题目与回答并限制模型输出长度。5 道主观题的模型等待链路由“5 次评分依次等待 + 1 次总结”缩短为“最多 2 批评分 + 1 次总结”，不改变原评分提示词、分数校准和报告字段。
- 修复首次选择视频面试后摄像头已显示“开启”但预览黑屏的问题：避免录制模式状态切换触发页面清理逻辑并提前停止新媒体流，同时在预览元素显示、媒体元数据就绪和视频轨道解除静音后重新绑定并显式播放画面。
- 修复管理端已经保存 DeepSeek API Key，但视频/语音/文字面试生成报告仍只检查 `DEEPSEEK_API_KEY`，导致错误提示“未配置 DEEPSEEK_API_KEY”的问题。
- 修复刷新页面后倒计时重置、题目位置只存在当前浏览器，以及已提交 AI 追问任务被重复创建的问题。
- 修复网络中断时发送内容可能被清空且用户不知道保存结果的问题；失败内容现在保留在本机并可恢复提交。
- 追问请求、数字人 NLP 事件与当前题目绑定令牌；切题、收尾或结束面试后迟到的追问结果会被忽略，避免串入下一题。
- 题目朗读、追问和收尾消息分别标记，收尾语不再被计入追问次数；AI 处理中禁止切题，避免并发状态错位。
- 模拟面试追问接口改为使用服务端题目快照，并在后端强制最多追问 3 次；同一题同一轮追问使用幂等任务键，避免旧页面、重复提交或接口直调生成重复追问。

### 待执行（需要云服务器/云账号权限）

- 为 ECS 绑定最小权限 RAM 角色，将生产密钥迁入 KMS Secrets Manager，并以 `/run/secrets/` 配置树提供给容器。
- 收紧阿里云安全组：SSH 仅固定运维来源；确认 3306、6379、8080、8210、5280 不对公网开放；按实际 coturn 配置放行媒体 UDP 端口。
- 部署 ClamAV 到私有网络并设置 `CLAMAV_REQUIRED=true`；完成恶意样本上传验证。
- 配置 Prometheus、Grafana、Alertmanager 与外部 HTTP/WebRTC 合成探针；设置 P1/P2 告警路由。
- 将备份上传到加密且访问受控的 OSS，启用保留策略，并完成首次隔离恢复演练。

## 2026-07-30 — 文档与数据库迁移整理

### 新增

- 建立当前迭代计划、技术知识库、数据库迁移指南、项目结构与云服务器维护说明。

### 变更

- 明确后端 `src/main/resources/db/migration/` 为唯一有效 Flyway 迁移源。
- 将旧版手工初始化 SQL、历史迁移副本和旧执行脚本归档到原工作区；当前干净仓库不携带这些归档副本。
