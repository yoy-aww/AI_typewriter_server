# AI Typewriter Server

AI 打字机后端服务，基于 Spring Boot 构建，提供与 DeepSeek API 的流式对话能力。

## 功能特性

- **流式 SSE 响应** — 基于 Server-Sent Events 实现逐 token 推送，前端可实时显示打字机效果
- **多轮对话管理** — 服务端按 `sessionId` 维护对话上下文，自动裁剪历史消息
- **异步非阻塞** — 使用 Spring `@Async` + 独立线程池处理 SSE 任务
- **配置灵活** — 支持自定义模型、超时、最大 token 数、对话轮数等

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 8+ |
| Spring Boot | 2.7.18 |
| OkHttp | 4.12.0 |
| Jackson | 2.13.5 |
| Maven | 3.8+ |

## 环境要求

- JDK 8 或更高版本
- Maven 3.8+
- DeepSeek API Key（[申请地址](https://platform.deepseek.com/)）

## 快速启动

### 1. 设置环境变量

```bash
# Windows (PowerShell)
$env:DEEPSEEK_API_KEY="your-api-key-here"

# Windows (CMD)
set DEEPSEEK_API_KEY=your-api-key-here

# Linux / macOS
export DEEPSEEK_API_KEY=your-api-key-here
```

### 2. 构建并运行

```bash
# 编译
mvn clean compile

# 运行
mvn spring-boot:run
```

服务启动后默认监听 `http://localhost:8080`。

### 3. 验证服务

```bash
curl http://localhost:8080/api/health
```

预期返回：
```json
{"status":"UP","service":"AI Typewriter Server","timestamp":1710000000000}
```

## API 文档

### POST /api/chat/stream — 流式聊天

**请求格式**：`Content-Type: application/json`

```json
{
  "messages": [
    {
      "role": "user",
      "content": "你好，请介绍一下你自己"
    }
  ],
  "sessionId": "optional-session-id",
  "temperature": 0.7
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `messages` | `ChatMessage[]` | 是 | 用户消息列表（只需发送最新消息，服务端自动合并历史） |
| `sessionId` | `string` | 否 | 会话标识，不传则服务端自动生成 |
| `temperature` | `number` | 否 | 生成温度 0~2（默认使用配置值） |

**ChatMessage 对象**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `role` | `string` | 消息角色：`user`、`assistant` |
| `content` | `string` | 消息内容 |

**响应格式**：`Content-Type: text/event-stream`

SSE 事件流，每个事件名为 `message`，数据格式如下：

```json
// 流式片段（isEnd: false）
{
  "content": "你好",
  "sessionId": "xxx",
  "isEnd": false
}

// 结束事件（isEnd: true）
{
  "content": "",
  "sessionId": "xxx",
  "isEnd": true
}

// 错误事件（事件名: error）
{
  "content": "",
  "sessionId": "xxx",
  "isEnd": true,
  "error": "错误描述信息"
}
```

**示例：使用 curl 测试流式聊天**

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "用一句话介绍 Spring Boot"}],
    "temperature": 0.7
  }'
```

### GET /api/health — 健康检查

**响应**：
```json
{
  "status": "UP",
  "service": "AI Typewriter Server",
  "timestamp": 1710000000000
}
```

## 配置说明

所有配置项在 `application.yml` 中，支持通过环境变量覆盖：

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|----------|--------|------|
| `deepseek.api-key` | `DEEPSEEK_API_KEY` | (必填) | DeepSeek API 密钥 |
| `deepseek.api-url` | — | `https://api.deepseek.com/v1/chat/completions` | API 端点 |
| `deepseek.model` | — | `deepseek-chat` | 模型名称 |
| `deepseek.max-tokens` | — | `2048` | 最大生成 token 数 |
| `deepseek.max-conversation-rounds` | — | `20` | 服务端保留的最大对话轮数 |
| `deepseek.connect-timeout` | — | `30` | HTTP 连接超时（秒） |
| `deepseek.read-timeout` | — | `120` | HTTP 读取超时（秒） |
| `server.port` | — | `8080` | 服务端口 |

## 项目结构

```
src/main/java/com/typewriter/
├── TypewriterApplication.java          # 应用启动类
├── config/
│   ├── AsyncConfig.java                # SSE 异步线程池配置
│   └── DeepSeekConfig.java             # DeepSeek API 配置属性
├── controller/
│   └── ChatController.java             # 聊天 SSE 接口控制器
├── model/
│   ├── ChatMessage.java                # 聊天消息体
│   ├── ChatRequest.java                # 聊天请求 DTO
│   └── SSEEvent.java                   # SSE 事件数据模型
└── service/
    ├── ConversationManager.java        # 会话历史管理器
    ├── DeepSeekService.java            # DeepSeek 服务接口
    └── impl/
        └── DeepSeekServiceImpl.java    # DeepSeek 服务实现（OkHttp 流式调用）
```

## 与前端集成

前端可基于 `EventSource` API 或 `fetch` 流式读取实现打字机效果：

```javascript
// 使用 EventSource (需通过 POST + fetch 流式读取)
const response = await fetch('/api/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    messages: [{ role: 'user', content: '你好' }]
  })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  const text = decoder.decode(value, { stream: true });
  // 解析 SSE 格式的文本，提取 content 字段
  console.log(text);
}
```

## 许可证

MIT License