package com.superd.infrastructure.adapter.port.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import com.superd.domain.ssh.adapter.port.ITerminalSessionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端会话管理器
 * 基础设施层实现，管理 Shell 通道的创建、读写、关闭
 *
 */
@Slf4j
@Component
public class TerminalSessionPort implements ITerminalSessionPort {

    @Resource
    private SshSessionPort sshSessionService;

    /** Shell prompt 检测用的常见标记 */
    private static final String[] PROMPT_MARKERS = {"$ ", "# ", "% "};

    /** sessionId -> Shell 通道 */
    private final Map<String, ChannelShell> channels = new ConcurrentHashMap<>();

    /** sessionId -> 输出流 */
    private final Map<String, OutputStream> outputStreams = new ConcurrentHashMap<>();

    /** sessionId -> 输入流 */
    private final Map<String, InputStream> inputStreams = new ConcurrentHashMap<>();

    /** sessionId -> 未读输出缓冲区 */
    private final Map<String, StringBuilder> outputBuffers = new ConcurrentHashMap<>();

    /** sessionId -> AI命令执行的独立缓冲区（不受前端轮询影响） */
    private final Map<String, StringBuilder> aiCommandBuffers = new ConcurrentHashMap<>();

    /** sessionId -> 是否正在执行AI命令（为true时reader写入aiCommandBuffers，否则写入outputBuffers） */
    private final Map<String, Boolean> aiCommandMode = new ConcurrentHashMap<>();

    /** sessionId -> 读取线程是否存活 */
    private final Map<String, Boolean> readerAlive = new ConcurrentHashMap<>();

    /** connectionId -> 当前活跃的 sessionId（一个连接只允许一个终端会话） */
    private final Map<String, String> activeConnectionSession = new ConcurrentHashMap<>();

