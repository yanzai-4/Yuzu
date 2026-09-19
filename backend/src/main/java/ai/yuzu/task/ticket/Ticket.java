package ai.yuzu.task.ticket;

import ai.yuzu.task.Actor;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** v0.0.20 🍊 API shape of a ticket (contract {@code Ticket}); creator and assignedBy are Java-only (not JSON). */
public record Ticket(String id, String roomId, String title, String detail, TicketStatus status, String creatorName,
                     String assigneeId, String requesterName, String listId, String time, String updatedTime,
                     @JsonIgnore Actor creator, @JsonIgnore Actor assignedBy) {
}
