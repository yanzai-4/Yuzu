package ai.yuzu.agent;

/**
 * v0.0.6 🍊 Everything an agent may be allowed to do. Checked in code by tool guards (never by the LLM).
 *
 * <p>High-risk permissions ({@link #isHighRisk()}) additionally pass a second review and limit checks.</p>
 */
public enum Permission {
    CHAT_POST("Post messages in the group chat"),
    CHAT_MENTION_ALL("Mention everyone with @all"),
    ASK_USER("Ask humans multiple-choice questions"),
    TASK_ASSIGN("Create tickets and assign work to coworkers"),
    TASK_APPROVE("Approve finished task lists so they can be archived"),
    WEB_BROWSE("Search and read web pages"),
    EMAIL_READ("Read the simulated mailbox"),
    EMAIL_SEND("Send simulated e-mails to allowlisted domains"),
    TRADE_VIEW("View simulated market data and portfolios"),
    TRADE_EXECUTE("Place simulated trades within limits"),
    CODE_WRITE("Write code files in the workspace"),
    CODE_EXECUTE("Run code in the sandbox"),
    FILE_READ("Read files in the workspace"),
    FILE_WRITE("Write files in the workspace"),
    MEMORY_RECALL("Recall deep memories on demand");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    /** v0.0.6 🍊 Human-readable description (also rendered into prompts). */
    public String description() {
        return description;
    }

    /** v0.0.6 🍊 True for permissions whose actions need multi-layer review (money, outbound mail, code). */
    public boolean isHighRisk() {
        return this == EMAIL_SEND || this == TRADE_EXECUTE || this == CODE_EXECUTE;
    }
}
