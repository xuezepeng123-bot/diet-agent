# Diet Agent · 饮食推荐 Agent

个人项目。用户用自然语言描述想吃什么，服务识别餐次和口味，在信息不足时追问，条件齐全后从 MySQL 菜单中推荐餐食。同一会话可以继续补充偏好或要求「换一批」。

**技术栈** Java 21 · Spring Boot · MyBatis · MySQL · AgentScope · 通义千问 · Maven

## 已实现功能

- **多轮澄清与会话持久化**。通过 `sessionId` 恢复会话，合并新一轮提取的偏好，再决定追问还是查菜。 [会话服务](https://github.com/xuezepeng123-bot/diet-agent/blob/develop/src/main/java/com/diet/learning/MealConversationService.java)
- **结构化偏好与规则兜底**。校验千问返回的 JSON 及字段可选值，模型未配置、超时或输出校验失败时回退到关键词匹配。 [偏好提取](https://github.com/xuezepeng123-bot/diet-agent/blob/develop/src/main/java/com/diet/learning/MealPreferenceExtractor.java)
- **餐食查询与换一批**。MyBatis 按餐次和口味查询，在「换一批」时排除同一会话已推荐的菜品。 [餐食查询](https://github.com/xuezepeng123-bot/diet-agent/blob/develop/src/main/resources/mapper/MealMapper.xml)
- **Agent 工具调用**。独立的 ReAct 接口注册 `search_meals`，由模型选择调用，Agent 根据工具结果回复。 [Agent 接口](https://github.com/xuezepeng123-bot/diet-agent/blob/develop/src/main/java/com/diet/learning/MealAgentController.java)
- **对话 Trace**。会话接口返回 `X-Trace-Id`，记录请求入口、换批分支、结束状态或异常和耗时，支持按 ID 查询。 [Trace 服务](https://github.com/xuezepeng123-bot/diet-agent/blob/develop/src/main/java/com/diet/learning/AgentTraceService.java)

## 代码与分支

| 分支 | 内容 |
| --- | --- |
| `main` | 项目首页与代码导航 |
| [`develop`](https://github.com/xuezepeng123-bot/diet-agent/tree/develop) | 个人项目的后端源码、数据库脚本、接口测试及运行文档 |
| [`docs/readme-fix`](https://github.com/xuezepeng123-bot/diet-agent/pull/1) | 保留早期文档修订与合并记录 |

本地启动和「追问 → 推荐 → 换一批 → 查询 Trace」的操作步骤见 [运行文档](https://github.com/xuezepeng123-bot/diet-agent/blob/develop/README.md#本地运行)。
