CREATE TABLE `ssh_connection_config` (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                                         `connection_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联的连接ID',
                                         `connect_timeout` int NOT NULL DEFAULT '10' COMMENT '连接超时时间(秒)',
                                         `keepalive_interval` int NOT NULL DEFAULT '60' COMMENT '保活间隔(秒)',
                                         `startup_command` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '连接后执行的启动命令',
                                         `compression` tinyint NOT NULL DEFAULT '0' COMMENT '是否压缩:0-否,1-是',
                                         `strict_host_key_check` tinyint NOT NULL DEFAULT '1' COMMENT '严格主机密钥检查:0-否,1-是',
                                         `known_hosts` longtext COLLATE utf8mb4_unicode_ci COMMENT '已知主机密钥列表',
                                         `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         PRIMARY KEY (`id`),
                                         UNIQUE KEY `uk_connection_id` (`connection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SSH连接高级配置表';