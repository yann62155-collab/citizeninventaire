package fr.citizen.inventaire;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Fichier généré : world/serverconfig/citizeninventaire-server.toml */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue AUTOSAVE_MINUTES = B
            .comment("Intervalle des sauvegardes automatiques des joueurs connectés, en minutes (0 = désactivé).")
            .defineInRange("autosaveMinutes", 5, 0, 1440);

    public static final ModConfigSpec.IntValue MAX_SNAPSHOTS = B
            .comment("Nombre de photos gardées par personnage (les plus anciennes sont supprimées).")
            .defineInRange("maxSnapshots", 30, 3, 1000);

    public static final ModConfigSpec.BooleanValue AUTO_RESTORE = B
            .comment("Restaurer automatiquement l'inventaire si le joueur revient vide alors qu'il est parti avec des objets.")
            .define("autoRestore", true);

    public static final ModConfigSpec.IntValue RESTORE_DELAY_TICKS = B
            .comment("Délai après la connexion avant la vérification (20 ticks = 1 s). Laisse le temps à CharacterSelect de charger le personnage.")
            .defineInRange("restoreDelayTicks", 100, 1, 1200);

    public static final ModConfigSpec.BooleanValue PER_CHARACTER = B
            .comment("Séparer les photos par personnage (nom affiché). Mettre false si tu retires CharacterSelect.")
            .define("perCharacter", true);

    public static final ModConfigSpec.BooleanValue RESTORE_POSITION = B
            .comment("Remettre le joueur à l'endroit où il s'est déconnecté s'il réapparaît loin (ex. corps de CharacterSelect déplacé).")
            .define("restorePosition", true);

    public static final ModConfigSpec.IntValue POSITION_TOLERANCE = B
            .comment("Distance (en blocs) à partir de laquelle on considère que le joueur a été décalé.")
            .defineInRange("positionTolerance", 32, 2, 10000);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
