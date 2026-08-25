package com.superd.test.domain.ssh;

import com.superd.domain.ssh.adapter.port.ISshSessionPort;
import com.superd.domain.ssh.adapter.repository.ISshConnectionRepository;
import com.superd.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.superd.domain.ssh.model.entity.SshConnectionEntity;
import com.superd.domain.ssh.model.valobj.AuthTypeEnum;
import com.superd.domain.ssh.model.valobj.ConnectionStatusEnum;
import com.superd.domain.ssh.service.SshConnectionService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SshConnectionServiceTest {
    /**
     * 需要配置：
     * 1) 测试框架：JUnit4（@RunWith + @Test）
     * 2) Mock框架：Mockito（@Mock / @InjectMocks）
     * 3) 依赖由 codepath-ai-app 的 spring-boot-starter-test 提供
     * 4) 本类是纯单元测试，不依赖真实 SSH 服务器，不读取 test.ssh.* 本地配置
     */

    @Mock
    private ISshConnectionRepository repository;

    @Mock
    private ISshSessionPort sshSessionPort;

    @InjectMocks
    private SshConnectionService sshConnectionService;

    private SshConnectionEntity baseEntity;

    @Before
    public void setUp() {
        baseEntity = SshConnectionEntity.builder()
                .connectionId("conn-001")
                .connectionName("test-conn")
                .host("127.0.0.1")
                .port(22)
                .username("root")
                .authType(AuthTypeEnum.PASSWORD)
                .password("pwd")
                .privateKey("key")
                .encrypted(1)
                .userId("u1")
                .build();
    }

    @Test
    // 测试创建连接时：自动补齐默认字段，并保存连接配置默认值
    public void test_createConnection_shouldSetDefaultsAndSaveConfig() {
        SshConnectionEntity entity = SshConnectionEntity.builder()
                .connectionName("conn")
                .host("192.168.1.10")
                .port(22)
                .username("admin")
                .encrypted(null)
                .userId("")
                .build();
        SshConnectionConfigEntity configEntity = SshConnectionConfigEntity.builder().build();

        sshConnectionService.createConnection(entity, configEntity);

        ArgumentCaptor<SshConnectionEntity> connectionCaptor = ArgumentCaptor.forClass(SshConnectionEntity.class);
        ArgumentCaptor<SshConnectionConfigEntity> configCaptor = ArgumentCaptor.forClass(SshConnectionConfigEntity.class);
        verify(repository, times(1)).saveConnection(connectionCaptor.capture());
        verify(repository, times(1)).saveConnectionConfig(configCaptor.capture());

        SshConnectionEntity saved = connectionCaptor.getValue();
        assertNotNull(saved.getConnectionId());
        assertFalse(saved.getConnectionId().isBlank());
        assertEquals(ConnectionStatusEnum.DISCONNECTED, saved.getStatus());
        assertEquals(Integer.valueOf(1), saved.getEncrypted());
        assertEquals("default", saved.getUserId());

        SshConnectionConfigEntity savedConfig = configCaptor.getValue();
        assertEquals(saved.getConnectionId(), savedConfig.getConnectionId());
        assertEquals(Integer.valueOf(10), savedConfig.getConnectTimeout());
        assertEquals(Integer.valueOf(60), savedConfig.getKeepaliveInterval());
        assertEquals(Boolean.FALSE, savedConfig.getCompression());
        assertEquals(Boolean.TRUE, savedConfig.getStrictHostKeyCheck());
    }

    @Test
    // 测试创建连接时：当高级配置为空，只保存连接主体，不保存配置
    public void test_createConnection_whenConfigIsNull_shouldNotSaveConfig() {
        SshConnectionEntity entity = SshConnectionEntity.builder()
                .connectionName("conn")
                .host("192.168.1.10")
                .port(22)
                .username("admin")
                .build();

        sshConnectionService.createConnection(entity, null);

        verify(repository, times(1)).saveConnection(any(SshConnectionEntity.class));
        verify(repository, never()).saveConnectionConfig(any(SshConnectionConfigEntity.class));
    }

    @Test(expected = IllegalArgumentException.class)
    // 测试更新连接时：连接不存在会抛出“连接不存在”异常
    public void test_updateConnection_whenConnectionNotExists_shouldThrow() {
        when(repository.queryConnectionById("conn-001")).thenReturn(null);

        sshConnectionService.updateConnection(baseEntity, null);
    }

    @Test
    // 测试更新连接时：密码/私钥/encrypted 为空时沿用旧值，并保存配置
    public void test_updateConnection_shouldKeepSensitiveFieldsWhenEmpty() {
        SshConnectionEntity existing = SshConnectionEntity.builder()
                .connectionId("conn-001")
                .connectionName("old")
                .host("10.0.0.1")
                .port(22)
                .username("root")
                .password("old-pwd")
                .privateKey("old-key")
                .encrypted(0)
                .build();
        when(repository.queryConnectionById("conn-001")).thenReturn(existing);

        SshConnectionEntity update = SshConnectionEntity.builder()
                .connectionId("conn-001")
                .connectionName("new")
                .host("10.0.0.2")
                .port(22)
                .username("admin")
                .password("")
                .privateKey("")
                .encrypted(null)
                .build();
        SshConnectionConfigEntity config = SshConnectionConfigEntity.builder().connectTimeout(30).build();

        sshConnectionService.updateConnection(update, config);

        ArgumentCaptor<SshConnectionEntity> connectionCaptor = ArgumentCaptor.forClass(SshConnectionEntity.class);
        ArgumentCaptor<SshConnectionConfigEntity> configCaptor = ArgumentCaptor.forClass(SshConnectionConfigEntity.class);
        verify(repository, times(1)).updateConnection(connectionCaptor.capture());
        verify(repository, times(1)).saveConnectionConfig(configCaptor.capture());

        SshConnectionEntity saved = connectionCaptor.getValue();
        assertEquals("old-pwd", saved.getPassword());
        assertEquals("old-key", saved.getPrivateKey());
        assertEquals(Integer.valueOf(0), saved.getEncrypted());
        assertEquals("conn-001", configCaptor.getValue().getConnectionId());
    }

    @Test(expected = IllegalArgumentException.class)
    // 测试删除连接时：连接ID为空白会抛出“连接ID不能为空”异常
    public void test_deleteConnection_whenIdIsBlank_shouldThrow() {
        sshConnectionService.deleteConnection(" ");
    }

    @Test
    // 测试查询列表时：userId 为空白会回退到 default 用户查询
    public void test_getConnectionList_whenUserIdBlank_shouldUseDefaultUser() {
        List<SshConnectionEntity> expected = List.of(baseEntity);
        when(repository.queryConnectionListByUserId("default")).thenReturn(expected);

        List<SshConnectionEntity> result = sshConnectionService.getConnectionList(" ");

        assertEquals(1, result.size());
        assertEquals("conn-001", result.get(0).getConnectionId());
        verify(repository, times(1)).queryConnectionListByUserId("default");
    }

    @Test
    // 测试连接成功时：返回 true，并将连接状态更新为 CONNECTED
    public void test_connect_success_shouldSetConnectedStatus() {
        when(repository.queryConnectionById("conn-001")).thenReturn(baseEntity);
        when(sshSessionPort.connect("conn-001", "127.0.0.1", 22, "root", "pwd", "key")).thenReturn(true);

        boolean result = sshConnectionService.connect("conn-001");

        assertTrue(result);
        ArgumentCaptor<SshConnectionEntity> captor = ArgumentCaptor.forClass(SshConnectionEntity.class);
        verify(repository, times(1)).updateConnection(captor.capture());
        assertEquals(ConnectionStatusEnum.CONNECTED, captor.getValue().getStatus());
    }

    @Test
    // 测试连接失败时：返回 false，并将连接状态更新为 FAILED
    public void test_connect_failed_shouldSetFailedStatus() {
        when(repository.queryConnectionById("conn-001")).thenReturn(baseEntity);
        when(sshSessionPort.connect("conn-001", "127.0.0.1", 22, "root", "pwd", "key")).thenReturn(false);

        boolean result = sshConnectionService.connect("conn-001");

        assertFalse(result);
        ArgumentCaptor<SshConnectionEntity> captor = ArgumentCaptor.forClass(SshConnectionEntity.class);
        verify(repository, times(1)).updateConnection(captor.capture());
        assertEquals(ConnectionStatusEnum.FAILED, captor.getValue().getStatus());
    }

    @Test
    // 测试断开连接时：连接存在则调用断开并将状态更新为 DISCONNECTED
    public void test_disconnect_whenConnectionExists_shouldUpdateStatus() {
        when(repository.queryConnectionById("conn-001")).thenReturn(baseEntity);

        sshConnectionService.disconnect("conn-001");

        verify(sshSessionPort, times(1)).disconnect("conn-001");
        ArgumentCaptor<SshConnectionEntity> captor = ArgumentCaptor.forClass(SshConnectionEntity.class);
        verify(repository, times(1)).updateConnection(captor.capture());
        assertEquals(ConnectionStatusEnum.DISCONNECTED, captor.getValue().getStatus());
    }

    @Test
    // 测试断开连接时：连接不存在仅执行断开，不进行状态更新
    public void test_disconnect_whenConnectionNotExists_shouldNotUpdateStatus() {
        when(repository.queryConnectionById("conn-001")).thenReturn(null);

        sshConnectionService.disconnect("conn-001");

        verify(sshSessionPort, times(1)).disconnect("conn-001");
        verify(repository, never()).updateConnection(any(SshConnectionEntity.class));
    }

    @Test
    // 测试连接状态查询：直接透传 ISshSessionPort 的 isConnected 结果
    public void test_isConnected_shouldDelegateToSessionPort() {
        when(sshSessionPort.isConnected("conn-001")).thenReturn(true);

        boolean connected = sshConnectionService.isConnected("conn-001");

        assertTrue(connected);
        verify(sshSessionPort, times(1)).isConnected("conn-001");
    }
}
