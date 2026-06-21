-- 文档原文落库:原先只存内存 Map,进程重启即丢、文档永久卡 pending 且不可重试。
-- 落库后可在重启时重新处理、失败后手动重试。
ALTER TABLE document ADD COLUMN raw_text LONGTEXT NULL;
