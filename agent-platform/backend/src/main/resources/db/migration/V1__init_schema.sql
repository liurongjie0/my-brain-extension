-- Agent 配置
CREATE TABLE agent (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    avatar        VARCHAR(512),
    system_prompt TEXT,
    model         VARCHAR(128) NOT NULL,
    temperature   DOUBLE       NOT NULL DEFAULT 0.7,
    max_tokens    INT          NOT NULL DEFAULT 2048,
    top_p         DOUBLE       NOT NULL DEFAULT 1.0,
    agent_type    VARCHAR(32)  NOT NULL DEFAULT 'chat',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库
CREATE TABLE knowledge_base (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    embedding_model VARCHAR(128) NOT NULL,
    chunk_size      INT          NOT NULL DEFAULT 800,
    chunk_overlap   INT          NOT NULL DEFAULT 100,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档(元数据;向量在 Redis)
CREATE TABLE document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    filename    VARCHAR(512) NOT NULL,
    file_type   VARCHAR(32),
    status      VARCHAR(32)  NOT NULL DEFAULT 'pending',
    chunk_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工具定义(HTTP 接口型)
CREATE TABLE tool (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(128) NOT NULL,
    description        VARCHAR(1024) NOT NULL,
    method             VARCHAR(16)  NOT NULL,
    url                VARCHAR(1024) NOT NULL,
    headers_json       TEXT,
    params_schema_json TEXT,
    enabled            TINYINT(1)   NOT NULL DEFAULT 1,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent ↔ 知识库
CREATE TABLE agent_knowledge_base (
    agent_id BIGINT NOT NULL,
    kb_id    BIGINT NOT NULL,
    PRIMARY KEY (agent_id, kb_id),
    CONSTRAINT fk_akb_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_akb_kb    FOREIGN KEY (kb_id) REFERENCES knowledge_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent ↔ 工具
CREATE TABLE agent_tool (
    agent_id BIGINT NOT NULL,
    tool_id  BIGINT NOT NULL,
    PRIMARY KEY (agent_id, tool_id),
    CONSTRAINT fk_at_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_at_tool  FOREIGN KEY (tool_id) REFERENCES tool (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 会话
CREATE TABLE conversation (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id   BIGINT       NOT NULL,
    user_id    VARCHAR(128) NOT NULL,
    title      VARCHAR(256),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_conv_agent FOREIGN KEY (agent_id) REFERENCES agent (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息
CREATE TABLE message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         MEDIUMTEXT,
    tool_calls_json MEDIUMTEXT,
    token_usage     INT,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_document_kb ON document (kb_id);
CREATE INDEX idx_conversation_agent ON conversation (agent_id);
CREATE INDEX idx_conversation_user ON conversation (user_id);
CREATE INDEX idx_message_conv ON message (conversation_id);
