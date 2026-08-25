package com.superd.domain.ssh.service;

import com.superd.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.superd.domain.ssh.model.entity.SshConnectionEntity;

import java.util.List;

public interface ISshConnectionDomainService {

    void createConnection(SshConnectionEntity entity, SshConnectionConfigEntity configEntity);
    void updateConnection(SshConnectionEntity entity, SshConnectionConfigEntity configEntity);
    void deleteConnection(String connectionId);
    SshConnectionEntity getConnection(String connectionId);
    List<SshConnectionEntity> getConnectionList(String userId);
    SshConnectionConfigEntity getConnectionConfig(String connectionId);
    boolean connect(String connectionId);
    void disconnect(String connectionId);
    boolean isConnected(String connectionId);
}