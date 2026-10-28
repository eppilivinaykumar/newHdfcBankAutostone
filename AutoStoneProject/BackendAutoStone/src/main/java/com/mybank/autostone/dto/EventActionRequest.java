package com.mybank.autostone.dto;

import lombok.*;

/**
 * Optional body for /event/* calls.
 * All fields optional - frontend can POST with no body at all for POC.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventActionRequest {

    private String triggeredBy;   // username, defaults to "MANUAL" if not supplied

    private String remarks;       // optional note, e.g. reason for stop/pause

    private String event;         // operator event name/payload for Send Event
}
