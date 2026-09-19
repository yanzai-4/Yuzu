package ai.yuzu.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.1 🍊 Verifies the id formats required by the spec. */
class IdGenTest {

    /** v0.0.1 🍊 Agent ids are agent-xxxx and never the reserved agent-0000. */
    @Test
    void agentIdsMatchFormatAndAreNeverReserved() {
        for (int i = 0; i < 20_000; i++) {
            AgentId id = IdGen.newAgentId();
            assertThat(id.value()).matches("^agent-[0-9a-f]{4}$");
            assertThat(id.isSystem()).isFalse();
        }
    }

    /** v0.0.1 🍊 Record ids are <name>-<agentHex>-<10hex> and embed the owner hex. */
    @Test
    void recordIdsEmbedOwnerHex() {
        AgentId owner = AgentId.of("agent-3fa9");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String id = IdGen.recordId(DataName.TASK_ITEM, owner);
            assertThat(id).matches("^item-3fa9-[0-9a-f]{10}$");
            assertThat(IdGen.isRecordId(id)).isTrue();
            seen.add(id);
        }
        assertThat(seen).hasSizeGreaterThan(9_990);
    }

    /** v0.0.1 🍊 System-owned records use the reserved 0000 segment. */
    @Test
    void systemRecordsUseZeroSegment() {
        assertThat(IdGen.recordId(DataName.MESSAGE, AgentId.SYSTEM)).startsWith("msg-0000-");
    }

    /** v0.0.1 🍊 Malformed agent ids are rejected. */
    @Test
    void invalidAgentIdsAreRejected() {
        assertThatThrownBy(() -> AgentId.of("agent-XYZ1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentId.of("agent-12345")).isInstanceOf(IllegalArgumentException.class);
        assertThat(AgentId.isValid("agent-00af")).isTrue();
    }

    /** v0.0.1 🍊 Human and room ids follow the same 4-hex shape. */
    @Test
    void userAndRoomIds() {
        assertThat(IdGen.newUserId()).matches("^user-[0-9a-f]{4}$").isNotEqualTo("user-0000");
        assertThat(IdGen.newRoomId()).matches("^room-[0-9a-f]{4}$");
    }
}
