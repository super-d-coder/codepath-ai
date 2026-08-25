package com.superd.domain.ssh.adapter.repository;

import com.superd.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.superd.domain.ssh.model.entity.SshConnectionEntity;

import java.util.List;

public interface ISshConnectionRepository {

    void saveConnection(SshConnectionEntity entity);

    void updateConnection(SshConnectionEntity entity);

    void deleteConnection(String connectionId);

    SshConnectionEntity queryConnectionById(String connectionId);

    List<SshConnectionEntity> queryConnectionListByUserId(String userId);

    void saveConnectionConfig(SshConnectionConfigEntity entity);

    SshConnectionConfigEntity queryConnectionConfigById(String connectionId);
}