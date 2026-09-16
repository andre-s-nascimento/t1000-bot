/* (c) 2026 | 15/09/2026 */
package net.ddns.adambravo79.tmill.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessDeniedController {

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
