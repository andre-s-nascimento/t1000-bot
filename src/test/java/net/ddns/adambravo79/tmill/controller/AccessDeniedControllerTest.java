package net.ddns.adambravo79.tmill.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

class AccessDeniedControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // ViewResolver "vazio" — só registra o nome da view, não tenta renderizar template
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".jsp");

        mockMvc =
                MockMvcBuilders.standaloneSetup(new AccessDeniedController())
                        .setViewResolvers(viewResolver)
                        .build();
    }

    @Test
    @DisplayName("GET /access-denied retorna a view 'access-denied'")
    void accessDenied_returnsView() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk())
                .andExpect(view().name("access-denied"));
    }
}
