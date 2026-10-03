package io.casehub.neocortex.caps.engine;

import com.zaxxer.hikari.HikariDataSource;
import io.casehub.neocortex.caps.*;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SqliteCapsEngine implements CapsEngine, AutoCloseable {

    private final CapsTopology topology;
    private final CapsSettler settler;
    private final CapsWeightUpdater weightUpdater;
    private final DispositionWeightMapper dispositionMapper;
    private final HikariDataSource dataSource;

    public SqliteCapsEngine(String dbPath) {
        this.topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        this.settler = new CapsSettler(topology);
        this.weightUpdater = new CapsWeightUpdater(topology);
        this.dispositionMapper = new DispositionWeightMapper(topology);
        this.dataSource = SqliteDataSourceFactory.create(dbPath, 3, 5000);
        SqliteDataSourceFactory.migrate(dataSource, "classpath:db/caps");
    }

    @Override
    public CapsTopology topology() { return topology; }

    @Override
    public AgentCapsState loadState(String tenantId, String agentId) {
        try (Connection conn = dataSource.getConnection()) {
            long generation = loadGeneration(conn, tenantId, agentId);
            if (generation < 0) return null;
            Map<String, ConnectionWeight> weights = loadWeights(conn, tenantId, agentId);
            Map<String, NodeState> nodeStates = loadNodeStates(conn, tenantId, agentId);
            return new AgentCapsState(agentId, tenantId, generation, weights, nodeStates);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load CAPS state", e);
        }
    }

    @Override
    public void saveState(AgentCapsState state) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                upsertAgent(conn, state.tenantId(), state.agentId(), state.generation());
                replaceWeights(conn, state.tenantId(), state.agentId(), state.weights());
                replaceNodeStates(conn, state.tenantId(), state.agentId(), state.nodeStates());
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save CAPS state", e);
        }
    }

    @Override
    public AgentCapsState initializeAgent(String tenantId, String agentId,
                                           DispositionAxes disposition) {
        AgentCapsState state = dispositionMapper.initializeWeights(tenantId, agentId, disposition);
        saveState(state);
        return state;
    }

    @Override
    public SettlingResult settle(AgentCapsState state,
                                 Map<String, Double> inputActivations) {
        return settler.settle(state, inputActivations);
    }

    @Override
    public AgentCapsState updateWeights(AgentCapsState state,
                                         Map<String, Double> inputActivations,
                                         double outcomeIntensity,
                                         double outcomeValence,
                                         double salienceMultiplier,
                                         String reinforcementSchedule) {
        return weightUpdater.update(state, inputActivations,
            outcomeIntensity, outcomeValence, salienceMultiplier,
            reinforcementSchedule);
    }

    @Override
    public void close() {
        if (dataSource != null) dataSource.close();
    }

    private long loadGeneration(Connection conn, String tenantId, String agentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT generation FROM caps_agent_state WHERE tenant_id = ? AND agent_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("generation") : -1;
        }
    }

    private Map<String, ConnectionWeight> loadWeights(Connection conn, String tenantId, String agentId) throws SQLException {
        Map<String, ConnectionWeight> weights = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT connection_id, excitatory, inhibitory, decay_resistance, precision_val FROM caps_connection_weights WHERE tenant_id = ? AND agent_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                weights.put(rs.getString("connection_id"), new ConnectionWeight(
                    rs.getDouble("excitatory"),
                    rs.getDouble("inhibitory"),
                    rs.getDouble("decay_resistance"),
                    rs.getDouble("precision_val")));
            }
        }
        return weights;
    }

    private Map<String, NodeState> loadNodeStates(Connection conn, String tenantId, String agentId) throws SQLException {
        Map<String, NodeState> nodeStates = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT node_name, activation_threshold, resting_activation FROM caps_node_states WHERE tenant_id = ? AND agent_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                nodeStates.put(rs.getString("node_name"), new NodeState(
                    rs.getDouble("activation_threshold"),
                    rs.getDouble("resting_activation")));
            }
        }
        return nodeStates;
    }

    private void upsertAgent(Connection conn, String tenantId, String agentId, long generation) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO caps_agent_state (tenant_id, agent_id, generation) VALUES (?, ?, ?) ON CONFLICT(tenant_id, agent_id) DO UPDATE SET generation = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ps.setLong(3, generation);
            ps.setLong(4, generation);
            ps.executeUpdate();
        }
    }

    private void replaceWeights(Connection conn, String tenantId, String agentId,
                                 Map<String, ConnectionWeight> weights) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement(
            "DELETE FROM caps_connection_weights WHERE tenant_id = ? AND agent_id = ?")) {
            del.setString(1, tenantId);
            del.setString(2, agentId);
            del.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO caps_connection_weights (tenant_id, agent_id, connection_id, excitatory, inhibitory, decay_resistance, precision_val) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (var entry : weights.entrySet()) {
                ps.setString(1, tenantId);
                ps.setString(2, agentId);
                ps.setString(3, entry.getKey());
                ps.setDouble(4, entry.getValue().excitatory());
                ps.setDouble(5, entry.getValue().inhibitory());
                ps.setDouble(6, entry.getValue().decayResistance());
                ps.setDouble(7, entry.getValue().precision());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceNodeStates(Connection conn, String tenantId, String agentId,
                                    Map<String, NodeState> nodeStates) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement(
            "DELETE FROM caps_node_states WHERE tenant_id = ? AND agent_id = ?")) {
            del.setString(1, tenantId);
            del.setString(2, agentId);
            del.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO caps_node_states (tenant_id, agent_id, node_name, activation_threshold, resting_activation) VALUES (?, ?, ?, ?, ?)")) {
            for (var entry : nodeStates.entrySet()) {
                ps.setString(1, tenantId);
                ps.setString(2, agentId);
                ps.setString(3, entry.getKey());
                ps.setDouble(4, entry.getValue().activationThreshold());
                ps.setDouble(5, entry.getValue().restingActivation());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
