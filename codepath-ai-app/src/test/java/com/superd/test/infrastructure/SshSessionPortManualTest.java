package com.superd.test.infrastructure;

import com.superd.infrastructure.adapter.port.SshSessionPort;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 手动交互式 SSH 集成测试。
 * 说明：
 * 1. 这是一个本地调试/验证用例，不是单元测试，依赖真实 SSH 服务器和真实密码；
 * 2. 运行前必须把 HOST / USERNAME / PASSWORD 换成你自己的机器信息；
 * 3. 该用例会直接打开终端读取密码，并持续执行远程命令，不能在 CI / 自动化环境中直接跑；
 * 4. 若运行失败，常见原因包括：服务器未开启 SSH、端口不对、密码错误、或远端拒绝交互式认证。
 */
public class SshSessionPortManualTest {

    /**
     * 这里的 IP、账号要换成你自己的云服务器；
     */
    // 这几个值需要替换成真实环境：
    // HOST：远端 Linux 机器 IP 或域名
    // PORT：SSH 端口，通常是 22
    // USERNAME：可登录的用户名，如 ubuntu、root
    // CONNECTION_ID：当前进程内用于缓存 Session 的唯一标识
    private static final String HOST = "192.144.135.128";
    private static final int PORT = 22;
    private static final String USERNAME = "ubuntu";
    private static final String CONNECTION_ID = "manual-ssh-test";

    @Test
    public void test_connect_and_execute_commands_interactively() throws Exception {
        // 这个测试是“手动验证”场景，而不是自动化断言用例。
        // 在 CI 环境下通常不应执行，因为它依赖终端输入密码和真实远程机器。
        runInteractiveDemo();
    }

    public static void main(String[] args) throws Exception {
        // IDE 直接运行 main() 时，也会走这一套交互式流程。
        runInteractiveDemo();
    }

    private static void runInteractiveDemo() throws Exception {
        // 注意：这里实例化的是基础实现类，不走 Spring 容器。
        // 若要在业务代码中复用 SSH 能力，建议通过应用层服务/端口做封装。
        SshSessionPort sshSessionPort = new SshSessionPort();
        String password = readPasswordFromConsole();

        Assert.assertTrue("密码不能为空", password != null && !password.isBlank());

        boolean connected = sshSessionPort.connect(
                CONNECTION_ID, HOST, PORT, USERNAME, password, null);
        Assert.assertTrue("SSH 连接失败，请检查 IP、账号或密码是否正确", connected);

        try {
            Session session = sshSessionPort.getSession(CONNECTION_ID);
            Assert.assertNotNull("未获取到 SSH Session", session);

            printConnectionBanner(session);
            printCommandResult(executeCommand(session, "hostname && whoami && pwd"));
            interactiveCommandLoop(session);
        } finally {
            sshSessionPort.disconnect(CONNECTION_ID);
            System.out.println("\nSSH 连接已关闭。");
        }
    }

    @SuppressWarnings("resource")
    private static String readPasswordFromConsole() {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword("请输入云服务器密码: ");
            return password == null ? null : new String(password);
        }
        System.out.print("请输入云服务器密码: ");
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        return scanner.nextLine();
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
