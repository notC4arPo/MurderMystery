package pl.plajer.murdermystery;

import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import pl.plajer.murdermystery.arena.Arena;
import pl.plajer.murdermystery.arena.ArenaEvents;
import pl.plajer.murdermystery.arena.ArenaManager;
import pl.plajer.murdermystery.arena.ArenaRegistry;
import pl.plajer.murdermystery.commands.MainCommand;
import pl.plajer.murdermystery.database.FileStats;
import pl.plajer.murdermystery.database.MySQLConnectionUtils;
import pl.plajer.murdermystery.database.MySQLDatabase;
import pl.plajer.murdermystery.events.ChatEvents;
import pl.plajer.murdermystery.events.Events;
import pl.plajer.murdermystery.events.JoinEvent;
import pl.plajer.murdermystery.events.LobbyEvent;
import pl.plajer.murdermystery.events.QuitEvent;
import pl.plajer.murdermystery.events.spectator.SpectatorEvents;
import pl.plajer.murdermystery.events.spectator.SpectatorItemEvents;
import pl.plajer.murdermystery.handlers.BungeeManager;
import pl.plajer.murdermystery.handlers.ChatManager;
import pl.plajer.murdermystery.handlers.InventoryManager;
import pl.plajer.murdermystery.handlers.PermissionsManager;
import pl.plajer.murdermystery.handlers.PlaceholderManager;
import pl.plajer.murdermystery.handlers.RewardsHandler;
import pl.plajer.murdermystery.handlers.SignManager;
import pl.plajer.murdermystery.handlers.items.SpecialItem;
import pl.plajer.murdermystery.handlers.language.LanguageManager;
import pl.plajer.murdermystery.handlers.setup.SetupInventoryEvents;
import pl.plajer.murdermystery.leaderheads.MurderMysteryDeaths;
import pl.plajer.murdermystery.leaderheads.MurderMysteryGamesPlayed;
import pl.plajer.murdermystery.leaderheads.MurderMysteryHighestScore;
import pl.plajer.murdermystery.leaderheads.MurderMysteryKills;
import pl.plajer.murdermystery.leaderheads.MurderMysteryLoses;
import pl.plajer.murdermystery.leaderheads.MurderMysteryWins;
import pl.plajer.murdermystery.user.User;
import pl.plajer.murdermystery.user.UserManager;
import pl.plajer.murdermystery.utils.MessageUtils;
import pl.plajer.murdermystery.utils.Metrics;
import pl.plajerlair.core.services.ReportedException;
import pl.plajerlair.core.services.ServiceRegistry;

/**
 * @author Plajer
 * <p>
 * Created at 03.08.2018
 */
//todo locales not working
public class Main extends JavaPlugin {

  public static int STARTING_TIMER_TIME = 60;
  public static int CLASSIC_TIMER_TIME = 270;
  private static boolean debug;
  private boolean bossbarEnabled;
  private String version;
  private boolean forceDisable = false;
  private boolean bungeeEnabled;
  private BungeeManager bungeeManager;
  private InventoryManager inventoryManager;
  private RewardsHandler rewardsHandler;
  private boolean inventoryManagerEnabled = false;
  private boolean chatFormat = true;
  private boolean dataEnabled = false;
  private List<String> fileNames = Arrays.asList("arenas", "bungee", "rewards", "stats", "lobbyitems", "mysql");
  private MySQLDatabase database;
  private FileStats fileStats;
  private SignManager signManager;
  private MainCommand mainCommand;
  private boolean databaseActivated = false;

