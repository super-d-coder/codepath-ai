package com.superd.test.infrastructure.dao;

import com.superd.infrastructure.dao.ISshConnectionConfigDAO;
import com.superd.infrastructure.dao.po.SshConnectionConfigPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SshConnectionConfigDAOTest {

    @Resource
    private ISshConnectionConfigDAO sshConnectionConfigDAO;

    private SshConnectionConfigPO buildPo(String suffix) {
        return SshConnectionConfigPO.builder()
                .connectionId("test-config-" + suffix + "-" + UUID.randomUUID())
                .connectTimeout(15)
                .keepaliveInterval(60)
                .startupCommand("echo hello")
                .compression(1)
                .strictHostKeyCheck(1)
                .knownHosts("github.com ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC")
                .build();
    }

    @Test
    public void test_insert_or_update_and_query_by_connection_id() {
        SshConnectionConfigPO po = buildPo("insert");

        sshConnectionConfigDAO.insertOrUpdate(po);

        SshConnectionConfigPO result = sshConnectionConfigDAO.queryByConnectionId(po.getConnectionId());
        Assert.assertNotNull("配置查询结果不应为空", result);
        Assert.assertEquals("connectionId 不一致", po.getConnectionId(), result.getConnectionId());
        Assert.assertEquals("connectTimeout 不一致", po.getConnectTimeout(), result.getConnectTimeout());
        Assert.assertEquals("keepaliveInterval 不一致", po.getKeepaliveInterval(), result.getKeepaliveInterval());
        Assert.assertEquals("startupCommand 不一致", po.getStartupCommand(), result.getStartupCommand());
        Assert.assertEquals("compression 不一致", po.getCompression(), result.getCompression());
        Assert.assertEquals("strictHostKeyCheck 不一致", po.getStrictHostKeyCheck(), result.getStrictHostKeyCheck());
        Assert.assertEquals("knownHosts 不一致", po.getKnownHosts(), result.getKnownHosts());

        log.info("insertOrUpdate success, connectionId={}", po.getConnectionId());
    }

    @Test
    public void test_update_same_connection_id() {
        SshConnectionConfigPO po = buildPo("update");
        sshConnectionConfigDAO.insertOrUpdate(po);

        po.setConnectTimeout(30);
        po.setKeepaliveInterval(120);
        po.setStartupCommand("echo updated");
        po.setCompression(0);
        po.setStrictHostKeyCheck(0);
        po.setKnownHosts("example.com ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC");

        sshConnectionConfigDAO.insertOrUpdate(po);

        SshConnectionConfigPO result = sshConnectionConfigDAO.queryByConnectionId(po.getConnectionId());
        Assert.assertNotNull("更新后查询结果不应为空", result);
        Assert.assertEquals("connectTimeout 更新失败", po.getConnectTimeout(), result.getConnectTimeout());
        Assert.assertEquals("keepaliveInterval 更新失败", po.getKeepaliveInterval(), result.getKeepaliveInterval());
        Assert.assertEquals("startupCommand 更新失败", po.getStartupCommand(), result.getStartupCommand());
        Assert.assertEquals("compression 更新失败", po.getCompression(), result.getCompression());
        Assert.assertEquals("strictHostKeyCheck 更新失败", po.getStrictHostKeyCheck(), result.getStrictHostKeyCheck());
        Assert.assertEquals("knownHosts 更新失败", po.getKnownHosts(), result.getKnownHosts());

        log.info("update same connectionId success, connectionId={}", po.getConnectionId());
    }
}
