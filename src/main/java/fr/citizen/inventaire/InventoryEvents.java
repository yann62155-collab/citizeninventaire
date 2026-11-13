package fr.citizen.inventaire;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryEvents {
    /** Joueurs en attente de vérification : UUID -> ticks restants. */
    private final Map<UUID, Integer> pendingChecks = new ConcurrentHashMap<>();
    private int autosaveTicks = 0;

    // Déconnexion : priorité maximale pour photographier AVANT que d'autres mods (CharacterSelect) ne touchent à l'inventaire.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pendingChecks.remove(player.getUUID());
        SnapshotStore.saveLogout(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pendingChecks.put(player.getUUID(), Config.RESTORE_DELAY_TICKS.get());
    }

    // Mort : photo avant que les objets ne tombent (utile pour un remboursement manuel). Jamais restaurée automatiquement.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SnapshotStore.saveHistory(player, "mort", false);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            SnapshotStore.saveHistory(p, "arret", false);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        // Vérifications différées après connexion
        Iterator<Map.Entry<UUID, Integer>> it = pendingChecks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left > 0) { e.setValue(left); continue; }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) checkAfterLogin(server, player);
        }

        // Sauvegarde périodique
        int minutes = Config.AUTOSAVE_MINUTES.get();
        if (minutes > 0 && ++autosaveTicks >= minutes * 60 * 20) {
            autosaveTicks = 0;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                SnapshotStore.saveHistory(p, "auto", false);
            }
        }
    }

    private void checkAfterLogin(MinecraftServer server, ServerPlayer player) {
        CompoundTag last = SnapshotStore.readLogout(player);
        if (last == null) return;                       // première connexion de ce personnage

        // Inventaire et coffre de l'Ender sont vérifiés séparément :
        // perdu = vide maintenant ALORS QU'il contenait des objets à la déconnexion.
        int invLost = last.getInt("Stacks");
        int enderLost = last.getInt("EnderStacks");
        boolean restoreInv = invLost > 0 && SnapshotStore.isEmpty(player);
        boolean restoreEnder = enderLost > 0 && SnapshotStore.isEnderEmpty(player);
        boolean restoreXp = SnapshotStore.hasXp(last) && SnapshotStore.hasNoXp(player);
        if (!restoreInv && !restoreEnder && !restoreXp) return;

        String name = player.getGameProfile().getName();
        String who = name + " (" + player.getDisplayName().getString() + ")";
        String what = describe(restoreInv, invLost, restoreEnder, enderLost, restoreXp, last.getInt("XpLevel"));

        if (!Config.AUTO_RESTORE.get()) {
            alert(server, who + " est revenu avec " + what + " vide(s). Utilise /inventaire liste " + name, ChatFormatting.RED);
            return;
        }

        SnapshotStore.apply(player, last, restoreInv, restoreEnder, restoreXp);
        SnapshotStore.saveHistory(player, "restauration-auto", false);
        player.sendSystemMessage(Component.literal("[Citizen] Restauré : " + what + ".").withStyle(ChatFormatting.GOLD));
        alert(server, "Restauration automatique pour " + who + " : " + what + ".", ChatFormatting.YELLOW);
    }

    static String describe(boolean inv, int invStacks, boolean ender, int enderStacks, boolean xp, int xpLevel) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (inv) parts.add("inventaire (" + invStacks + " piles)");
        if (ender) parts.add("coffre de l'Ender (" + enderStacks + " piles)");
        if (xp) parts.add("expérience (niveau " + xpLevel + ")");
        return String.join(" + ", parts);
    }

    static void alert(MinecraftServer server, String msg, ChatFormatting color) {
        CitizenInventaire.LOGGER.warn(msg);
        Component c = Component.literal("[Citizen Inventaire] " + msg).withStyle(color);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) p.sendSystemMessage(c);
        }
    }
}
