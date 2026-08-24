package com.superd.test.infrastructure.dao;

import com.superd.infrastructure.dao.ISshConnectionDAO;
import com.superd.infrastructure.dao.po.SshConnectionPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SshConnectionDAOTest {

    @Resource
    private ISshConnectionDAO sshConnectionDAO;

    @Value("${test.ssh.host:127.0.0.1}")
    private String sshHost;

    @Value("${test.ssh.username:test-user}")
    private String sshUsername;

    @Value("${test.ssh.password:test-password}")
    private String sshPassword;

    private SshConnectionPO buildPo(String suffix) {
        return SshConnectionPO.builder()
                .connectionId("test-ssh-" + suffix + "-" + UUID.randomUUID())
                .connectionName("测试连接-" + suffix)
                .host(sshHost)
                .port(22)
                .username(sshUsername)
                .authType(1)
                .password(sshPassword)
                .privateKey(null)
                .encrypted(1)
                .status(0)
                .userId("default")
                .build();
    }

    @Test
    public void test_insert_and_query_by_connection_id() {
        SshConnectionPO po = buildPo("insert");

        sshConnectionDAO.insert(po);
        Assert.assertNotNull("主键不应为空", po.getId());

        SshConnectionPO result = sshConnectionDAO.queryByConnectionId(po.getConnectionId());
        Assert.assertNotNull("查询结果不应为空", result);
        Assert.assertEquals("连接ID不一致", po.getConnectionId(), result.getConnectionId());
        Assert.assertEquals("用户名不一致", po.getUsername(), result.getUsername());
        Assert.assertEquals("host不一致", po.getHost(), result.getHost());

        log.info("insert success, id={}", po.getId());
    }

    @Test
    public void test_update() {
        SshConnectionPO po = buildPo("update");
        sshConnectionDAO.insert(po);

        po.setConnectionName("测试连接-更新后");
        po.setHost("192.168.1.101");
        po.setPort(2222);
        po.setUsername("new-test-user");
        po.setPassword("newPassword456");
        po.setStatus(1);

        sshConnectionDAO.update(po);

        SshConnectionPO result = sshConnectionDAO.queryByConnectionId(po.getConnectionId());
        Assert.assertNotNull("更新后查询结果不应为空", result);
        Assert.assertEquals("更新后的名称不一致", "测试连接-更新后", result.getConnectionName());
        Assert.assertEquals("更新后的host不一致", "192.168.1.101", result.getHost());
        Assert.assertEquals("更新后的端口不一致", Integer.valueOf(2222), result.getPort());
        Assert.assertEquals("更新后的用户名不一致", "new-test-user", result.getUsername());
        Assert.assertEquals("更新后的状态不一致", Integer.valueOf(1), result.getStatus());

        log.info("update success, id={}", po.getId());
    }

    @Test
    public void test_delete_and_query_list_by_user_id() {
        SshConnectionPO po = buildPo("delete");
        sshConnectionDAO.insert(po);

        sshConnectionDAO.delete(po.getConnectionId());

        SshConnectionPO deletedPo = sshConnectionDAO.queryByConnectionId(po.getConnectionId());
        Assert.assertNull("逻辑删除后不应查询到记录", deletedPo);

        List<SshConnectionPO> list = sshConnectionDAO.queryListByUserId("default");
        Assert.assertNotNull("列表不应为空", list);
        Assert.assertFalse("已删除记录不应出现在列表中", list.stream()
                .anyMatch(item -> po.getConnectionId().equals(item.getConnectionId())));

        log.info("delete success, connectionId={}", po.getConnectionId());
    }
}