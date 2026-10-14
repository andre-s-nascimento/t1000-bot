/* (c) 2026 */
package net.ddns.adambravo79.tmill.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller mínimo usado exclusivamente pelos testes de segurança.
 *
 * <p>É uma classe top-level (não aninhada) para garantir que o {@code @WebMvcTest} consiga
 * registrá-lo corretamente via {@code controllers = TestAdminController.class}.
 *
 * <p>Não é um bean de produção — vive apenas em {@code src/test/java} e é varrido apenas quando
 * explicitamente referenciado por testes.
 */
@RestController
public class TestAdminController {

    @GetMapping("/admin/test")
    public String adminTest() {
        return "ok";
    }

    @PostMapping("/admin/test")
    public String adminTestPost() {
        return "ok";
    }

    @GetMapping("/admin/features/transcription.enabled")
    public String adminFeatures() {
        return "ok";
    }

    @GetMapping("/admin-web/test")
    public String adminWebTest() {
        return "ok";
    }
}
