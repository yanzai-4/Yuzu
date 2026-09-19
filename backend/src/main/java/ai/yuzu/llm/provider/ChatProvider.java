package ai.yuzu.llm.provider;

import ai.yuzu.common.concurrent.CancelToken;

/** v0.0.8 🍊 A Chat Completions provider (OpenAI-compatible). One HTTP exchange per call; no retries inside. */
public interface ChatProvider {

    /** v0.0.8 🍊 Sends one request and returns the complete answer. */
    LlmResult complete(ProviderEndpoint endpoint, LlmRequest request, CancelToken cancel);

    /** v0.0.8 🍊 Sends one streaming request, forwarding deltas to the sink, and returns the aggregated answer. */
    LlmResult stream(ProviderEndpoint endpoint, LlmRequest request, StreamSink sink, CancelToken cancel);
}
