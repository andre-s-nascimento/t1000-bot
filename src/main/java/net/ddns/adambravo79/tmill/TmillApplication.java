/* (c) 2026 | 15/05/2026 */
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
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class TmillApplication {
    public static void main(String[] args) {
        SpringApplication.run(TmillApplication.class, args);
    }
}
