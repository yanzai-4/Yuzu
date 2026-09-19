package ai.yuzu.task.list;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.20 🍊 The JSON form of task operations that the planning module parses from model output. */
class TaskOpTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** v0.0.20 🍊 Every operation kind reads from its "op" discriminator. */
    @Test
    void readsEveryKindFromJson() throws Exception {
        List<TaskOp> ops = mapper.readValue("""
                [{"op":"ADD","text":"Collect prices"},
                 {"op":"START","itemId":"item-3fa9-0000000001"},
                 {"op":"CHECK","itemId":"item-3fa9-0000000001","note":"12 sources"},
                 {"op":"STRIKE","itemId":"item-3fa9-0000000002","reason":"out of scope"},
                 {"op":"EDIT_GOAL","goal":"Compare prices","reason":"narrower"},
                 {"op":"NOTE","itemId":"item-3fa9-0000000003","note":"waiting"}]
                """, new TypeReference<>() {
        });
        assertThat(ops).containsExactly(TaskOp.add("Collect prices"), TaskOp.start("item-3fa9-0000000001"),
                TaskOp.check("item-3fa9-0000000001", "12 sources"),
                TaskOp.strike("item-3fa9-0000000002", "out of scope"),
                TaskOp.editGoal("Compare prices", "narrower"), TaskOp.note("item-3fa9-0000000003", "waiting"));
        assertThat(ops).extracting(TaskOp::kind).containsExactly(TaskOp.Kind.values());
    }

    /** v0.0.20 🍊 Writing an operation keeps the discriminator and only the record fields. */
    @Test
    void writesTheDiscriminator() throws Exception {
        assertThat(mapper.writerFor(TaskOp.class).writeValueAsString(TaskOp.check("item-3fa9-0000000001", null)))
                .isEqualTo("{\"op\":\"CHECK\",\"itemId\":\"item-3fa9-0000000001\",\"note\":null}");
    }
}
