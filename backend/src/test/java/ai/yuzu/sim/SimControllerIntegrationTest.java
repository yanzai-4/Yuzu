package ai.yuzu.sim;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Role;
import ai.yuzu.sim.email.FakeMailbox;
import ai.yuzu.sim.market.FakePortfolio;
import ai.yuzu.sim.market.Trade;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.11 🍊 /api/sim/* and the bootstrap snapshot return exactly the contract shapes (Email, Trade, Portfolio). */
@IntegrationTest
@AutoConfigureMockMvc
class SimControllerIntegrationTest {

    private static final Set<String> EMAIL_FIELDS =
            Set.of("id", "agentId", "direction", "from", "to", "subject", "body", "status", "time");
    private static final Set<String> TRADE_REQUIRED =
            Set.of("id", "agentId", "symbol", "side", "qty", "price", "notional", "status", "time");
    private static final Set<String> PORTFOLIO_FIELDS = Set.of("agentId", "cash", "positions");
    private static final Set<String> POSITION_FIELDS = Set.of("symbol", "qty", "avgPrice");
    private static final String COMPACT_TIME = "^[A-Z][a-z]{2} [A-Z][a-z]{2} \\d{1,2}, \\d{1,2}:\\d{2}:\\d{2} [AP]M$";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AgentService agents;

    @Autowired
    private FakeMailbox mailbox;

    @Autowired
    private FakePortfolio portfolio;

    /** v0.0.11 🍊 E-mails, trades and portfolios of a room: field names, number types, times and ordering. */
    @Test
    void restShapesMatchTheContract() throws Exception {
        String room = TestRooms.create(jdbc);
        AgentProfile liaison = agents.create(room, new CreateAgentRequest(Role.CUSTOMER_LIAISON, null, null, null, null,
                null));
        AgentProfile analyst = agents.create(room, new CreateAgentRequest(Role.FINANCE_ANALYST, null, null, null, null,
                new Limits.LimitsPatch(null, null, 50_000.0, 50_000.0, null)));
        agents.create(room, new CreateAgentRequest(Role.ENGINEER, null, null, null, null, null));
        mailbox.inbox(liaison.agentId());
        mailbox.send(liaison.agentId(), null, List.of("dana.kim@acme.test"), "Replacement units", "They ship today.");
        portfolio.executeTrade(analyst.agentId(), "CITR", Trade.Side.BUY, new BigDecimal("5"));
        portfolio.recordTrade(analyst.agentId(), "ZEST", Trade.Side.SELL, BigDecimal.ONE, Trade.Status.BLOCKED,
                "Blocked by a test.", null);

        JsonNode emails = getJson("/api/sim/emails?roomId=" + room);
        assertThat(emails).hasSize(6);
        for (JsonNode email : emails) {
            assertThat(fields(email)).isEqualTo(EMAIL_FIELDS);
            assertThat(email.path("time").asText()).matches(COMPACT_TIME);
            assertThat(email.path("agentId").asText()).isEqualTo(liaison.agentId().value());
        }
        JsonNode newest = emails.get(0);
        assertThat(newest.path("direction").asText()).isEqualTo("OUT");
        assertThat(newest.path("status").asText()).isEqualTo("SENT");
        assertThat(newest.path("to").asText()).isEqualTo("dana.kim@acme.test");
        assertThat(newest.path("from").asText()).isEqualTo(mailbox.addressOf(liaison.agentId()));
        assertThat(getJson("/api/sim/emails?agentId=" + liaison.agentId() + "&limit=2")).hasSize(2);

        JsonNode trades = getJson("/api/sim/trades?roomId=" + room);
        assertThat(trades).hasSize(2);
        for (JsonNode trade : trades) {
            assertThat(fields(trade)).containsAll(TRADE_REQUIRED);
            assertThat(fields(trade)).isSubsetOf(union(TRADE_REQUIRED, Set.of("reason")));
            assertThat(trade.path("qty").isNumber()).isTrue();
            assertThat(trade.path("price").isNumber()).isTrue();
            assertThat(trade.path("notional").isNumber()).isTrue();
            assertThat(trade.path("time").asText()).matches(COMPACT_TIME);
        }
        assertThat(trades.get(0).path("status").asText()).isEqualTo("BLOCKED");
        assertThat(trades.get(0).path("reason").asText()).isEqualTo("Blocked by a test.");
        assertThat(trades.get(1).path("status").asText()).isEqualTo("EXECUTED");
        assertThat(trades.get(1).path("side").asText()).isEqualTo("BUY");
        assertThat(trades.get(1).path("qty").asDouble()).isEqualTo(5.0);

        JsonNode portfolios = getJson("/api/sim/portfolios?roomId=" + room);
        assertThat(portfolios).hasSize(1);
        JsonNode account = portfolios.get(0);
        assertThat(fields(account)).isEqualTo(PORTFOLIO_FIELDS);
        assertThat(account.path("agentId").asText()).isEqualTo(analyst.agentId().value());
        assertThat(account.path("cash").isNumber()).isTrue();
        assertThat(account.path("cash").asDouble()).isLessThan(10_000);
        assertThat(fields(account.path("positions").get(0))).isEqualTo(POSITION_FIELDS);
        assertThat(account.path("positions").get(0).path("symbol").asText()).isEqualTo("CITR");

        JsonNode single = getJson("/api/sim/portfolios?agentId=" + liaison.agentId());
        assertThat(single).hasSize(1);
        assertThat(single.get(0).path("cash").asDouble()).isEqualTo(10_000.0);
        assertThat(single.get(0).path("positions")).isEmpty();

        JsonNode snapshot = getJson("/api/bootstrap?roomId=" + room);
        assertThat(snapshot.path("emails")).hasSize(6);
        assertThat(snapshot.path("trades")).hasSize(2);
        assertThat(snapshot.path("portfolios")).hasSize(1);
    }

    /** v0.0.11 🍊 A trading agent that never traded shows its virtual $10,000 account; others are not listed. */
    @Test
    void tradingAgentsShowAStartingAccount() throws Exception {
        String room = TestRooms.create(jdbc);
        AgentProfile analyst = agents.create(room, new CreateAgentRequest(Role.FINANCE_ANALYST, null, null, null, null,
                null));
        agents.create(room, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null));
        JsonNode portfolios = getJson("/api/sim/portfolios?roomId=" + room);
        assertThat(portfolios).singleElement().satisfies(account -> {
            assertThat(account.path("agentId").asText()).isEqualTo(analyst.agentId().value());
            assertThat(account.path("cash").asDouble()).isEqualTo(10_000.0);
        });
        assertThat(portfolio.findPortfolio(analyst.agentId())).isEmpty();
    }

    /** v0.0.11 🍊 Empty rooms give empty lists; a malformed agent id is BAD_REQUEST. */
    @Test
    void emptyRoomsAndBadAgentIds() throws Exception {
        String room = TestRooms.create(jdbc);
        assertThat(getJson("/api/sim/emails?roomId=" + room)).isEmpty();
        assertThat(getJson("/api/sim/trades?roomId=" + room)).isEmpty();
        assertThat(getJson("/api/sim/portfolios?roomId=" + room)).isEmpty();
        String body = mvc.perform(get("/api/sim/emails?agentId=bogus")).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).path("code").asText()).isEqualTo("BAD_REQUEST");
    }

    /** v0.0.11 🍊 GETs a URL and parses the JSON body (expects 200). */
    private JsonNode getJson(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    /** v0.0.11 🍊 Field names of a JSON object. */
    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** v0.0.11 🍊 Union of two sets. */
    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }
}
