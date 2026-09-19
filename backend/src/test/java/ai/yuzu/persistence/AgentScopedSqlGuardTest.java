package ai.yuzu.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.0.2 🍊 Static isolation guard: every SQL_* constant of every agent-scoped repository must filter by agent_id.
 */
class AgentScopedSqlGuardTest {

    /** v0.0.2 🍊 Scans all AgentScopedRepository subclasses under ai.yuzu. */
    @Test
    void everyAgentScopedStatementFiltersByAgent() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(AgentScopedRepository.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("ai.yuzu");

        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (BeanDefinition candidate : candidates) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class
                        && field.getName().startsWith("SQL_")) {
                    field.setAccessible(true);
                    String sql = (String) field.get(null);
                    checked++;
                    if (!sql.contains("agent_id") || !sql.contains(":agentId")) {
                        violations.add(type.getSimpleName() + "." + field.getName());
                    }
                }
            }
        }
        assertThat(checked).isPositive();
        assertThat(violations).as("SQL constants missing agent_id isolation").isEmpty();
    }
}
