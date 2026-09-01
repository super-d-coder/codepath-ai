package com.superd.test.trigger.http;

import com.superd.api.dto.ssh.SshConnectionRequestDTO;
import com.superd.api.dto.ssh.SshConnectionResponseDTO;
import com.superd.api.response.Response;
import com.superd.domain.ssh.model.entity.SshConnectionEntity;
import com.superd.domain.ssh.model.valobj.AuthTypeEnum;
import com.superd.domain.ssh.model.valobj.ConnectionStatusEnum;
import com.superd.domain.ssh.service.ISshConnectionDomainService;
import com.superd.trigger.http.SshConnectionController;
import com.superd.types.enums.ResponseCode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SshConnectionControllerTest {

    @Mock
    private ISshConnectionDomainService sshConnectionDomainService;

    private SshConnectionController controller;

    @Before
    public void setUp() {
        controller = new SshConnectionController();
        ReflectionTestUtils.setField(controller, "sshConnectionDomainService", sshConnectionDomainService);
    }

    @Test
    // 测试：创建连接成功时，控制器应返回 SUCCESS，并附带转换后的响应 DTO
    public void test_createConnection_success() {
        SshConnectionRequestDTO requestDTO = buildRequestDTO();

        Response<SshConnectionResponseDTO> response = controller.createConnection(requestDTO);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(ResponseCode.SUCCESS.getInfo(), response.getInfo());
        assertNotNull(response.getData());
        assertEquals("demo-conn", response.getData().getConnectionName());
        assertEquals("127.0.0.1", response.getData().getHost());
        verify(sshConnectionDomainService, times(1)).createConnection(any(SshConnectionEntity.class), any());
    }

    @Test
    // 测试：创建连接参数非法时，控制器应返回 ILLEGAL_PARAMETER，并保留抛出的错误信息
    public void test_createConnection_illegalArgument() {
        SshConnectionRequestDTO requestDTO = buildRequestDTO();
        doThrow(new IllegalArgumentException("连接名称不能为空")).when(sshConnectionDomainService)
                .createConnection(any(SshConnectionEntity.class), any());

        Response<SshConnectionResponseDTO> response = controller.createConnection(requestDTO);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        assertEquals("连接名称不能为空", response.getInfo());
    }

    @Test
    // 测试：更新连接成功时，控制器应调用 domain service 更新，并返回更新后的结果
    public void test_updateConnection_success() {
        SshConnectionRequestDTO requestDTO = buildRequestDTO();
        SshConnectionEntity updatedEntity = buildEntity();
        when(sshConnectionDomainService.getConnection("conn-001")).thenReturn(updatedEntity);

        Response<SshConnectionResponseDTO> response = controller.updateConnection(requestDTO);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals("conn-001", response.getData().getConnectionId());
        verify(sshConnectionDomainService, times(1)).updateConnection(any(SshConnectionEntity.class), any());
        verify(sshConnectionDomainService, times(1)).getConnection("conn-001");
    }

    @Test
    // 测试：查询单个连接不存在时，控制器应返回 ILLEGAL_PARAMETER，提示“连接不存在”
    public void test_getConnection_notFound() {
        when(sshConnectionDomainService.getConnection("missing")).thenReturn(null);

        Response<SshConnectionResponseDTO> response = controller.getConnection("missing");

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        assertEquals("连接不存在", response.getInfo());
    }

    @Test
    // 测试：查询连接列表时，控制器会同步真实连接状态，并按实际状态返回 DTO 列表
    public void test_getConnectionList_shouldSyncStatus() {
        SshConnectionEntity entity = buildEntity();
        entity.setStatus(ConnectionStatusEnum.DISCONNECTED);
        when(sshConnectionDomainService.getConnectionList("default")).thenReturn(List.of(entity));
        when(sshConnectionDomainService.isConnected("conn-001")).thenReturn(true);

        Response<List<SshConnectionResponseDTO>> response = controller.getConnectionList("default");

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals(Integer.valueOf(ConnectionStatusEnum.CONNECTED.getCode()), response.getData().get(0).getStatus());
        verify(sshConnectionDomainService, times(1)).isConnected("conn-001");
    }

    @Test
    // 测试：建立连接成功时，控制器返回“连接成功”指示语，并保留 SUCCESS 码
    public void test_connect_success() {
        when(sshConnectionDomainService.connect("conn-001")).thenReturn(true);

        Response<Void> response = controller.connect("conn-001");

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals("连接成功", response.getInfo());
    }

    @Test
    // 测试：建立连接失败时，控制器应返回 UN_ERROR，并返回明确失败提示
    public void test_connect_failed() {
        when(sshConnectionDomainService.connect("conn-001")).thenReturn(false);

        Response<Void> response = controller.connect("conn-001");

        assertEquals(ResponseCode.UN_ERROR.getCode(), response.getCode());
        assertEquals("连接失败，请检查主机地址、端口和认证信息", response.getInfo());
    }

    @Test
    // 测试：断开连接成功时，控制器应返回 SUCCESS，并提示已断开连接
    public void test_disconnect_success() {
        Response<Void> response = controller.disconnect("conn-001");

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals("已断开连接", response.getInfo());
        verify(sshConnectionDomainService, times(1)).disconnect("conn-001");
    }

    @Test
    // 测试：删除连接成功时，控制器返回 SUCCESS
    public void test_deleteConnection_success() {
        Response<Void> response = controller.deleteConnection("conn-001");

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(ResponseCode.SUCCESS.getInfo(), response.getInfo());
        verify(sshConnectionDomainService, times(1)).deleteConnection("conn-001");
    }

    private SshConnectionRequestDTO buildRequestDTO() {
        return SshConnectionRequestDTO.builder()
                .connectionId("conn-001")
                .connectionName("demo-conn")
                .host("127.0.0.1")
                .port(22)
                .username("root")
                .authType(AuthTypeEnum.PASSWORD.getCode())
                .password("123456")
                .privateKey(null)
                .userId("u-001")
                .connectTimeout(10)
                .keepaliveInterval(60)
                .startupCommand("ls")
                .compression(false)
                .strictHostKeyCheck(true)
                .build();
    }

    private SshConnectionEntity buildEntity() {
        return SshConnectionEntity.builder()
                .connectionId("conn-001")
                .connectionName("demo-conn")
                .host("127.0.0.1")
                .port(22)
                .username("root")
                .authType(AuthTypeEnum.PASSWORD)
                .password("123456")
                .privateKey(null)
                .encrypted(1)
                .status(ConnectionStatusEnum.CONNECTED)
                .userId("u-001")
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2025, 1, 1, 10, 5, 0))
                .build();
    }
}
