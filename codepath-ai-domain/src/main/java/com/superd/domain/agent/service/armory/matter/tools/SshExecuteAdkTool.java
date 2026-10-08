package com.superd.domain.agent.service.armory.matter.tools;


import com.google.adk.tools.Annotations;
import com.superd.domain.ssh.service.ISshTerminalDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * ssh命令执行工具
 */
@Slf4j
@Service
public class SshExecuteAdkTool {

    @Resource
    private ISshTerminalDomainService sshTerminalDomainService;

    // 当前线程的终端会话 ID（使用 InheritableThreadLocal 支持异步线程继承）
    private static final InheritableThreadLocal<String> currentTerminalSession = new InheritableThreadLocal<>();

    /** 当前会话级终端会话ID（由 Controller 设置，优先级低于 ThreadLocal） */
    private static volatile String sessionTerminalSessionId;

    /**
     * 设置当前线程的终端会话 ID（兼容旧接口）
     */
    public static void setCurrentTerminalSession(String terminalSessionId) {
        currentTerminalSession.set(terminalSessionId);
        sessionTerminalSessionId=terminalSessionId;
        log.info("[ThreadLocal] 设置终端会话: thread={}, terminalSession={}",
                Thread.currentThread().getName(), terminalSessionId);
    }

    /**
     * 清除当前线程的终端会话 ID
     */
    public static void clearCurrentTerminalSession() {
        currentTerminalSession.remove();
        sessionTerminalSessionId=null;
    }

    public Map<String,Object> executeCommand(
            @Annotations.Schema(name = "command",description = "要执行的 Shell 命令，如: ls -la, apt install docker.io, docker --version")
            String command
    ){
        // 优先从 ThreadLocal 获取终端会话ID
        String terminalSessionId = currentTerminalSession.get();

        //如果线程池情况下导致ThreadLocal失效 改用controller设置的会话id
        if (terminalSessionId == null||terminalSessionId.isEmpty()) {
            terminalSessionId = sessionTerminalSessionId;
            log.info("[executeCommand] ThreadLocal 为空，回退到会话级变量: terminalSessionId={}", terminalSessionId);
        }
        log.info("[executeCommand] thread={}, terminalSessionId={}, command={}",
                Thread.currentThread().getName(), terminalSessionId, command);

        if (terminalSessionId == null || terminalSessionId.isEmpty()) {
            log.warn("[executeCommand] 终端会话ID为空，无法执行命令");
            return Map.of(
                    "success", false,
                    "output", "未绑定 SSH 终端会话。请先打开 SSH 终端连接。",
                    "command", command
            );
        }

        try{
            //获取id尝试开始执行命令
            log.info("SSH 执行命令: session={}, command={}", terminalSessionId, command);
            String output = sshTerminalDomainService.executeCommand(terminalSessionId, command);
            //检测执行结果，是否成功
            Boolean success = isExecutionSuccessful(output);
            //无论是否成功，写入结果
            Map<String, Object> result = new HashMap<String, Object>(Map.of(
                    "success", success,
                    "output", output,
                    "command", command
            ));
            //如果未成功，分析原因并写入
            if(!success){
                String suggestion = analyzeError(output);
                result.put("suggestion", suggestion);
            }
            return result;
        }catch (Exception e){
            //错误，写入结果
            log.error("SSH 命令执行异常: session={}, command={}", terminalSessionId, command, e);
            return Map.of(
                    "success", false,
                    "output", "命令执行异常: " + e.getMessage(),
                    "command", command
            );
        }



    }


    /**
     * 检查执行结果是否成功
     */
    public Boolean isExecutionSuccessful(String output){
        if (output == null || output.isEmpty()) {
            return true;
        }

        String lowerOutput = output.toLowerCase();
        String[] errorIndicators = {
                "command not found", "no such file or directory", "permission denied",
                "operation not permitted", "cannot find", "error:", "failed",
                "fatal:", "unable to", "connection refused", "network is unreachable"
        };

        for (String indicator : errorIndicators) {
            if (lowerOutput.contains(indicator)) {
                return false;
            }
        }
        return true;
    }

    /**
     *  分析错误，给出建议
     */
    public String analyzeError(String output){
        if (output == null) return null;

        String lowerOutput = output.toLowerCase();

        if (lowerOutput.contains("command not found")) {
            return "命令不存在。可能原因：命令拼写错误、软件未安装、或命令不在 PATH 中。建议检查命令名称或安装对应软件包。";
        }
        if (lowerOutput.contains("permission denied")) {
            return "权限不足。建议使用 sudo 提升权限，或检查文件/目录权限。";
        }
        if (lowerOutput.contains("no such file or directory")) {
            return "文件或目录不存在。建议检查路径是否正确，或使用绝对路径。";
        }
        if (lowerOutput.contains("connection refused") || lowerOutput.contains("network is unreachable")) {
            return "网络连接问题。建议检查网络连接、确认目标服务是否运行、检查防火墙设置。";
        }
        return "执行失败，请检查命令和输出信息。";
    }

}
