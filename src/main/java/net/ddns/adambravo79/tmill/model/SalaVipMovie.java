package net.ddns.adambravo79.tmill.model;

public record SalaVipMovie(
        Long id,
        Long snapshotId,
        Integer salaVipNumber, // posição 1..120
        Integer salaVipEpisode, // NOVO: episódio do RapaduraCast (pode repetir)
        String letterboxdSlug,
        String title,
        Integer year,
        String letterboxdUrl,
        String noteText) {
    public SalaVipMovie(
            Integer salaVipNumber,
            Integer salaVipEpisode,
            String letterboxdSlug,
            String title,
            Integer year,
            String letterboxdUrl,
            String noteText) {
        this(
                null,
                null,
                salaVipNumber,
                salaVipEpisode,
                letterboxdSlug,
                title,
                year,
                letterboxdUrl,
                noteText);
    }
}
