# Diet Agent · 饮食推荐 Agent

个人项目。用户用自然语言描述想吃什么，服务识别餐次和口味，在信息不足时追问，条件齐全后从 MySQL 菜单中推荐餐食。同一会话可以继续补充偏好或要求「换一批」。

**技术栈** Java 21 · Spring Boot · MyBatis · MySQL · AgentScope · 通义千问 · Maven

## 已实现功能

- **多轮澄清与会话持久化**。通过 `sessionId` 恢复会话，合并新一轮提取的偏好，再决定追问还是查菜。 [会话服务](src/main/java/com/diet/learning/MealConversationService.java)
- **对话编排与回复 Agent**。编排层调用偏好提取和会话服务，澄清 Worker 确定缺失条件，推荐服务筛选菜品。启用回复 Agent 后，可根据业务结果改写回复，异常时使用模板。 [编排层](src/main/java/com/diet/learning/MealConversationOrchestrator.java)
- **六类意图路由**。识别餐食推荐、信息不足、调整推荐、多餐规划、健康风险和其他输入。规则可直接拦截健康风险，推荐结合会话中的餐次和口味决定是否追问。 [意图路由](src/main/java/com/diet/learning/MealIntentRouter.java)
- **结构化偏好与规则兜底**。校验千问返回的 JSON 及字段可选值，模型未配置、超时或输出校验失败时回退到关键词匹配。 [偏好提取](src/main/java/com/diet/learning/MealPreferenceExtractor.java)
- **餐食查询与换一批**。MyBatis 按餐次和口味查询，在「换一批」时排除同一会话已推荐的菜品。 [餐食查询](src/main/resources/mapper/MealMapper.xml)
- **Agent 工具调用**。独立的 ReAct 接口注册 `search_meals`，由模型选择调用，Agent 根据工具结果回复。 [Agent 接口](src/main/java/com/diet/learning/MealAgentController.java)
- **对话 Trace**。会话接口返回 `X-Trace-Id`，记录路由、偏好提取方式、兜底原因、提取耗时和推荐结果，支持按 ID 查询。 [Trace 服务](src/main/java/com/diet/learning/AgentTraceService.java)
- **Trace 人工标注**。按 `traceId` 保存期望结果和备注，供后续离线评估与实际结果对比。
- **用户反馈**。用户可对单轮对话标记「有帮助」或「没帮助」，评估报告汇总样本的反馈分布。
- **离线规则评估**。批量对比已标注 Trace 的实际结果和人工预期，返回准确率、平均耗时和待复查样本。
- **LLM Judge**。单独触发千问评分，按百分制评价回复与结果的一致性和表达清晰度，并保存评分理由。

## 一轮请求怎么走

1. `MealConversationController` 接收消息，校验参数并生成本轮 Trace ID。
2. `MealConversationOrchestrator` 根据关键词、`MealPreferenceExtractor` 的模型输出和已有偏好确定路由，分别处理调换推荐、规划三餐和引导回复。
3. `MealConversationService` 在事务内合并偏好。`MealClarificationWorker` 确定是否需要追问，条件齐全时由 `RecommendationService` 查询餐食并返回候选，会话服务保存推荐历史。
4. 会话事务提交后，`MealReplyAgent` 可以根据结果组织回复。业务代码确定状态和菜品列表。
5. `AgentTraceService` 保存本轮事件，调用方通过响应头中的 Trace ID 查看处理结果。

| 接口 | 功能 |
| --- | --- |
| `POST /api/conversations/recommendations` | 多轮澄清、推荐与换批 |
| `POST /api/agent/recommendations` | 千问 ReAct 工具调用 |
| `GET /api/meals` | 按餐次和口味查询菜单 |
| `GET /api/traces/{traceId}` | 查询单轮会话 Trace |
| `PUT /api/traces/{traceId}/label` | 新增或覆盖人工标注 |
| `GET /api/traces/{traceId}/label` | 查询该轮的人工标注 |
| `PUT /api/traces/{traceId}/feedback` | 新增或更新该轮的用户反馈 |
| `GET /api/traces/{traceId}/feedback` | 查询该轮的用户反馈 |
| `GET /api/evaluations/traces` | 批量评估有人工标注的 Trace |
| `POST /api/evaluations/traces/{traceId}/judge` | 调用评分模型并保存单轮评分 |
| `GET /api/evaluations/traces/{traceId}/judge` | 查询该轮已保存的模型评分 |
| `GET /api/health` | 检查 HTTP 服务是否启动 |

