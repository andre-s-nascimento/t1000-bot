/* (c) 2026 | 15/05/2026 */
package net.ddns.adambravo79.tmill.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void deveCriarChatCompletionResponse() {
        Message msg = new Message("user", "conteudo");
        Choice choice = new Choice(msg);
        ChatCompletionResponse resp = new ChatCompletionResponse(List.of(choice));

        assertThat(resp.choices()).hasSize(1);
        assertThat(resp.choices().get(0).message().content()).isEqualTo("conteudo");
    }

    @Test
    void deveCriarChoice() {
        Message msg = new Message("system", "ola");
        Choice choice = new Choice(msg);

        assertThat(choice.message().role()).isEqualTo("system");
        assertThat(choice.message().content()).isEqualTo("ola");
    }

    @Test
    void deveCriarCountryProviders() {
        Provider provider = new Provider("Netflix", 1, "/logo.png");
        CountryProviders cp = new CountryProviders(List.of(provider));

        assertThat(cp.flatrate()).hasSize(1);
        assertThat(cp.flatrate().get(0).name()).isEqualTo("Netflix");
    }

    @Test
    void deveCriarCreditsResponse() {
        CastRecord cast = new CastRecord("Ator", "Personagem");
        CreditsResponse resp = new CreditsResponse(List.of(cast), List.of());

        assertThat(resp.cast()).hasSize(1);
        assertThat(resp.cast().get(0).character()).isEqualTo("Personagem");
    }

    @Test
    void deveCriarMessage() {
        Message msg = new Message("assistant", "resposta");
        assertThat(msg.role()).isEqualTo("assistant");
        assertThat(msg.content()).isEqualTo("resposta");
    }

    @Test
    void deveCriarMovieResponse() {
        MovieRecord movieRecord =
                new MovieRecord(
                        1L,
                        "Titulo",
                        "Title",
                        "2020",
                        "overview",
                        10.0,
                        8.0,
                        "/poster",
                        List.of("US"));
        MovieResponse resp = new MovieResponse(List.of(movieRecord));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).title()).isEqualTo("Titulo");
    }

    @Test
    void deveCriarProvider() {
        Provider provider = new Provider("Prime", 2, "/logo2.png");
        assertThat(provider.name()).isEqualTo("Prime");
        assertThat(provider.id()).isEqualTo(2);
        assertThat(provider.logoPath()).isEqualTo("/logo2.png");
    }

    @Test
    void deveCriarTranscriptionResponse() {
        TranscriptionResponse resp = new TranscriptionResponse("texto transcrito");
        assertThat(resp.text()).isEqualTo("texto transcrito");
    }

    @Test
    void deveCriarWatchProviderResponse() {
        Provider provider = new Provider("Disney+", 3, "/logo3.png");
        CountryProviders cp = new CountryProviders(List.of(provider));
        WatchProviderResponse resp = new WatchProviderResponse(Map.of("BR", cp));

        assertThat(resp.results()).containsKey("BR");
        assertThat(resp.results().get("BR").flatrate().get(0).name()).isEqualTo("Disney+");
    }
}
