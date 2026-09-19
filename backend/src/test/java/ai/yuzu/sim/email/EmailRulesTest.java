package ai.yuzu.sim.email;

import ai.yuzu.agent.Limits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.11 🍊 Pure e-mail rules: exact allowlisted domains, hourly limit boundaries, address syntax and sender identity. */
class EmailRulesTest {

    private static final Limits LIAISON = new Limits(List.of("acme.test", "Example.COM"), 3, 0, 0, 50);

    /** v0.0.11 🍊 Allowlisted recipients pass and come back trimmed and lowercased. */
    @Test
    void allowlistedRecipientsPass() {
        EmailRules.Result result = EmailRules.check(LIAISON, List.of("Dana.Kim@ACME.test", " procurement@example.com "), 0);
        assertThat(result.allowed()).isTrue();
        assertThat(result.blocked()).isFalse();
        assertThat(result.reasons()).isEmpty();
        assertThat(result.recipients()).containsExactly("dana.kim@acme.test", "procurement@example.com");
    }

    /** v0.0.11 🍊 Only an exact domain matches: subdomains, suffix tricks and look-alikes are refused. */
    @ParameterizedTest
    @ValueSource(strings = {"eve@evil.test", "bob@mail.acme.test", "bob@acme.test.evil.com", "bob@notacme.test",
            "bob@acme.testx", "bob@example.co"})
    void onlyExactAllowlistedDomainsPass(String address) {
        EmailRules.Result result = EmailRules.check(LIAISON, List.of(address), 0);
        assertThat(result.blocked()).isTrue();
        assertThat(result.has(EmailRules.Code.DOMAIN_NOT_ALLOWED)).isTrue();
        assertThat(result.reasons()).singleElement().asString()
                .contains(EmailRules.domainOf(address)).contains("allowed: acme.test, example.com");
    }

    /** v0.0.11 🍊 Malformed, display-name, header-injection and homoglyph addresses are invalid. */
    @ParameterizedTest
    @ValueSource(strings = {"bob", "bob@", "@acme.test", "bob@@acme.test", "bob@acme", "bob@acme..test",
            "bob smith@acme.test", "bob@acme.test\nbcc: eve@evil.test", "\"Bob\" <bob@acme.test>", "bob@\u0430cme.test",
            ".bob@acme.test", "bob.@acme.test", "bob@-acme.test", "bob@acme.t"})
    void malformedAddressesAreInvalid(String address) {
        EmailRules.Result result = EmailRules.check(LIAISON, List.of(address), 0);
        assertThat(result.has(EmailRules.Code.INVALID_ADDRESS)).isTrue();
        assertThat(result.has(EmailRules.Code.DOMAIN_NOT_ALLOWED)).isFalse();
    }

    /** v0.0.11 🍊 The hourly limit blocks at exactly the limit; 0 disables sending; negatives count as 0. */
    @Test
    void hourlyLimitBoundaries() {
        assertThat(EmailRules.check(LIAISON, List.of("a@acme.test"), 2).allowed()).isTrue();
        EmailRules.Result full = EmailRules.check(LIAISON, List.of("a@acme.test"), 3);
        assertThat(full.has(EmailRules.Code.HOURLY_LIMIT)).isTrue();
        assertThat(full.reasons()).singleElement().asString().contains("3 of 3");
        assertThat(EmailRules.check(LIAISON, List.of("a@acme.test"), 99).blocked()).isTrue();
        assertThat(EmailRules.check(LIAISON, List.of("a@acme.test"), -5).allowed()).isTrue();

        Limits disabled = new Limits(List.of("acme.test"), 0, 0, 0, 50);
        EmailRules.Result off = EmailRules.check(disabled, List.of("a@acme.test"), 0);
        assertThat(off.has(EmailRules.Code.HOURLY_LIMIT)).isTrue();
        assertThat(off.reasons()).singleElement().asString().contains("disabled");
    }

