-- 模型配置（多端点：每个模型独立 base-url/key/模型名）
CREATE TABLE model_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    base_url    VARCHAR(512) NOT NULL,
    api_key     VARCHAR(512) NOT NULL,
    model       VARCHAR(128) NOT NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_config_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent 关联模型配置（为空则回退全局 .env 模型）
ALTER TABLE agent ADD COLUMN model_config_id BIGINT NULL;
ALTER TABLE agent ADD CONSTRAINT fk_agent_model_config
    FOREIGN KEY (model_config_id) REFERENCES model_config (id);

-- MCP server 配置
CREATE TABLE mcp_server (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    transport   VARCHAR(16)  NOT NULL DEFAULT 'sse',  -- sse | stdio
    url         VARCHAR(512),                          -- for sse
    command     VARCHAR(512),                          -- for stdio
    args        VARCHAR(1024),                         -- for stdio, space-separated
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent ↔ MCP server
CREATE TABLE agent_mcp (
    agent_id   BIGINT NOT NULL,
    mcp_id     BIGINT NOT NULL,
    PRIMARY KEY (agent_id, mcp_id),
    CONSTRAINT fk_amcp_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_amcp_mcp   FOREIGN KEY (mcp_id) REFERENCES mcp_server (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工具调用日志（监控：成功率/耗时）
CREATE TABLE tool_call_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_name   VARCHAR(128) NOT NULL,
    source      VARCHAR(16)  NOT NULL DEFAULT 'http',  -- http | mcp
    success     TINYINT(1)   NOT NULL,
    duration_ms BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tool_call_log_tool ON tool_call_log (tool_name);
CREATE INDEX idx_tool_call_log_created ON tool_call_log (created_at);