    @Override
    public String openTerminal(String connectionId, int cols, int rows) {
        // 同一 connectionId 只允许一个终端会话，先关闭旧的
        String oldSessionId = activeConnectionSession.get(connectionId);
        if (oldSessionId != null) {
            log.info("关闭旧终端会话以避免重复 connectionId={} oldSessionId={}", connectionId, oldSessionId);
            cleanup(oldSessionId);
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");

        try {
            Session session = sshSessionService.getSession(connectionId);
            if (session == null || !session.isConnected()) {
                throw new IllegalStateException("SSH会话不可用 connectionId=" + connectionId);
            }

            ChannelShell channel = (ChannelShell) session.openChannel("shell");
            channel.setPty(true);
            channel.setPtySize(cols, rows, 480, 640);

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            channel.connect(5000);

            channels.put(sessionId, channel);
            inputStreams.put(sessionId, in);
            outputStreams.put(sessionId, out);
            outputBuffers.put(sessionId, new StringBuilder());
            activeConnectionSession.put(connectionId, sessionId);

            // 启动输出读取线程，持续读取 shell 输出到缓冲区
            startOutputReader(sessionId, in);

            // 等待 Shell 首次输出到达 + 额外等待让 MOTD 完整积累
            // 然后消费缓冲区，作为 initialOutput 返回给前端
            // 这样前端不再依赖轮询获取初始输出，彻底解决"有时显示有时不显示"的问题
            StringBuilder buffer = outputBuffers.get(sessionId);
            long waitDeadline = System.currentTimeMillis() + 3000; // 最多等 3s 等首数据
            try {
                // 阶段1：等首数据到达
                while (System.currentTimeMillis() < waitDeadline) {
                    synchronized (buffer) {
                        if (buffer.length() > 0) {
                            break;
                        }
                    }
                    Thread.sleep(30);
                }
                // 阶段2：额外等 200ms 让 MOTD/prompt 完整到达
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            log.info("终端会话打开成功 sessionId={} connectionId={}", sessionId, connectionId);
            return sessionId;

        } catch (Exception e) {
            log.error("打开终端会话失败 connectionId={}", connectionId, e);
            cleanup(sessionId);
            throw new RuntimeException("打开终端失败: " + e.getMessage(), e);
        }
    }

    /** 输入重试次数 */
    private static final int WRITE_MAX_RETRIES = 2;

    /** Write with retry on I/O failure */
    @Override
    public void write(String sessionId, String command) {
        OutputStream out = outputStreams.get(sessionId);
        if (out == null) {
            throw new IllegalArgumentException("终端会话不存在或已关闭 sessionId=" + sessionId);
        }

        IOException lastError = null;
        for (int attempt = 1; attempt <= WRITE_MAX_RETRIES; attempt++) {
            try {
                out.write(command.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return; // 成功
            } catch (IOException e) {
                lastError = e;
                log.warn("写入终端失败 (attempt={}/{}) sessionId={} reason={}",
                        attempt, WRITE_MAX_RETRIES, sessionId, e.getMessage());
                // 最后一次重试后抛出异常
                if (attempt == WRITE_MAX_RETRIES) {
                    break   ;
                }
                // 短暂等待后重试
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.error("写入终端失败 (已重试 {} 次) sessionId={}", WRITE_MAX_RETRIES, sessionId, lastError);
        throw new RuntimeException("写入终端失败: " + lastError.getMessage(), lastError);
    }

    @Override
    public String read(String sessionId) {
        StringBuilder buffer = outputBuffers.get(sessionId);
        if (buffer == null) {
            throw new IllegalArgumentException("终端会话不存在或已关闭 sessionId=" + sessionId);
        }

        Boolean alive = readerAlive.get(sessionId);
        if (alive != null && !alive) {
            ChannelShell channel = channels.get(sessionId);
            if (channel == null || !channel.isConnected()) {
                return "\u001b[31m\r\n[连接已断开]\u001b[0m\r\n";
            }
            InputStream in = inputStreams.get(sessionId);
            if (in != null) {
                log.info("尝试重启终端读取线程 sessionId={}", sessionId);
                startOutputReader(sessionId, in);
            }
        }

        // 非阻塞：直接返回缓冲区当前内容，不等
        // 前端轮询本身就是等待机制，不需要后端再等
        synchronized (buffer) {
            if (buffer.length() == 0) {
                return "";
            }
            String output = buffer.toString();
            buffer.setLength(0);
            return output;
        }
    }

    // 输出清理在 cleanCommandOutput（去 ANSI 控制码 / 去 \r / 去命令回显行 / 去结尾 prompt 行）。
    //
    // 遗留一件事（自己拍）：
    //   **超时参数被架空**：调用方 SshTerminalService 传的是硬编码 30000，本方法签名里的 timeoutMs 收不到真实意图 ——
    //   调用方以为能控制超时其实控制不了。要么让它把 waitTimeoutMs 真透下来，要么干脆删掉参数只留常量。
    @Override
    public String executeCommandAndWait(String sessionId, String command, long timeoutMs) {
        StringBuilder frontBuffer = outputBuffers.get(sessionId);
        if (frontBuffer == null) {
            throw new IllegalArgumentException("终端会话不存在或已关闭 sessionId=" + sessionId);
        }

        // 创建或获取AI命令独立缓冲区
        StringBuilder aiBuffer = aiCommandBuffers.computeIfAbsent(sessionId, k -> new StringBuilder());

        // 1. 切换到AI命令模式
        aiCommandMode.put(sessionId, true);
        synchronized (aiBuffer) {
            aiBuffer.setLength(0); // drain 残余数据
        }
        // 也 drain 前端缓冲区，避免混淆
        synchronized (frontBuffer) {
            frontBuffer.setLength(0);
        }

        try {
            // 2. 写入命令 + 换行符
            String fullCommand = command.endsWith("\n") ? command : command + "\n";
            try {
                OutputStream out = outputStreams.get(sessionId);
                if (out == null) {
                    throw new IllegalStateException("终端输出流不可用 sessionId=" + sessionId);
                }
                out.write(fullCommand.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                throw new RuntimeException("写入终端失败: " + e.getMessage(), e);
            }
            log.info("[executeCommandAndWait] 命令已写入 sessionId={} command={}", sessionId, command);

            // 3. 等待命令执行结束
            //    结束信号只有一个：输出末尾重新出现 Shell prompt。
            //    timeoutMs 只是兜底 —— 正常命令靠 prompt 检测提前返回，碰不到它。
            long deadline = System.currentTimeMillis() + timeoutMs;
            final long POLL_INTERVAL = 100;   // 单轮 wait 的上限
            final long STABLE_MS = 300;       // 距上次收到数据超过这么久，才去检查 prompt
            long lastDataAt = System.currentTimeMillis();
            StringBuilder collected = new StringBuilder();

            while (System.currentTimeMillis() < deadline) {
                String chunk;
                synchronized (aiBuffer) {
                    if (aiBuffer.isEmpty()) {
                        long remain = deadline - System.currentTimeMillis();
                        if (remain > 0) {
                            try {
                                aiBuffer.wait(Math.min(POLL_INTERVAL, remain));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                    // 取走并清空：清空是必须的 —— 否则缓冲区永远非空，"数据停没停"就判断不出来
                    chunk = aiBuffer.toString();
                    aiBuffer.setLength(0);
                }

                if (!chunk.isEmpty()) {
                    collected.append(chunk);
                    lastDataAt = System.currentTimeMillis();
                    continue;
                }

                // 这一轮没有新数据；只有「距上次收到数据够久」才去检查 prompt
                long idleMs = System.currentTimeMillis() - lastDataAt;
                if (collected.length() > 0 && idleMs >= STABLE_MS && containsPrompt(collected.toString())) {
                    log.info("[executeCommandAndWait] 检测到 prompt，命令结束 sessionId={} outputLength={} idleMs={}",
                            sessionId, collected.length(), idleMs);
                    break;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                log.warn("[executeCommandAndWait] 到达超时兜底（未检测到 prompt）sessionId={} timeoutMs={} outputLength={}",
                        sessionId, timeoutMs, collected.length());
            }

            // 5. 清理输出
            String result = cleanCommandOutput(collected.toString(), command);

            log.info("[executeCommandAndWait] 完成 sessionId={} resultLength={} resultPreview={}",
                    sessionId, result.length(),
                    result.length() > 200 ? result.substring(0, 200) + "..." : result);

            return result;

        } finally {
            // 6. 切换回正常模式
            aiCommandMode.put(sessionId, false);
        }
    }

    /**
     * 判断输出末尾是否已经回到 Shell prompt —— 这是「命令执行结束」的唯一信号。
     * <p>只看<b>最后一行</b>，且要求它以 marker 结尾：比「末尾 N 个字符里包含 marker」严格得多，
     * 避免输出内容里恰好出现 "$ " 就被误判成命令结束。
     */
    private boolean containsPrompt(String output) {
        if (output == null || output.isEmpty()) return false;
        int lastNewline = output.lastIndexOf('\n');
        String lastLine = (lastNewline >= 0 ? output.substring(lastNewline + 1) : output).trim();
        if (lastLine.isEmpty()) return false;
        for (String marker : PROMPT_MARKERS) {
            String symbol = marker.trim();          // {"$ ", "# ", "% "} -> {"$", "#", "%"}
            if (!symbol.isEmpty() && lastLine.endsWith(symbol)) return true;
        }
        return false;
    }

    /**
     * 清理命令输出：
     * - 去除第一行命令回显
     * - 去除末尾 prompt 行
     * - 去除 ANSI 控制序列
     */
    private String cleanCommandOutput(String output, String command) {
        if (output == null || output.isEmpty()) return "";

        // 去除 ANSI CSI 转义序列：ESC [ 参数 中间字节 终止字节
        // 参数类可以是 0-9 : ; < = > ? —— 原来的 [0-9;] 覆盖不到 ?，所以 ESC[?2004l 会漏掉
        String cleaned = output.replaceAll("\u001b\\[[0-9:;<=>?]*[ -/]*[@-~]", "");
        // 去除回车符
        cleaned = cleaned.replace("\r", "");

        // 按行分割
        String[] lines = cleaned.split("\n");
        java.util.List<String> resultLines = new java.util.ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            // 跳过空行
            if (trimmedLine.isEmpty()) continue;

            // 跳过第一行如果是命令回显
            if (resultLines.isEmpty() && isCommandEcho(trimmedLine, command)) {
                continue;
            }

            // 跳过末尾 prompt 行
            if (i == lines.length - 1 && isPromptLine(trimmedLine)) {
                continue;
            }

            resultLines.add(line);
        }

        return String.join("\n", resultLines).trim();
    }

    /**
     * 判断是否为命令回显行
     */
    private boolean isCommandEcho(String line, String command) {
        String cmd = command.trim();
        // 精确匹配或前缀匹配（Shell 有时会加 prompt 前缀）
        if (line.equals(cmd)) return true;
        // 处理带 prompt 前缀的情况，如 "ubuntu@host:~$ cat /etc/os-release"
        if (line.endsWith(cmd)) return true;
        // 命令很短时只做精确匹配，避免误杀
        if (cmd.length() > 10 && line.contains(cmd)) return true;
        return false;
    }

    /**
     * 判断是否为 prompt 行（如 "ubuntu@VM-0-7-ubuntu:~$"）
     */
    private boolean isPromptLine(String line) {
        for (String marker : PROMPT_MARKERS) {
            if (line.endsWith(marker.trim())) return true;
        }
        // 匹配 user@host:dir$ 格式
        if (line.matches(".*@.*:[^$]*\\$\\s*$")) return true;
        if (line.matches(".*@.*:[^#]*#\\s*$")) return true;
        return false;
    }
    
    @Override
    public void resize(String sessionId, int cols, int rows) {
        ChannelShell channel = channels.get(sessionId);
        if (channel == null || !channel.isConnected()) {
            throw new IllegalArgumentException("终端会话不存在或已关闭 sessionId=" + sessionId);
        }

        try {
            channel.setPtySize(cols, rows, 480, 640);
            log.debug("终端大小已调整 sessionId={} {}x{}", sessionId, cols, rows);
        } catch (Exception e) {
            log.error("调整终端大小失败 sessionId={}", sessionId, e);
            throw new RuntimeException("调整终端大小失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void closeSession(String sessionId) {
        log.info("关闭终端会话 sessionId={}", sessionId);
        cleanup(sessionId);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        ChannelShell channel = channels.get(sessionId);
        return channel != null && channel.isConnected();
    }

    // ========== 内部方法 ==========

    /**
     * 启动输出读取线程
     * SocketTimeoutException 时继续循环（不是真正的断连），
     * 只有 EOF（-1）或真正的 IOException 才退出
     */
    private void startOutputReader(String sessionId, InputStream in) {
        readerAlive.put(sessionId, true);
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int len;
                while ((len = in.read(buf)) != -1) {
                    String text = new String(buf, 0, len, StandardCharsets.UTF_8);
                    // 根据模式决定写入哪个缓冲区
                    Boolean aiMode = aiCommandMode.get(sessionId);
                    if (Boolean.TRUE.equals(aiMode)) {
                        // AI命令模式：写入独立缓冲区
                        StringBuilder aiBuffer = aiCommandBuffers.get(sessionId);
                        if (aiBuffer != null) {
                            synchronized (aiBuffer) {
                                aiBuffer.append(text);
                                aiBuffer.notifyAll(); // 通知等待的 executeCommandAndWait
                            }
                        }
                        // 同时也写入前端缓冲区，让前端能看到命令回显
                        StringBuilder frontBuffer = outputBuffers.get(sessionId);
                        if (frontBuffer != null) {
                            synchronized (frontBuffer) {
                                frontBuffer.append(text);
                            }
                        }
                    } else {
                        // 正常模式：写入前端缓冲区
                        StringBuilder buffer = outputBuffers.get(sessionId);
                        if (buffer != null) {
                            synchronized (buffer) {
                                buffer.append(text);
                            }
                        }
                    }
                }
                // in.read() 返回 -1，说明 shell channel EOF
                log.warn("终端 Shell Channel EOF sessionId={}", sessionId);
            } catch (java.net.SocketTimeoutException e) {
                // SocketTimeout 不是断连，重试即可——但实际不应到这里了（setTimeout(0)）
                // 保留作为防御性处理
                log.debug("终端读取超时（非断连），继续读取 sessionId={}", sessionId);
                InputStream inRef = inputStreams.get(sessionId);
                if (inRef != null) {
                    startOutputReader(sessionId, inRef);
                }
                return; // 当前线程退出，新线程已接替
            } catch (IOException e) {
                ChannelShell ch = channels.get(sessionId);
                // channel 还活着说明不是真正的断连，可能是临时 I/O 问题，尝试重启
                if (ch != null && ch.isConnected()) {
                    log.warn("终端读取I/O异常但channel仍连接，重启reader sessionId={} reason={}", sessionId, e.getMessage());
                    InputStream inRef = inputStreams.get(sessionId);
                    if (inRef != null) {
                        startOutputReader(sessionId, inRef);
                    }
                    return;
                }
                log.debug("终端输出读取异常 sessionId={} reason={}", sessionId, e.getMessage());
            } finally {
                readerAlive.put(sessionId, false);

                // 诊断：为什么线程退出了？
                ChannelShell ch = channels.get(sessionId);
                boolean channelConnected = ch != null && ch.isConnected();
                boolean channelClosed = ch != null && ch.isClosed();
                log.warn("终端输出读取线程退出 sessionId={} channelConnected={} channelClosed={}",
                        sessionId, channelConnected, channelClosed);

                // 通知等待中的 read() 方法：线程已退出，不再有数据
                StringBuilder buffer = outputBuffers.get(sessionId);
                if (buffer != null) {
                    synchronized (buffer) {
                        buffer.notifyAll();
                    }
                }
            }
        }, "terminal-reader-" + sessionId);
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * 清理资源
     */
    private void cleanup(String sessionId) {
        // 清理 connectionId -> sessionId 映射
        activeConnectionSession.entrySet().removeIf(e -> sessionId.equals(e.getValue()));

        try {
            OutputStream out = outputStreams.remove(sessionId);
            if (out != null) out.close();
        } catch (IOException ignored) {}

        try {
            InputStream in = inputStreams.remove(sessionId);
            if (in != null) in.close();
        } catch (IOException ignored) {}

        ChannelShell channel = channels.remove(sessionId);
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }

        outputBuffers.remove(sessionId);
        readerAlive.remove(sessionId);
    }

}
