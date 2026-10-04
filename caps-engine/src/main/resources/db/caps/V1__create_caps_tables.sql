CREATE TABLE caps_agent_state (
    tenant_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    generation INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, agent_id)
);

CREATE TABLE caps_connection_weights (
    tenant_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    connection_id TEXT NOT NULL,
    excitatory REAL NOT NULL,
    inhibitory REAL NOT NULL,
    decay_resistance REAL NOT NULL,
    precision_val REAL NOT NULL,
    PRIMARY KEY (tenant_id, agent_id, connection_id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES caps_agent_state(tenant_id, agent_id) ON DELETE CASCADE
);

CREATE TABLE caps_node_states (
    tenant_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    node_name TEXT NOT NULL,
    activation_threshold REAL NOT NULL,
    resting_activation REAL NOT NULL,
    PRIMARY KEY (tenant_id, agent_id, node_name),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES caps_agent_state(tenant_id, agent_id) ON DELETE CASCADE
);