## 本地运行

需要 Java 21、Maven 和 MySQL。默认连接 `127.0.0.1:3306/diet_agent_learning`，使用 `diet_app` 账号。可以通过 `SPRING_DATASOURCE_URL` 和 `SPRING_DATASOURCE_USERNAME` 覆盖连接地址和账号。

在空数据库中建立 `meal_item` 并录入演示数据。

```sql
CREATE TABLE meal_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    meal_time VARCHAR(20) NOT NULL,
    taste VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO meal_item (name, meal_time, taste) VALUES
    ('清蒸鲈鱼', '晚餐', '清淡'),
    ('冬瓜虾仁', '晚餐', '清淡');
```

在同一个数据库中依次执行 [会话表](sql/meal_conversation.sql)、[推荐历史字段](sql/add_last_recommendation_ids.sql)、[Trace 表](sql/meal_agent_trace.sql)、[Trace 标注表](sql/meal_agent_trace_label.sql)、[Trace 反馈表](sql/meal_agent_trace_feedback.sql)和 [Trace 评分表](sql/meal_agent_trace_judgment.sql)脚本。升级时执行现有数据库缺少的脚本。应用账号应能读取 `meal_item`、读写 `meal_conversation`、查询和插入 `meal_agent_trace`，并查询、插入和更新 `meal_agent_trace_label`、`meal_agent_trace_feedback` 与 `meal_agent_trace_judgment`。

在运行 Maven 的终端设置 `DIET_DB_PASSWORD` 环境变量。如果要体验千问偏好提取、ReAct 工具调用或 LLM Judge，再设置 `DASHSCOPE_API_KEY` 和 `DASHSCOPE_BASE_URL`。

```sh
mvn spring-boot:run
```

默认使用模板回复。设置 `DIET_AGENT_REPLY_ENABLED=true` 并配置千问后，多轮接口每轮可以额外调用一次回复 Agent。未配置模型、调用失败或输出格式异常时使用模板。`REQUEST_FINISHED` 的 `replySource` 和 `replyDurationMs` 记录回复来源与耗时。

## 接口演示

第一轮只提供餐次，预期返回 `ASK`（等待补充口味）及 `sessionId`。

```sh
curl -i http://127.0.0.1:8080/api/conversations/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"message":"我想吃晚餐"}'
```

沿用返回的 `sessionId`，依次发送「清淡」和「换一批」。使用上面的演示数据，第二轮返回清蒸鲈鱼，换批返回冬瓜虾仁。用任一轮响应头的 `X-Trace-Id` 查询 `GET /api/traces/{traceId}`。

普通对话的 Trace 包含 `PREFERENCES_EXTRACTED`。查看 `details.extraction.source` 可以区分千问提取（`MODEL`）和关键词匹配（`KEYWORDS`）。关键词兜底会记录 `fallbackReason`，例如 `MODEL_NOT_CONFIGURED` 或 `MODEL_FAILURE`。`preferences` 中只记录本轮抽到的餐次和口味。换批直接沿用会话中的偏好，Trace 记录 `CHANGE_BATCH` 路由。`REQUEST_FINISHED` 的 `mealCount` 是本轮返回的餐食数量。

### 查看意图路由

- 「帮我规划三餐，清淡一点」走 `MEAL_PLAN`，按早、午、晚顺序各取至多一道菜，缺少候选的餐次会在回复中指出。三餐规划直接返回当次方案，会话保留单餐推荐历史。
- 「再来一批」走 `MEAL_ADJUST`，复用会话的餐次和口味，排除已推荐的菜品。
- 诊疗类需求走 `HEALTH_RISK`，闲聊等无关输入走 `OTHER`，返回固定引导文案。

