/* (c) 2026 | 20/05/2026 */
package net.ddns.adambravo79.tmill.service;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AutoResponseRule {
    private String response;
    private String animation;
    private LocalTime startTime; // pode ser null
    private LocalTime endTime; // pode ser null
}
