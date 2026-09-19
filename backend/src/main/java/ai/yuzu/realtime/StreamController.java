package ai.yuzu.realtime;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** v0.0.3 🍊 REST entry point of the realtime stream: {@code GET /api/stream?roomId=...}. */
@RestController
@RequestMapping("/api")
public class StreamController {

    private final SseHub hub;

    /** v0.0.3 🍊 Injects the hub. */
    public StreamController(SseHub hub) {
        this.hub = hub;
    }

    /**
     * v0.0.3 🍊 Opens an SSE stream for a room; replays after {@code Last-Event-ID} or {@code after}.
     *
     * @param roomId      room to follow
     * @param after       explicit cursor (used by the first connection after bootstrap)
     * @param lastEventId cursor sent automatically by the browser on reconnect
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "room-0001") String roomId,
                             @RequestParam(defaultValue = "0") long after,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        long resumeFrom = parseCursor(lastEventId, after);
        SseEmitter emitter = new SseEmitter(0L);
        hub.connect(roomId, resumeFrom, new SseEmitterSink(emitter));
        return emitter;
    }

    /** v0.0.3 🍊 Prefers the browser's Last-Event-ID over the query parameter. */
    private static long parseCursor(String lastEventId, long fallback) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
