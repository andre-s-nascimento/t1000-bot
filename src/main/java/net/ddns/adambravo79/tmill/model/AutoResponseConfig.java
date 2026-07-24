// AutoResponseConfig.java
package net.ddns.adambravo79.tmill.model;

import java.util.Map;

public record AutoResponseConfig(Map<String, AutoResponseRuleEntry> rules) {}
