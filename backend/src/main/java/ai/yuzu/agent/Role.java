package ai.yuzu.agent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static ai.yuzu.agent.Permission.ASK_USER;
import static ai.yuzu.agent.Permission.CHAT_MENTION_ALL;
import static ai.yuzu.agent.Permission.CHAT_POST;
import static ai.yuzu.agent.Permission.CODE_EXECUTE;
import static ai.yuzu.agent.Permission.CODE_WRITE;
import static ai.yuzu.agent.Permission.EMAIL_READ;
import static ai.yuzu.agent.Permission.EMAIL_SEND;
import static ai.yuzu.agent.Permission.FILE_READ;
import static ai.yuzu.agent.Permission.FILE_WRITE;
import static ai.yuzu.agent.Permission.MEMORY_RECALL;
import static ai.yuzu.agent.Permission.TASK_APPROVE;
import static ai.yuzu.agent.Permission.TASK_ASSIGN;
import static ai.yuzu.agent.Permission.TRADE_EXECUTE;
import static ai.yuzu.agent.Permission.TRADE_VIEW;
import static ai.yuzu.agent.Permission.WEB_BROWSE;

/**
 * v0.0.6 🍊 Job presets: title, work scope, persona, default permissions and limits.
 *
 * <p>The scope and persona texts are part of every prompt, so they define how coworkers collaborate:
 * the PM owns intake and assignment; everyone else works on assigned tickets and reports back.</p>
 */
public enum Role {
    PROJECT_MANAGER("Project Manager",
            "Owns intake, planning, tickets, assignment, follow-up and approvals.",
            "Intake of every human request, breaking work into tickets, assigning tickets to the right coworker, "
                    + "tracking progress, unblocking people, approving finished work and reporting to humans. "
                    + "Does not write code, send customer e-mails or trade.",
            "Organized, decisive and friendly. You own intake: human requests normally go through you. You create "
                    + "tickets, assign them to the best coworker with an @mention, follow up, and approve finished "
                    + "task lists. If a coworker starts working on a human's request that was never assigned, "
                    + "@mention them, ask them to pause, and route it through a ticket.",
            EnumSet.of(CHAT_POST, CHAT_MENTION_ALL, ASK_USER, TASK_ASSIGN, TASK_APPROVE, WEB_BROWSE, EMAIL_READ,
                    FILE_READ, MEMORY_RECALL),
            new Limits(List.of(), 10, 0, 0, 50)),
    RESEARCHER("Data Researcher",
            "Web research, competitive analysis, data gathering and concise summaries.",
            "Searching the web, reading sources, extracting data, writing research notes and summaries into the "
                    + "workspace, and handing findings to coworkers. Works on tickets assigned by the PM or a human.",
            "Curious and precise. You cite sources, separate facts from guesses, and keep summaries short. You work on "
                    + "assigned tickets and report findings with an @mention to whoever asked.",
            EnumSet.of(CHAT_POST, ASK_USER, WEB_BROWSE, FILE_READ, FILE_WRITE, MEMORY_RECALL),
            new Limits(List.of(), 10, 0, 0, 100)),
    ENGINEER("Software Engineer",
            "Writes and runs code in the sandbox, builds prototypes and pages.",
            "Writing code and files in the workspace, running code in the sandbox, building prototypes, landing pages "
                    + "and scripts. Works only on tickets assigned by the PM or explicitly by a human.",
            "Pragmatic builder. You write small, working increments and explain what you built. You do not pick up "
                    + "unassigned requests; if a human asks you directly, you check with the PM first.",
            EnumSet.of(CHAT_POST, ASK_USER, CODE_WRITE, CODE_EXECUTE, FILE_READ, FILE_WRITE, WEB_BROWSE,
                    MEMORY_RECALL),
            new Limits(List.of(), 10, 0, 0, 200)),
    CUSTOMER_LIAISON("Customer Liaison",
            "Customer communication by e-mail with allowlisted domains.",
            "Reading the mailbox, drafting and sending e-mails to customers on allowlisted domains, summarizing "
                    + "customer feedback for the team. Works on assigned tickets.",
            "Warm, clear and careful. You never promise what the team has not confirmed, and you ask a human before "
                    + "sending anything unusual.",
            EnumSet.of(CHAT_POST, ASK_USER, EMAIL_READ, EMAIL_SEND, FILE_READ, MEMORY_RECALL),
            new Limits(List.of("acme.test", "example.com"), 10, 0, 0, 50)),
    FINANCE_ANALYST("Finance Analyst",
            "Market data, portfolio analysis and simulated trades within limits.",
            "Watching simulated market data, analyzing the portfolio and placing simulated trades within the "
                    + "notional limits. Trades above the auto-approve threshold need a human approval.",
            "Cautious and numbers-driven. You explain risks before acting and never exceed your limits.",
            EnumSet.of(CHAT_POST, ASK_USER, TRADE_VIEW, TRADE_EXECUTE, FILE_READ, WEB_BROWSE, MEMORY_RECALL),
            new Limits(List.of(), 10, 1_000, 200, 50));

    private final String title;
    private final String description;
    private final String scopeText;
    private final String persona;
    private final Set<Permission> permissions;
    private final Limits limits;

    Role(String title, String description, String scopeText, String persona, Set<Permission> permissions,
         Limits limits) {
        this.title = title;
        this.description = description;
        this.scopeText = scopeText;
        this.persona = persona;
        this.permissions = permissions;
        this.limits = limits;
    }

    /** v0.0.6 🍊 Default job title. */
    public String title() {
        return title;
    }

    /** v0.0.6 🍊 One-line description shown in the UI. */
    public String description() {
        return description;
    }

    /** v0.0.6 🍊 Default work scope (what is in and out of this job). */
    public String scopeText() {
        return scopeText;
    }

    /** v0.0.6 🍊 Default persona (how this coworker behaves). */
    public String persona() {
        return persona;
    }

    /** v0.0.6 🍊 Default permission scope. */
    public PermissionScope defaultScope() {
        return new PermissionScope(permissions, limits);
    }

    /** v0.0.6 🍊 API view of the preset. */
    public RolePresetView toView() {
        PermissionScope scope = defaultScope();
        return new RolePresetView(this, title, description, scopeText, scope.names(), scope.limits());
    }

    /** v0.0.6 🍊 API shape of a preset (contract type {@code RolePreset}). */
    public record RolePresetView(Role role, String title, String description, String scopeText,
                                 List<String> permissions, Limits limits) {
    }
}
