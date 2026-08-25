package com.superd.test.infrastructure;

import com.superd.infrastructure.adapter.port.SshSessionPort;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 手动交互式 SSH 集成测试。
 * 说明：
 * 1. 这是一个本地调试/验证用例，不是单元测试，依赖真实 SSH 服务器；
 * 2. 连接信息从 application-local.yml 的 test.ssh.* 读取，无需在代码里硬编码；
 * 3. 该用例会持续执行远程命令，不能在 CI / 自动化环境中直接跑；
 * 4. 若运行失败，常见原因包括：服务器未开启 SSH、端口不对、密码错误、或远端拒绝交互式认证。
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SshSessionPortManualTest {

    @Value("${test.ssh.host}")
    private String host;

    @Value("${test.ssh.username}")
    private String username;

    @Value("${test.ssh.password}")
    private String password;

    @Value("${test.ssh.port:22}")
    private int port;

    @Value("${test.ssh.connection-id:manual-ssh-test}")
    private String connectionId;

    @Test
    public void test_connect_and_execute_commands_interactively() throws Exception {
        runInteractiveDemo();
    }

    private void runInteractiveDemo() throws Exception {
        // 这里直接实例化实现类，不依赖容器内缓存，便于单次手工验证。
        SshSessionPort sshSessionPort = new SshSessionPort();

        Assert.assertTrue("host 不能为空，请配置 test.ssh.host", host != null && !host.isBlank());
        Assert.assertTrue("username 不能为空，请配置 test.ssh.username", username != null && !username.isBlank());
        Assert.assertTrue("password 不能为空，请配置 test.ssh.password", password != null && !password.isBlank());

        boolean connected = sshSessionPort.connect(
                connectionId, host, port, username, password, null);
        Assert.assertTrue("SSH 连接失败，请检查 test.ssh 配置是否正确", connected);

        try {
            Session session = sshSessionPort.getSession(connectionId);
            Assert.assertNotNull("未获取到 SSH Session", session);

            printConnectionBanner(session);
            printCommandResult(executeCommand(session, "hostname && whoami && pwd"));
            interactiveCommandLoop(session);
        } finally {
            sshSessionPort.disconnect(connectionId);
            System.out.println("\nSSH 连接已关闭。");
        }
    }

    private static void printConnectionBanner(Session session) {
        System.out.println("\n================ SSH 连接成功 ================");
        System.out.println("Host      : " + session.getHost());
        System.out.println("Port      : " + session.getPort());
        System.out.println("User      : " + session.getUserName());
        System.out.println("ServerVer : " + session.getServerVersion());
        System.out.println("ClientVer : " + session.getClientVersion());
        System.out.println("输入命令后回车即可执行，例如: ls、ls -la");
        System.out.println("输入 exit 或 quit 结束测试");
        System.out.println("============================================");
    }

    @SuppressWarnings("resource")
    private static void interactiveCommandLoop(Session session) throws Exception {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("\nssh> ");
            if (!scanner.hasNextLine()) break;
            String command = scanner.nextLine();
            if (command == null || command.isBlank()) continue;
            if ("exit".equalsIgnoreCase(command) || "quit".equalsIgnoreCase(command)) break;
            printCommandResult(executeCommand(session, command));
        }
    }

    /**
     * 在已建立的 SSH Session 上执行一条远程命令，并返回命令的标准输出、错误输出与退出码。
     *
     * 作用：
     * 1. 通过 JSch 打开一个 exec 类型的 Channel；
     * 2. 把待执行的 shell 命令（如 ls、pwd、hostname）交给远端主机；
     * 3. 收集 stdout / stderr；
     * 4. 等待命令结束，读取 exitCode；
     * 5. 最终把结果包装成 CommandResult 返回，供控制台输出或后续断言。
     *
     * 这个方法是整个手工 SSH 调试用例的核心：它让本地 Java 代码像 SSH 客户端一样执行远程命令。
     */
    private static CommandResult executeCommand(Session session, String command) throws Exception {
        ChannelExec channel = null;
        try {
            // 打开一个 exec 通道，适合执行单条远程命令
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            // 用 ByteArrayOutputStream 收集命令输出，避免直接打到控制台
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);
            channel.connect(10_000);

            // 等待命令执行结束
            while (!channel.isClosed()) {
                Thread.sleep(200L);
            }

            return new CommandResult(
                    command,
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8),
                    channel.getExitStatus()
            );
        } catch (JSchException e) {
            throw new IllegalStateException("执行命令失败: " + command, e);
        } finally {
            // 释放通道，防止资源泄漏
            if (channel != null) channel.disconnect();
        }
    }

    private static void printCommandResult(CommandResult result) {
        System.out.println("\n$ " + result.command());
        System.out.println("exitCode: " + result.exitCode());

        if (!result.stdout().isBlank()) {
            System.out.println("[stdout]");
            System.out.print(result.stdout());
            if (!result.stdout().endsWith("\n")) System.out.println();
        }

        if (!result.stderr().isBlank()) {
            System.out.println("[stderr]");
            System.out.print(result.stderr());
            if (!result.stderr().endsWith("\n")) System.out.println();
        }
    }

    private record CommandResult(String command, String stdout, String stderr, int exitCode) {
    }
}
