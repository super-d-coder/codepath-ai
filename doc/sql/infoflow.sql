-- InfoFlow MVP schema (MySQL 8+)
-- 新环境执行本文件；已有 infoflow_hot_repo 表请先执行 doc/sql/infoflow_readme_migration.sql。
CREATE TABLE IF NOT EXISTS infoflow_hot_repo (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  repo_full_name VARCHAR(255) NOT NULL COMMENT 'owner/repo', repo_name VARCHAR(255) NOT NULL,
  description TEXT NULL, html_url VARCHAR(500) NOT NULL, stars INT NOT NULL DEFAULT 0, forks INT NOT NULL DEFAULT 0,
  language VARCHAR(64) NULL, topics VARCHAR(1000) NULL, source VARCHAR(64) NOT NULL DEFAULT 'github',
  published_at DATETIME NULL, collected_date DATE NOT NULL, collected_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  readme_content MEDIUMTEXT NULL COMMENT 'README Markdown 内容', readme_url VARCHAR(1000) NULL COMMENT 'README 页面地址', readme_status VARCHAR(32) NULL COMMENT 'README 状态', readme_fetched_at DATETIME NULL COMMENT 'README 获取时间',
  PRIMARY KEY (id), UNIQUE KEY uk_repo_collected_date (repo_full_name, collected_date), KEY idx_collected_at (collected_at), KEY idx_language_collected_at (language, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='InfoFlow GitHub 热门仓库快照';

CREATE TABLE IF NOT EXISTS infoflow_news_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键', external_id VARCHAR(128) NOT NULL, title VARCHAR(1000) NOT NULL,
  url VARCHAR(1000) NOT NULL, author VARCHAR(255) NULL, summary TEXT NULL, source VARCHAR(64) NOT NULL,
  published_at DATETIME NULL, collected_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_source_external_id (source, external_id), KEY idx_source_published_at (source, published_at), KEY idx_collected_at (collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='InfoFlow 行业资讯';
