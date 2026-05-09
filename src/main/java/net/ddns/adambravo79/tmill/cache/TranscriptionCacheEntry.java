/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill.cache;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TranscriptionCacheEntry {
  private String textoBruto;
  private String textoRefinado;
  private long timestamp;
}
