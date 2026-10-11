package com.superd.trigger.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.superd.api.IAgentService;
import com.superd.api.dto.agent.AiAgentConfigResponseDTO;
import com.superd.api.dto.agent.ChatRequestDTO;
import com.superd.api.dto.agent.ChatResponseDTO;
import com.superd.api.dto.agent.CreateSessionRequestDTO;
import com.superd.api.dto.agent.CreateSessionResponseDTO;
import com.superd.api.response.Response;
import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.superd.domain.agent.service.IChatService;
import com.superd.domain.agent.service.armory.matter.tools.SshExecuteAdkTool;
import com.superd.types.enums.ResponseCode;
import com.superd.types.exception.AppException;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AgentServiceController implements IAgentService {

    @Resource
    private IChatService chatService;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            log.info("查询智能体配置列表");

            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询智能体配置列表失败", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            log.info("创建会话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("智能体对话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            List<String> messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage());

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            responseDTO.setContent(String.join("\n", messages));

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("智能体对话异常", e);
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("智能体对话败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }


    /*
     * ========== TODO：把 ADK 事件流转成结构化 JSON 事件（待实现）==========
     * 现状：下面把 event.stringifyContent() 直接推给前端 —— 前端是一坨纯文本，
     *      分不出「AI 说的话」和「工具干的事」，工具调用还会被拼成 "Function Call: ..." 推出去。
     *
     * 目标：每行推一个 JSON，共 5 种。⚠️ 协议已冻结，前端已按这个实现，照它写不用返工：
     *   {"event":"text",        "content":"增量片段", "fullText":"累计全文"}
     *   {"event":"tool_call",   "toolCallId":"...", "toolName":"executeCommand", "status":"success"}
     *   {"event":"tool_result", "toolCallId":"...", "content":"命令原始输出", "status":"success"}
     *   {"event":"done",        "content":"累计全文"}
     *   {"event":"error",       "content":"错误信息"}
     *   心跳行（不是 JSON，前端自动跳过）：": heartbeat"
     *
     * 怎么判类型 —— 别用 stringifyContent()。ADK 1.5.0 的 Event 自带两个便捷方法，不用自己拆 parts：
     *   event.functionCalls()     → ImmutableList<FunctionCall>      → tool_call 事件
     *   event.functionResponses() → ImmutableList<FunctionResponse>  → tool_result 事件
     *   event.content()           → Optional<Content>  ⚠️ 是 Optional，不能直接 .parts()
     *       content.parts()       → Optional<List<Part>>；part.text() 有值 → text 事件
     *   取值全是 Optional：call.name().orElse("") / call.id().orElse("") / resp.id().orElse("")
     *   命令原始输出在 resp.response()（Map）里 —— 就是 executeCommand 的返回值 {success, output, command}
     *
     *   形状大致这样（自己补全）：
     *     for (FunctionCall call : event.functionCalls()) { 发送 tool_call }
     *     for (FunctionResponse resp : event.functionResponses()) { 发送 tool_result }
     *     event.content().flatMap(Content::parts).ifPresent(parts ->
     *         parts.forEach(p -> p.text().ifPresent(text -> 发送 text)));
     *
     *   以上签名已在 ADK 1.5.0 + google-genai 1.58.0（本项目实际解析到的版本）上核实过。
     *   ⚠️ 坑：`if (!stringifyContent().isBlank())` 挡不住工具调用 —— "Function Call: ..." 是非空串，照样当 text 推出去了。
     *
     * 另外三件事：
     *   1) 心跳线程：每 15 秒发一行 ": heartbeat"，防止长时间无数据被浏览器/代理掐断
     *   2) 异步执行：blockingForEach + 推送放进 new Thread(...)，别占 HTTP 线程
     *   3) onError 里先推一个 error 事件再 complete()（顺手解决 04 清单 A 条：现在出错前端一个字节都收不到）
     *   建议把 emitter 超时从 3 分钟调大（AI + SSH 可能跑很久）
     *
     * ⚠️ 教程的 stateDelta 方案先别照抄：ssh-agent.yml:147 的 output-key: ssh_result 配在 **Agent** 上，
     *   语义是「把 agent 最终回答写进 state」，未必是命令原始输出。先在这行打日志把 stateDelta 的
     *   key/value 打出来看一眼再决定；命令真输出建议从 functionResponse 拿。
     *
     * ⚠️ terminalSessionId 现在绑在 ChatService.handleMessageStream 里（能跑）。若按第 2 点改成新线程执行，
     *   绑定必须发生在「新线程内部」或「创建新线程之前」，否则 ThreadLocal 和会话级回退变量都可能拿不到值。
     * ====================================================================
     */
    @RequestMapping(value = "chat_stream", method = RequestMethod.POST)
    @Override
    public ResponseBodyEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("流式对话 agentId:{} userId:{} sessionId:{} terminalSessionId:{} message:{}",
                    requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(),
                    requestDTO.getTerminalSessionId(), requestDTO.getMessage());

            // 如果未指定sessionId，先创建
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            //创建发射器
            ResponseBodyEmitter emitter = new ResponseBodyEmitter(10 * 60 * 1000L);

            // 绑定终端会话到 ThreadLocal
            String terminalSessionId = requestDTO.getTerminalSessionId();
            if(terminalSessionId != null&&!terminalSessionId.isEmpty()){
                SshExecuteAdkTool.setCurrentTerminalSession(terminalSessionId);
            }

            // 异步执行，避免阻塞 HTTP 线程
            String finalSessionId = sessionId;
            new Thread(()->{
                //todo 心跳线程是我直接抄的，我不会写
                ObjectMapper objectMapper = new ObjectMapper();
                // 心跳保活线程：在 AI 处理期间定期发送 SSE 注释行，防止浏览器/proxy 超时断开
                java.util.concurrent.atomic.AtomicBoolean streaming = new java.util.concurrent.atomic.AtomicBoolean(true);
                Thread heartbeatThread = new Thread(() -> {
                    try {
                        while (streaming.get()) {
                            Thread.sleep(15000); // 每 15 秒发一次心跳
                            try {
                                emitter.send(": heartbeat\n\n");
                            } catch (Exception e) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        // 正常退出
                    }
                }, "sse-heartbeat-" + finalSessionId);
                heartbeatThread.setDaemon(true);
                heartbeatThread.start();
                try {
                    // 调用 ChatService 获取事件流
                    Flowable<Event> events = chatService.handleMessageStream(
                            requestDTO.getAgentId(),
                            requestDTO.getUserId(),
                            finalSessionId,
                            requestDTO.getMessage(),
                            terminalSessionId
                    );

                    StringBuilder textAccumulator = new StringBuilder();

                    // 遍历事件流，转发为结构化 SSE JSON 事件
                    events.blockingForEach(event -> {
                        try {
                            event.stringifyContent();

                            // 1. 处理文本内容（AI 的回复文本）
                            String eventText = event.stringifyContent();
                            if (!eventText.isBlank()) {
                                textAccumulator.append(eventText);

                                Map<String, Object> textEvent = new HashMap<>();
                                textEvent.put("event", "text");
                                textEvent.put("content", eventText);
                                textEvent.put("fullText", textAccumulator.toString());
                                emitter.send(objectMapper.writeValueAsString(textEvent) + "\n");
                                log.info("SSE text 事件已发送: length={}, fullTextLength={}, turnComplete={}",
                                        eventText.length(), textAccumulator.length(), event.turnComplete());
                            }

                            // 2. 从 stateDelta 检测工具执行结果（SSH 命令输出）
                            EventActions actions = event.actions();
                            if (actions != null) {
                                Map<String, Object> stateDelta = actions.stateDelta();
                                if (stateDelta != null && !stateDelta.isEmpty()) {
                                    for (Map.Entry<String, Object> entry : stateDelta.entrySet()) {
                                        String stateKey = entry.getKey();
                                        Object stateValue = entry.getValue();
                                        if ("REMOVED".equals(stateValue)) continue;

                                        String resultContent = stateValue != null ? stateValue.toString() : "";
                                        // 去除 JSON 字符串外层引号号
                                        if (resultContent.startsWith("\"") && resultContent.endsWith("\"") && resultContent.length() > 1) {
                                            resultContent = resultContent.substring(1, resultContent.length() - 1);
                                        }

                                        String toolCallId = "call_" + stateKey + "_" + System.currentTimeMillis();

                                        // 发送 tool_call 事件
                                        Map<String, Object> toolCallEvent = new HashMap<>();
                                        toolCallEvent.put("event", "tool_call");
                                        toolCallEvent.put("toolCallId", toolCallId);
                                        toolCallEvent.put("toolName", "executeCommand");
                                        toolCallEvent.put("status", "success");
                                        emitter.send(objectMapper.writeValueAsString(toolCallEvent) + "\n");

                                        // 发送 tool_result 事件（SSH 命令输出）
                                        Map<String, Object> toolResultEvent = new HashMap<>();
                                        toolResultEvent.put("event", "tool_result");
                                        toolResultEvent.put("toolCallId", toolCallId);
                                        toolResultEvent.put("content", resultContent);
                                        toolResultEvent.put("status", "success");
                                        emitter.send(objectMapper.writeValueAsString(toolResultEvent) + "\n");

                                        log.info("工具执行结果已发送: stateKey={}, resultLength={}", stateKey, resultContent.length());
                                    }
                                }
                            }

                        } catch (Exception ex) {
                            log.warn("SSE 发送异常: {}", ex.getMessage());
                        }
                    });

                    // 发送 done 事件
                    Map<String, Object> doneEvent = new HashMap<>();
                    doneEvent.put("event", "done");
                    doneEvent.put("content", textAccumulator.toString());
                    emitter.send(objectMapper.writeValueAsString(doneEvent) + "\n");

                    emitter.complete();
                    log.info("MVP 流式对话完成 sessionId={}", finalSessionId);

                } catch (Exception e) {
                    log.error("MVP 流式对话异常", e);
                    try {
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("event", "error");
                        errorEvent.put("content", e.getMessage());
                        emitter.send(new ObjectMapper().writeValueAsString(errorEvent) + "\n");
                    } catch (Exception ignored) {
                    }
                    emitter.completeWithError(e);
                } finally {
                    streaming.set(false);
                    heartbeatThread.interrupt();
                    // 清理 ThreadLocal 和会话级变量
                    SshExecuteAdkTool.clearCurrentTerminalSession();
                }
            }, "mvp-stream-" + sessionId).start();

            return emitter;
        } catch (Exception e) {
            log.error("MVP 流式对话初始化失败", e);
            ResponseBodyEmitter emitter = new ResponseBodyEmitter();
            emitter.completeWithError(e);
            return emitter;
        }
    }
}
