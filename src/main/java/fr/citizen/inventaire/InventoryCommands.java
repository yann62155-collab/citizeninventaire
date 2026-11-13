package fr.citizen.inventaire;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * /inventaire liste <joueur>               -> historique du personnage actuel du joueur
 * /inventaire restaurer <joueur> <numéro> [tout|inventaire|ender|xp]
 *     -> remet la photo n° X (tout par défaut ; l'état actuel est sauvegardé avant)
 * /inventaire sauver <joueur>              -> photo manuelle
 * Réservé aux op (niveau 2). Le joueur doit être connecté, sur le bon personnage.
 */
public final class InventoryCommands {
    private InventoryCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("inventaire")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("liste")
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(InventoryCommands::list)))
                .then(Commands.literal("restaurer")
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .then(Commands.argument("numero", IntegerArgumentType.integer(1))
                                        .executes(c -> restore(c, true, true, true))
                                        .then(Commands.literal("tout").executes(c -> restore(c, true, true, true)))
                                        .then(Commands.literal("inventaire").executes(c -> restore(c, true, false, false)))
                                        .then(Commands.literal("ender").executes(c -> restore(c, false, true, false)))
                                        .then(Commands.literal("xp").executes(c -> restore(c, false, false, true))))))
                .then(Commands.literal("sauver")
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(InventoryCommands::save))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        List<SnapshotStore.Snapshot> snaps = SnapshotStore.list(target);
        CommandSourceStack src = ctx.getSource();
        if (snaps.isEmpty()) {
            src.sendFailure(Component.literal("Aucune sauvegarde pour " + target.getDisplayName().getString()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Sauvegardes de " + target.getDisplayName().getString()
                + " (" + target.getGameProfile().getName() + ") :").withStyle(ChatFormatting.GOLD), false);
        int shown = Math.min(snaps.size(), 15);
        for (int i = 0; i < shown; i++) {
            SnapshotStore.Snapshot s = snaps.get(i);
            int n = i + 1;
            src.sendSuccess(() -> Component.literal(" #" + n + "  " + s.time().format(SnapshotStore.DISPLAY_TS)
                    + "  " + s.reason() + "  — inv " + s.stacks() + " / ender " + s.enderStacks() + " piles / XP niv. " + s.xpLevel()), false);
        }
        if (snaps.size() > shown) {
            int more = snaps.size() - shown;
            src.sendSuccess(() -> Component.literal(" … et " + more + " plus anciennes").withStyle(ChatFormatting.GRAY), false);
        }
        return snaps.size();
    }

    private static int restore(CommandContext<CommandSourceStack> ctx, boolean inv, boolean ender, boolean xp) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        int n = IntegerArgumentType.getInteger(ctx, "numero");
        List<SnapshotStore.Snapshot> snaps = SnapshotStore.list(target);
        if (n > snaps.size()) {
            ctx.getSource().sendFailure(Component.literal("Pas de sauvegarde n°" + n + " (" + snaps.size() + " disponibles)."));
            return 0;
        }
        SnapshotStore.Snapshot s = snaps.get(n - 1);
        SnapshotStore.saveHistory(target, "avant-restauration", true); // permet d'annuler
        SnapshotStore.apply(target, s.data(), inv, ender, xp);
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (inv) parts.add("inventaire");
        if (ender) parts.add("coffre de l'Ender");
        if (xp) parts.add("expérience");
        String what = String.join(" + ", parts);
        target.sendSystemMessage(Component.literal("[Citizen] Un admin a restauré : " + what + ".").withStyle(ChatFormatting.GOLD));
        String by = ctx.getSource().getTextName();
        InventoryEvents.alert(ctx.getSource().getServer(), by + " a restauré " + what + " de "
                + target.getGameProfile().getName() + " (sauvegarde du " + s.time().format(SnapshotStore.DISPLAY_TS) + ").",
                ChatFormatting.YELLOW);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        SnapshotStore.saveHistory(target, "manuel", true);
        ctx.getSource().sendSuccess(() -> Component.literal("Inventaire de " + target.getDisplayName().getString() + " sauvegardé."), true);
        return 1;
    }
}
