package com.savoris.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SlotAvailabilityResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime slotTime;

    private int bookedGuests;

    private int maxCapacity;

    /** True when (bookedGuests + 1) <= maxCapacity — at least one more guest can be seated. */
    private boolean available;

    /** The next available slot when this one is at or near capacity, or null. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime suggestedAlternative;
}
