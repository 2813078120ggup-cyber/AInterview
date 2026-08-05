package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("feedback_ticket_read_state")
public class FeedbackTicketReadState {
    private Long ticketId;
    private Long userId;
    private Long lastReadActivityId;
    private LocalDateTime updatedAt;
}