`ROUTE_SELECTED` 记录 `intent`、`route` 和 `reason`。模型置信度低于 `0.55` 且已有完整餐食条件时，先提示用户重新确认条件。条件不足时沿用已有的逐项追问。

### 标注一轮对话

用第一轮响应头的 `X-Trace-Id` 替换示例中的 ID。`expectedStatus` 可填 `ASK`（追问）、`RECOMMEND`（推荐）或 `NO_MATCH`（无匹配）。`note` 可省略，最多 500 字符。每条 Trace 保留一份标注，再次 `PUT` 会覆盖原标注。

```sh
TRACE_ID='<第一轮 X-Trace-Id>'
curl -i -X PUT "http://127.0.0.1:8080/api/traces/$TRACE_ID/label" \
  -H 'Content-Type: application/json' \
  -d '{"expectedStatus":"ASK","note":"缺少口味，应该先追问"}'
curl -sS "http://127.0.0.1:8080/api/traces/$TRACE_ID/label"
```

保存成功返回 `204`。用 Trace 的 `REQUEST_FINISHED.details.resultStatus` 对比标注中的 `expectedStatus`，即可判断这一轮的结果是否符合预期。

### 提交一轮对话的反馈

用响应头的 `X-Trace-Id` 替换示例中的 ID。`helpful` 填 `true` 表示有帮助，填 `false` 表示没帮助。每条 Trace 保留最新一份反馈，再次 `PUT` 可更新。

```sh
TRACE_ID='<要评价的 X-Trace-Id>'
curl -i -X PUT "http://127.0.0.1:8080/api/traces/$TRACE_ID/feedback" \
  -H 'Content-Type: application/json' \
  -d '{"helpful":true}'
curl -sS "http://127.0.0.1:8080/api/traces/$TRACE_ID/feedback"
```

### 批量评估标注样本

```sh
curl -sS 'http://127.0.0.1:8080/api/evaluations/traces?limit=20'
```

默认按创建时间从新到旧取最近 100 条已标注 Trace，可用 `limit` 指定 1 到 500 条，也可用 `sessionId` 限定会话。评估读取已保存的结果，无需再次请求模型。

- `accuracyPercent` 用命中数量除以可评分样本数，再乘以 100。明确记录了请求失败的样本计入分母，缺少有效结果事件的样本记为无法评分。没有可评分样本时返回 `null`。
- `averageDurationMs` 是所选样本的平均请求处理耗时。`issues` 包含结果不符、请求失败和缺少有效结果的样本，可用其中的 `traceId` 查询原始事件。
- `feedback` 汇总本批已标注样本中的有帮助、没帮助和未反馈数量。`helpfulPercent` 以已提交反馈的样本为分母，整批都未收到反馈时返回 `null`。

### 用模型评价一轮回复

先发起新一轮对话，取响应头的 `X-Trace-Id`。评分模型只接收该轮保存的回复、结果状态和菜品数量。评分上限为 100 分，结果一致性占 60 分，表达清晰度占 40 分。

```sh
TRACE_ID='<本轮 X-Trace-Id>'
curl -sS -X POST "http://127.0.0.1:8080/api/evaluations/traces/$TRACE_ID/judge"
curl -sS "http://127.0.0.1:8080/api/evaluations/traces/$TRACE_ID/judge"
```

`POST` 每次调用模型，成功时覆盖上次评分。`GET` 读取保存的分数、理由、模型名和评分时间。缺少回复记录的历史 Trace 返回 `422`，模型未配置返回 `503`，模型调用失败或输出无效返回 `502`。

## 计划扩展

- **餐食查询 RPC（gRPC）**。将 `SearchMeals` 查询拆为独立服务，推荐流程和 `search_meals` 共用同一个 RPC 客户端。验收覆盖推荐、换批、空结果和远程服务不可用四种情况。
