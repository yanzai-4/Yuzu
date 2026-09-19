package ai.yuzu.web;

/** v0.0.26 🍊 Where web content comes from: the real internet or the deterministic demo corpus. */
public enum WebMode {

    /** v0.0.26 🍊 Real browsing: DuckDuckGo's HTML endpoint plus direct page fetches. */
    LIVE,

    /** v0.0.26 🍊 A fixed set of offline pages (including one prompt-injection page) for demos and tests. */
    CORPUS
}
