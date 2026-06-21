-- 「计划即工具」开关：react Agent 用内置 write_todos 维护可见的任务清单
ALTER TABLE agent ADD COLUMN plan_enabled TINYINT(1) NOT NULL DEFAULT 0;
