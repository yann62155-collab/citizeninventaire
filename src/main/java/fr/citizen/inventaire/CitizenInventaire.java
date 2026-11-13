package fr.citizen.inventaire;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Citizen Inventaire : filet de sécurité pour les inventaires des joueurs.
 * - photo de l'inventaire (+ coffre de l'Ender) à la déconnexion, toutes les X minutes, à la mort et à l'arrêt du serveur
 * - photos séparées par personnage (compatible CharacterSelect : la clé est le nom affiché du personnage)
 * - si un joueur revient avec un inventaire vide alors qu'il est parti avec des objets : restauration auto + alerte admins
 * - commandes /inventaire liste | restaurer | sauver
 * Mod 100 % serveur : rien à installer chez les joueurs.
 */
@Mod(CitizenInventaire.MODID)
public class CitizenInventaire {
    public static final String MODID = "citizeninventaire";
    public static final Logger LOGGER = LoggerFactory.getLogger("Citizen Inventaire");

    public CitizenInventaire(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(new InventoryEvents());
        NeoForge.EVENT_BUS.addListener(InventoryCommands::register);
    }
}
