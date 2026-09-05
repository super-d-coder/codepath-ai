👩🏻‍🏫敲黑板，这里有三个关键设计：

1）@Annotations.Schema **注解 — 工具的"说明书"**

这个注解是 LLM 能识别工具的起点。name = "command" 告诉 LLM 参数名叫 command，description 告诉 LLM 这个参数是干什么的。LLM 正是根据这个描述来决定"要不要调用这个工具"和"传什么参数"。

2）InheritableThreadLocal **— 跨线程传递终端会话**

为什么用 InheritableThreadLocal 而不是普通 ThreadLocal？因为 Google ADK 内部用 RxJava 响应式流，工具执行可能在子线程中触发。InheritableThreadLocal 保证子线程能继承父线程设置的终端会话 ID。

3）返回 Map<String, Object> **— 结构化结果给 LLM**

工具返回的不是纯文本，而是包含 success、output、suggestion 的结构化 Map。LLM 拿到后能精确判断命令是否成功，失败时还能参考 suggestion 给用户建议。这比返回一坨纯文本让 LLM 自己猜要好得多。

错误分析与建议

工具内置了 isExecutionSuccessful() 和 analyzeError() 两个辅助方法：

D:\Projects\JAVA\codepath-ai\codepath-ai-domain\src\main\java\com\superd\domain\agent\service\armory\matter\plugin\MyTestPlugin.java:
7 个回调方法覆盖了 Agent 执行的完整生命周期：
用户输入 → onUserMessageCallback     ① 用户消息进入
→ beforeAgentCallback        ② Agent 启动
→ beforeModelCallback        ③ LLM 请求（含工具列表）
→ [LLM 推理]                 
→ afterModelCallback         ④ LLM 响应（含 Token 消耗）
→ beforeToolCallback         ⑤ 工具调用开始（含参数）
→ [工具执行]                 
→ afterToolCallback          ⑥ 工具调用完成（含结果）
→ [可能多轮循环 ③→⑥]         
→ afterAgentCallback         ⑦ Agent 完成

另外还有一个 MyLogPlugin，直接继承 ADK 内置的 LoggingPlugin，提供标准日志输出：

两个插件可以同时使用——MyTestPlugin 提供结构化日志（方便定位问题），MyLogPlugin 提供 ADK 标准日志（方便对照源码）。如果你想扩展点啥，可以在 MyLogPlugin 扩展下，监听你想监听的数据。

Agent 配置（ssh-agent.yml）：
👩🏻‍🏫敲黑板，instruction 是整个 Agent 的"灵魂"——它告诉大模型"你是谁、你能做什么、你应该怎么做"。这里有几个关键点：

最高优先级规则：明确告诉 LLM "必须直接调用工具"，禁止只描述不执行。因为有些模型会"偷懒"——只说"你可以运行 xxx 命令"但不实际调用 function call。

工作流程示例：给出"安装 docker"的完整步骤（检测系统→执行安装→验证→报告），引导 LLM 形成多步的习惯。

错误处理策略：告诉 LLM 遇到 permission denied **建议用** sudo**，遇到** command not found **建议安装软件——和工具里的** analyzeError() **形成双重保障。**

安全规则：危险命令拦截、敏感信息保护——Agent 安全无。


对话服务（ChatService）— 终端会话绑定
ChatService.handleMessageStream 是前端调用 AI 对话的入口，本节新增了 terminalSessionId 参数，绑定到 ThreadLocal 供工具使用：
@Override
public Flowable<Event> handleMessageStream(String agentId, String userId,
String sessionId, String message, String terminalSessionId) {

    AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
    InMemoryRunner runner = aiAgentRegisterVO.getRunner();

    // ★ 设置终端会话ID到ThreadLocal，供工具使用
    if (terminalSessionId != null && !terminalSessionId.isEmpty()) {
        log.info("设置终端会话ID: {}", terminalSessionId);
        SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
    }

    Content userMsg = Content.fromParts(Part.fromText(message));
    return runner.runAsync(userId, sessionId, userMsg);
}
terminalSessionId **是第 1-7 节终端会话的 ID，通过** ThreadLocal **传递给** SshExecuteAdkTool**。**

runner.runAsync() **返回** Flowable<Event> **响应式流，前端通过 SSE（Server-Sent Events）接收流式输出。**

整个链路：前端发消息 → AgentServiceController.chatStream **→** ChatService.handleMessageStream **→** InMemoryRunner.runAsync **→** LlmAgent.runAsyncImpl **→ ReAct 循环。**

Trigger 层（AgentServiceController）:
ResponseBodyEmitter **是 Spring MVC 的流式响应接口，配合** subscribe **把 RxJava 的** Flowable<Event> **转为 HTTP SSE 流。**

ChatRequestDTO **新增了** terminalSessionId **字段，前端在打开终端后把会话 ID 传过来。**

3 分钟超时保护，防止长时间无响应挂起。