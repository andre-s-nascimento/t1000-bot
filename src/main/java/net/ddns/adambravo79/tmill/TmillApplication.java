/* (c) 2026 | 09/05/2026 */
package net.ddns.adambravo79.tmill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal da aplicação Tmill.
 *
 * <p>Responsável por: - Inicializar o contexto Spring Boot. - Habilitar execução assíncrona via
 * {@link EnableAsync}, permitindo uso de {@code @Async} em serviços como {@link
 * net.ddns.adambravo79.tmill.service.AudioService}.
 *
 * <p>O método {@link #main(String[])} é o ponto de entrada da aplicação.
 */
@EnableAsync // Habilita o @Async do AudioService
@EnableScheduling // Habilita o @Scheduled do BuildInfoService
@SpringBootApplication(scanBasePackages = "net.ddns.adambravo79.tmill")
public class TmillApplication {

  /**
   * Ponto de entrada da aplicação Spring Boot.
   *
   * @param args argumentos de linha de comando.
   */
  public static void main(String[] args) {
    SpringApplication.run(TmillApplication.class, args);
  }
}
