package de.purplekush.tabliste;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class TablistePlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer FARBEN = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private NamespacedKey datumKey;
    private NamespacedKey basisKey;
    private Statistic spielzeitStat;
    private ZoneId zone;
    private List<String> header;
    private List<String> footer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        datumKey = new NamespacedKey(this, "tag_datum");
        basisKey = new NamespacedKey(this, "tag_basis");
        spielzeitStat = findeSpielzeitStatistik();
        if (spielzeitStat == null) {
            getLogger().warning("Spielzeit-Statistik nicht gefunden, Spielzeit wird als 0 angezeigt.");
        }
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::updateAlle, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private void loadSettings() {
        reloadConfig();
        try {
            zone = ZoneId.of(getConfig().getString("zeitzone", "Europe/Berlin"));
        } catch (DateTimeException e) {
            getLogger().warning("Ungültige Zeitzone, nutze Europe/Berlin.");
            zone = ZoneId.of("Europe/Berlin");
        }
        header = getConfig().getStringList("header");
        footer = getConfig().getStringList("footer");
    }

    // Der Statistik-Name hat sich in manchen Versionen geändert, daher beide probieren.
    private Statistic findeSpielzeitStatistik() {
        for (String name : new String[]{"PLAY_ONE_MINUTE", "PLAY_TIME"}) {
            try {
                return Statistic.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer());
    }

    private void updateAlle() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        ZonedDateTime jetzt = ZonedDateTime.now(zone);
        long gesamtTicks = spielzeitStat == null ? 0 : player.getStatistic(spielzeitStat);

        // Tages-Startwert merken, bei neuem Tag zurücksetzen
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String heute = LocalDate.now(zone).toString();
        if (!heute.equals(pdc.get(datumKey, PersistentDataType.STRING))) {
            pdc.set(datumKey, PersistentDataType.STRING, heute);
            pdc.set(basisKey, PersistentDataType.LONG, gesamtTicks);
        }
        long basis = pdc.getOrDefault(basisKey, PersistentDataType.LONG, gesamtTicks);
        long heuteTicks = Math.max(0, gesamtTicks - basis);

        double tps = Math.min(20.0, Bukkit.getTPS()[0]);

        String[][] ersatz = {
                {"%spieler%", player.getName()},
                {"%uhrzeit%", jetzt.format(UHRZEIT)},
                {"%datum%", jetzt.format(DATUM)},
                {"%online%", String.valueOf(Bukkit.getOnlinePlayers().size())},
                {"%max%", String.valueOf(Bukkit.getMaxPlayers())},
                {"%gesamt%", dauer(gesamtTicks)},
                {"%heute%", dauer(heuteTicks)},
                {"%ping%", String.valueOf(player.getPing())},
                {"%tps%", String.format(Locale.GERMANY, "%.1f", tps)}
        };

        player.sendPlayerListHeaderAndFooter(bauen(header, ersatz), bauen(footer, ersatz));
    }

    private Component bauen(List<String> zeilen, String[][] ersatz) {
        Component result = Component.empty();
        for (int i = 0; i < zeilen.size(); i++) {
            String zeile = zeilen.get(i);
            for (String[] e : ersatz) {
                zeile = zeile.replace(e[0], e[1]);
            }
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(FARBEN.deserialize(zeile));
        }
        return result;
    }

    private static String dauer(long ticks) {
        long minuten = ticks / 20 / 60;
        return (minuten / 60) + " Std " + String.format("%02d", minuten % 60) + " Min";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage(FARBEN.deserialize(getConfig().getString("nachrichten.reload", "")));
            return true;
        }
        sender.sendMessage(Component.text("/tabliste reload"));
        return true;
    }
}