  public static void debug(String thing, long millis) {
    long elapsed = System.currentTimeMillis() - millis;
    if (debug) {
      Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[Murder Debugger] Running task '" + thing + "'");
    }
    if (elapsed > 15) {
      Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Murder Debugger] Slow server response, games may be affected.");
    }
  }

  @Override
  public void onEnable() {
    try {
      //todo later
      //ServiceRegistry.registerService(this);
      version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
      LanguageManager.init(this);
      bossbarEnabled = getConfig().getBoolean("Bossbar-Enabled", true);
      saveDefaultConfig();
      if (!(version.equalsIgnoreCase("v1_9_R1") || version.equalsIgnoreCase("v1_10_R1") || version.equalsIgnoreCase("v1_11_R1")
              || version.equalsIgnoreCase("v1_12_R1"))) {
        MessageUtils.thisVersionIsNotSupported();
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Your server version is not supported by Murder Mystery!");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Sadly, we must shut off. Maybe you consider changing your server version?");
        forceDisable = true;
        getServer().getPluginManager().disablePlugin(this);
        return;
      }
      try {
        Class.forName("org.spigotmc.SpigotConfig");
      } catch (Exception e) {
        MessageUtils.thisVersionIsNotSupported();
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Your server software is not supported by Murder Mystery!");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "We support only Spigot and Spigot forks only! Shutting off...");
        forceDisable = true;
        getServer().getPluginManager().disablePlugin(this);
        return;
      }
      debug = getConfig().getBoolean("Debug", false);
      debug("Main setup start", System.currentTimeMillis());
      setupFiles();
      initializeClasses();

      if (databaseActivated) {
        for (Player p : Bukkit.getOnlinePlayers()) {
          Bukkit.getScheduler().runTaskAsynchronously(this, () -> MySQLConnectionUtils.loadPlayerStats(p, this));
        }
      } else {
        fileStats.loadStatsForPlayersOnline();
      }

      String currentVersion = "v" + Bukkit.getPluginManager().getPlugin("MurderMystery").getDescription().getVersion();
      //todo
    /*if (getConfig().getBoolean("Update-Notifier.Enabled", true)) {
      try {
        UpdateChecker.checkUpdate(currentVersion);
        String latestVersion = UpdateChecker.getLatestVersion();
        if (latestVersion != null) {
          latestVersion = "v" + latestVersion;
          if (latestVersion.contains("b")) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[MurderMystery] Your software is ready for update! However it's a BETA VERSION. Proceed with caution.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[MurderMystery] Current version %old%, latest version %new%".replace("%old%", currentVersion)
                    .replace("%new%", latestVersion));
          } else {
            MessageUtils.updateIsHere();
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "Your Murder Mystery plugin is outdated! Download it to keep with latest changes and fixes.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "Disable this option in config.yml if you wish.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "Current version: " + ChatColor.RED + currentVersion + ChatColor.YELLOW + " Latest version: " + ChatColor.GREEN + latestVersion);
          }
        }
      } catch (Exception ex) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[MurderMystery] An error occured while checking for update!");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Please check internet connection or check for update via WWW site directly!");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "WWW site todo");
      }
    }*/
    } catch (Exception ex) {
      new ReportedException(this, ex);
    }
  }

  @Override
  public void onDisable() {
    if (forceDisable) {
      return;
    }
    debug("System disable", System.currentTimeMillis());
    for (Arena a : ArenaRegistry.getArenas()) {
      for (Player p : a.getPlayers()) {
        ArenaManager.leaveAttempt(p, a);
        a.cleanUpArena();
      }
    }
    for (Player player : getServer().getOnlinePlayers()) {
      User user = UserManager.getUser(player.getUniqueId());
      for (String s : FileStats.STATISTICS.keySet()) {
        if (isDatabaseActivated()) {
          getMySQLDatabase().setStat(player.getUniqueId().toString(), s, user.getInt(s));
        } else {
          getFileStats().saveStat(player, s);
        }
      }
      UserManager.removeUser(player.getUniqueId());
    }
    for (Hologram hologram : HologramsAPI.getHolograms(this)) {
      hologram.delete();
    }
    if (isDatabaseActivated()) {
      getMySQLDatabase().closeDatabase();
    }
  }

  private void initializeClasses() {
    bungeeEnabled = getConfig().getBoolean("BungeeActivated", false);
    if (getConfig().getBoolean("BungeeActivated", false)) {
      bungeeManager = new BungeeManager(this);
    }
    databaseActivated = getConfig().getBoolean("DatabaseActivated", false);
    if (databaseActivated) {
      database = new MySQLDatabase(this);
    } else {
      fileStats = new FileStats(this);
    }
    SpecialItem.loadAll();
    inventoryManagerEnabled = getConfig().getBoolean("InventoryManager", false);
    chatFormat = getConfig().getBoolean("ChatFormat-Enabled", true);
    PermissionsManager.init();
    new ChatManager(ChatManager.colorMessage("In-Game.Plugin-Prefix"));
    mainCommand = new MainCommand(this, true);
    new ArenaEvents(this);
    inventoryManager = new InventoryManager(this);
    new SpectatorEvents(this);
    new QuitEvent(this);
    new SetupInventoryEvents(this);
    new JoinEvent(this);
    new ChatEvents(this);
    STARTING_TIMER_TIME = getConfig().getInt("Starting-Waiting-Time", 60);
    CLASSIC_TIMER_TIME = getConfig().getInt("Classic-Gameplay-Time", 270);
    Metrics metrics = new Metrics(this);
    metrics.addCustomChart(new Metrics.SimplePie("database_enabled", () -> getConfig().getString("DatabaseActivated", "false")));
    metrics.addCustomChart(new Metrics.SimplePie("bungeecord_hooked", () -> getConfig().getString("BungeeActivated", "false")));
    metrics.addCustomChart(new Metrics.SimplePie("locale_used", () -> {
      switch (getConfig().getString("locale", "default")) {
        case "default":
          return "English";
        case "english":
        case "en":
          return "English";
        case "polish":
        case "polski":
        case "pl":
          return "Polish";
        case "german":
        case "deutsch":
        case "de":
          return "German";
        default:
          return "English";
      }
    }));
    metrics.addCustomChart(new Metrics.SimplePie("update_notifier", () -> {
      if (getConfig().getBoolean("Update-Notifier.Enabled", true)) {
        if (getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true)) {
          return "Enabled with beta notifier";
        } else {
          return "Enabled";
        }
      } else {
        if (getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true)) {
          return "Beta notifier only";
        } else {
          return "Disabled";
        }
      }
    }));
    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      Main.debug("Hooking into PlaceholderAPI", System.currentTimeMillis());
      new PlaceholderManager().register();
    }
    if (Bukkit.getPluginManager().isPluginEnabled("LeaderHeads")) {
      Main.debug("Hooking into LeaderHeads", System.currentTimeMillis());
      new MurderMysteryDeaths();
      new MurderMysteryGamesPlayed();
      new MurderMysteryHighestScore();
      new MurderMysteryKills();
      new MurderMysteryLoses();
      new MurderMysteryWins();
    }
    User.cooldownHandlerTask();
    ArenaRegistry.registerArenas();
    new Events(this);
    new LobbyEvent(this);
    new SpectatorItemEvents(this);
    rewardsHandler = new RewardsHandler(this);
    signManager = new SignManager(this);

    new BukkitRunnable() {

      @Override
      public void run() {
        // Find the holograms created by your plugin
        for (Hologram hologram : HologramsAPI.getHolograms(Main.this)) {
          long tenMinutesMillis = 60 * 1000;
          long elapsedMillis = System.currentTimeMillis() - hologram.getCreationTimestamp();

          if (elapsedMillis > tenMinutesMillis) {
            hologram.delete();
          }
        }
      }

    }.runTaskTimer(this, 30 * 20L, 30 * 20L);
  }

  private void setupFiles() {
    for (String fileName : fileNames) {
      File file = new File(getDataFolder() + File.separator + fileName + ".yml");
      if (!file.exists()) {
        saveResource(fileName + ".yml", false);
      }
    }
  }

  public boolean isBossbarEnabled() {
    return bossbarEnabled;
  }

  public boolean is1_9_R1() {
    return version.equalsIgnoreCase("v1_9_R1");
  }

  public boolean is1_10_R1() {
    return version.equalsIgnoreCase("v1_10_R1");
  }

  public boolean isInventoryManagerEnabled() {
    return inventoryManagerEnabled;
  }

  public RewardsHandler getRewardsHandler() {
    return rewardsHandler;
  }

  public boolean isChatFormatEnabled() {
    return chatFormat;
  }

  public boolean isBungeeActivated() {
    return bungeeEnabled;
  }

  public BungeeManager getBungeeManager() {
    return bungeeManager;
  }

  public FileStats getFileStats() {
    return fileStats;
  }

  public boolean isDatabaseActivated() {
    return databaseActivated;
  }

  public MySQLDatabase getMySQLDatabase() {
    return database;
  }

  public InventoryManager getInventoryManager() {
    return inventoryManager;
  }

  public SignManager getSignManager() {
    return signManager;
  }

  public boolean isDataEnabled() {
    return dataEnabled;
  }

  public void setDataEnabled(boolean dataEnabled) {
    this.dataEnabled = dataEnabled;
  }

  public MainCommand getMainCommand() {
    return mainCommand;
  }
}
