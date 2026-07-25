package net.ddns.adambravo79.tmill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

class AdminIpFilterTest {

    private MockMvc mockMvc;
    private AdminIpFilter adminIpFilter;

    @RestController
    static class TestController {
        @GetMapping("/admin/test")
        public String test() {
            return "OK";
        }

        @GetMapping("/public/test")
        public String publicTest() {
            return "OK";
        }
    }

    @BeforeEach
    void setup() {
        adminIpFilter = new AdminIpFilter();
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "");
        adminIpFilter.initFilterBean();

        mockMvc =
                MockMvcBuilders.standaloneSetup(new TestController())
                        .addFilter(adminIpFilter)
                        .build();
    }

    // 1. Rotas não /admin devem passar
    @Test
    void shouldAllowNonAdminPath() throws Exception {
        mockMvc.perform(get("/public/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 2. Quando nenhum IP configurado, /admin deve ser permitido
    @Test
    void shouldAllowAdminWhenNoIpsConfigured() throws Exception {
        mockMvc.perform(get("/admin/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 3. Quando IP permitido, deve passar
    @Test
    void shouldAllowAdminWhenIpAllowed() throws Exception {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "127.0.0.1,192.168.1.1");
        adminIpFilter.initFilterBean();

        mockMvc.perform(
                        get("/admin/test")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("127.0.0.1");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 4. Quando IP não permitido, deve retornar 403
    @Test
    void shouldBlockAdminWhenIpNotAllowed() throws Exception {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "192.168.1.100");
        adminIpFilter.initFilterBean();

        mockMvc.perform(
                        get("/admin/test")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("10.0.0.1");
                                            return request;
                                        }))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Acesso negado")));
    }

    // 5. Deve extrair IP do cabeçalho X-Forwarded-For
    @Test
    void shouldExtractClientIpFromXForwardedFor() throws Exception {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "192.168.1.50");
        adminIpFilter.initFilterBean();

        mockMvc.perform(
                        get("/admin/test")
                                .header("X-Forwarded-For", "192.168.1.50, 10.0.0.1")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("10.0.0.2");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 6. Deve extrair IP do cabeçalho X-Real-Ip quando X-Forwarded-For ausente
    @Test
    void shouldExtractClientIpFromXRealIp() throws Exception {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "192.168.1.60");
        adminIpFilter.initFilterBean();

        mockMvc.perform(
                        get("/admin/test")
                                .header("X-Real-Ip", "192.168.1.60")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("10.0.0.3");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 7. Deve usar remoteAddr quando nenhum header proxy presente
    @Test
    void shouldUseRemoteAddrWhenNoProxyHeaders() throws Exception {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "192.168.1.70");
        adminIpFilter.initFilterBean();

        mockMvc.perform(
                        get("/admin/test")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("192.168.1.70");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 8. Deve extrair corretamente o primeiro IP do X-Forwarded-For
    @Test
    void shouldExtractFirstIpFromXForwardedFor() throws Exception {
        ReflectionTestUtils.setField(adminIpFilter, "allowedIpsStr", "10.0.0.1");
        adminIpFilter.initFilterBean();

        mockMvc.perform(
                        get("/admin/test")
                                .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1, 172.16.0.1")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("1.2.3.4");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 9. Teste da lógica de extração de IP via reflexão
    @Test
    void extractClientIp_shouldReturnXForwardedForFirst() throws Exception {
        java.lang.reflect.Method method =
                AdminIpFilter.class.getDeclaredMethod("extractClientIp", HttpServletRequest.class);
        method.setAccessible(true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");
        String result = (String) method.invoke(adminIpFilter, request);
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void extractClientIp_shouldReturnXRealIpWhenNoXForwardedFor() throws Exception {
        java.lang.reflect.Method method =
                AdminIpFilter.class.getDeclaredMethod("extractClientIp", HttpServletRequest.class);
        method.setAccessible(true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-Ip")).thenReturn("192.168.1.2");
        String result = (String) method.invoke(adminIpFilter, request);
        assertThat(result).isEqualTo("192.168.1.2");
    }

    @Test
    void extractClientIp_shouldReturnRemoteAddrWhenNoHeaders() throws Exception {
        java.lang.reflect.Method method =
                AdminIpFilter.class.getDeclaredMethod("extractClientIp", HttpServletRequest.class);
        method.setAccessible(true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-Ip")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        String result = (String) method.invoke(adminIpFilter, request);
        assertThat(result).isEqualTo("10.0.0.5");
    }
}
