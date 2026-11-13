package fr.citizen.inventaire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Stockage : world/citizeninventaire/<uuid>/<personnage>/
 *   - deconnexion.dat         : état exact au moment de la dernière déconnexion (sert à la restauration auto)
 *   - 20261111-221800-123_raison.dat : historique (les plus récentes en premier)
 */
public final class SnapshotStore {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    public static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
    public static final String LOGOUT_FILE = "deconnexion.dat";

    public record Snapshot(Path file, LocalDateTime time, String reason, int stacks, int enderStacks, int xpLevel, CompoundTag data) {}

    private SnapshotStore() {}

    // ---------- clé personnage ----------

    public static String characterKey(ServerPlayer player) {
        String name = Config.PER_CHARACTER.get()
                ? player.getDisplayName().getString()
                : player.getGameProfile().getName();
        String key = name.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]+", "_");
        if (key.isEmpty() || key.equals("_")) key = "compte";
        return key.length() > 48 ? key.substring(0, 48) : key;
    }

    private static Path dir(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(CitizenInventaire.MODID)
                .resolve(player.getUUID().toString())
                .resolve(characterKey(player));
    }

    // ---------- capture / application ----------

    public static int countStacks(ServerPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) n++;
        return n;
    }

    public static int countEnderStacks(ServerPlayer player) {
        int n = 0;
        var ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) if (!ender.getItem(i).isEmpty()) n++;
        return n;
    }

    public static boolean isEnderEmpty(ServerPlayer player) {
        return countEnderStacks(player) == 0;
    }

    public static boolean hasXp(CompoundTag tag) {
        return tag.getInt("XpLevel") > 0 || tag.getFloat("XpProgress") > 0f;
    }

    public static boolean hasNoXp(ServerPlayer player) {
        return player.experienceLevel == 0 && player.experienceProgress <= 0f;
    }

    private static boolean hasContent(CompoundTag tag) {
        return tag.getInt("Stacks") > 0 || tag.getInt("EnderStacks") > 0 || hasXp(tag);
    }

    public static CompoundTag capture(ServerPlayer player, String reason) {
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", player.getInventory().save(new ListTag()));
        tag.put("EnderChest", player.getEnderChestInventory().createTag(player.registryAccess()));
        tag.putString("Reason", reason);
        tag.putString("Character", player.getDisplayName().getString());
        tag.putString("Account", player.getGameProfile().getName());
        tag.putInt("Stacks", countStacks(player));
        tag.putInt("EnderStacks", countEnderStacks(player));
        tag.putInt("XpLevel", player.experienceLevel);
        tag.putFloat("XpProgress", player.experienceProgress);
        tag.putInt("XpTotal", player.totalExperience);
        tag.putLong("Time", System.currentTimeMillis());
        return tag;
    }

    /** Remplace l'inventaire, le coffre de l'Ender et/ou l'expérience du joueur par la photo. */
    public static void apply(ServerPlayer player, CompoundTag tag, boolean inventory, boolean enderChest, boolean xp) {
        if (inventory) {
            var inv = player.getInventory();
            inv.clearContent();
            inv.load(tag.getList("Inventory", Tag.TAG_COMPOUND));
            inv.setChanged();
        }
        if (enderChest && tag.contains("EnderChest")) {
            var ender = player.getEnderChestInventory();
            ender.clearContent();
            ender.fromTag(tag.getList("EnderChest", Tag.TAG_COMPOUND), player.registryAccess());
            ender.setChanged();
        }
        if (xp && tag.contains("XpLevel")) {
            player.experienceProgress = tag.getFloat("XpProgress");
            player.totalExperience = tag.getInt("XpTotal");
            player.setExperienceLevels(tag.getInt("XpLevel")); // force l'envoi de la barre d'XP au client
        }
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    // ---------- fichiers ----------

    /** Ajoute une photo à l'historique. Les photos vides ne sont gardées que si demandées explicitement. */
    public static void saveHistory(ServerPlayer player, String reason, boolean keepIfEmpty) {
        CompoundTag tag = capture(player, reason);
        if (!keepIfEmpty && !hasContent(tag)) return;
        Path d = dir(player);
        String file = LocalDateTime.now().format(FILE_TS) + "_" + reason + ".dat";
        write(d.resolve(file), tag);
        prune(d);
    }

    public static void saveLogout(ServerPlayer player) {
        CompoundTag tag = capture(player, "deconnexion");
        Path d = dir(player);
        write(d.resolve(LOGOUT_FILE), tag);
        if (hasContent(tag)) {
            String file = LocalDateTime.now().format(FILE_TS) + "_deconnexion.dat";
            write(d.resolve(file), tag);
            prune(d);
        }
    }

    public static CompoundTag readLogout(ServerPlayer player) {
        return read(dir(player).resolve(LOGOUT_FILE));
    }

    /** Historique du personnage actuel du joueur, du plus récent au plus ancien. */
    public static List<Snapshot> list(ServerPlayer player) {
        List<Snapshot> out = new ArrayList<>();
        Path d = dir(player);
        if (!Files.isDirectory(d)) return out;
        try (Stream<Path> s = Files.list(d)) {
            s.filter(p -> p.getFileName().toString().endsWith(".dat"))
             .filter(p -> !p.getFileName().toString().equals(LOGOUT_FILE))
             .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
             .forEach(p -> {
                 CompoundTag t = read(p);
                 if (t == null) return;
                 LocalDateTime time = java.time.Instant.ofEpochMilli(t.getLong("Time"))
                         .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                 out.add(new Snapshot(p, time, t.getString("Reason"), t.getInt("Stacks"), t.getInt("EnderStacks"), t.getInt("XpLevel"), t));
             });
        } catch (IOException e) {
            CitizenInventaire.LOGGER.error("Lecture de l'historique impossible : {}", d, e);
        }
        return out;
    }

    private static void prune(Path d) {
        int max = Config.MAX_SNAPSHOTS.get();
        try (Stream<Path> s = Files.list(d)) {
            List<Path> files = s.filter(p -> p.getFileName().toString().endsWith(".dat"))
                    .filter(p -> !p.getFileName().toString().equals(LOGOUT_FILE))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (int i = max; i < files.size(); i++) Files.deleteIfExists(files.get(i));
        } catch (IOException e) {
            CitizenInventaire.LOGGER.error("Nettoyage de l'historique impossible : {}", d, e);
        }
    }

    private static void write(Path file, CompoundTag tag) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(tag, tmp);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            CitizenInventaire.LOGGER.error("Sauvegarde impossible : {}", file, e);
        }
    }

    private static CompoundTag read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            CitizenInventaire.LOGGER.error("Lecture impossible : {}", file, e);
            return null;
        }
    }

    /** Vrai si l'inventaire (sacs, armure, seconde main) ne contient rien. */
    public static boolean isEmpty(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) if (!s.isEmpty()) return false;
        for (ItemStack s : player.getInventory().armor) if (!s.isEmpty()) return false;
        for (ItemStack s : player.getInventory().offhand) if (!s.isEmpty()) return false;
        return true;
    }
}