    /** v0.0.11 🍊 Empty or missing recipient lists are refused; more than 10 recipients too. */
    @Test
    void recipientCountRules() {
        assertThat(EmailRules.check(LIAISON, List.of(), 0).has(EmailRules.Code.NO_RECIPIENTS)).isTrue();
        assertThat(EmailRules.check(LIAISON, null, 0).has(EmailRules.Code.NO_RECIPIENTS)).isTrue();
        assertThat(EmailRules.check(LIAISON, List.of(" ", ""), 0).has(EmailRules.Code.NO_RECIPIENTS)).isTrue();
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("user" + i + "@acme.test");
        }
        assertThat(EmailRules.check(LIAISON, eleven, 0).has(EmailRules.Code.TOO_MANY_RECIPIENTS)).isTrue();
        assertThat(EmailRules.check(LIAISON, eleven.subList(0, 10), 0).allowed()).isTrue();
    }

    /** v0.0.11 🍊 Duplicates collapse (case-insensitively) and "a, b" entries are split. */
    @Test
    void duplicatesCollapseAndListsSplit() {
        EmailRules.Result result = EmailRules.check(LIAISON,
                List.of("a@acme.test", "A@ACME.TEST", "a@acme.test, b@example.com; c@acme.test"), 0);
        assertThat(result.allowed()).isTrue();
        assertThat(result.recipients()).containsExactly("a@acme.test", "b@example.com", "c@acme.test");
    }

    /** v0.0.11 🍊 Without allowed domains nobody can be e-mailed. */
    @Test
    void noAllowedDomainsBlocksEverything() {
        EmailRules.Result result = EmailRules.check(Limits.defaults(), List.of("a@acme.test"), 0);
        assertThat(result.has(EmailRules.Code.DOMAIN_NOT_ALLOWED)).isTrue();
        assertThat(result.reasons()).singleElement().asString().contains("may not e-mail any domain");
        assertThat(EmailRules.check(null, List.of("a@acme.test"), 0).blocked()).isTrue();
    }

    /** v0.0.11 🍊 Every broken rule is reported, not only the first one. */
    @Test
    void everyBrokenRuleIsReported() {
        EmailRules.Result result = EmailRules.check(LIAISON, List.of("bad", "eve@evil.test", "x@evil.test"), 3);
        assertThat(result.violations()).extracting(EmailRules.Violation::code).containsExactly(
                EmailRules.Code.INVALID_ADDRESS, EmailRules.Code.DOMAIN_NOT_ALLOWED, EmailRules.Code.HOURLY_LIMIT);
    }

    /** v0.0.11 🍊 An agent may only send as itself (blank means itself). */
    @Test
    void senderMustBeTheAgentItself() {
        assertThat(EmailRules.checkSender("pomelo@citrushq.test", null)).isEmpty();
        assertThat(EmailRules.checkSender("pomelo@citrushq.test", "  ")).isEmpty();
        assertThat(EmailRules.checkSender("pomelo@citrushq.test", " POMELO@citrushq.test ")).isEmpty();
        assertThat(EmailRules.checkSender("pomelo@citrushq.test", "ceo@acme.test"))
                .hasValueSatisfying(v -> {
                    assertThat(v.code()).isEqualTo(EmailRules.Code.SENDER_MISMATCH);
                    assertThat(v.message()).contains("pomelo@citrushq.test").contains("ceo@acme.test");
                });
    }

    /** v0.0.11 🍊 Helpers: domains, validity limits and safe explanations. */
    @Test
    void helpers() {
        assertThat(EmailRules.domainOf("a@B.test")).isEqualTo("b.test");
        assertThat(EmailRules.domainOf("nope")).isEmpty();
        assertThat(EmailRules.domainOf(null)).isEmpty();
        assertThat(EmailRules.isValidAddress(null)).isFalse();
        assertThat(EmailRules.isValidAddress("a".repeat(250) + "@acme.test")).isFalse();
        assertThat(EmailRules.isValidAddress("first.last+tag@sub.acme.test")).isTrue();
        String reason = EmailRules.check(LIAISON, List.of("x\u0007y@acme.test"), 0).reasons().get(0);
        assertThat(reason).contains("\\u0007").doesNotContain("\u0007");
    }
}
