-- 为已存在的 InfoFlow GitHub 表补充 README 字段。
-- 执行前请确认当前数据库已选择 codepath_ai。
ALTER TABLE infoflow_hot_repo
    ADD COLUMN readme_content MEDIUMTEXT NULL COMMENT 'README Markdown 内容',
    ADD COLUMN readme_url VARCHAR(1000) NULL COMMENT 'README 页面地址',
    ADD COLUMN readme_status VARCHAR(32) NULL COMMENT 'README 状态',
    ADD COLUMN readme_fetched_at DATETIME NULL COMMENT 'README 获取时间';
