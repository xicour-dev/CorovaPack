package com.example.corovaCore.commands.subcommands;

import com.example.corovaEvents.CorovaEvents;
import com.example.corovaEvents.MinigameEvents.*;
import com.example.corovaEvents.MinigameEvents.FireworkSpawner.FireworkTrigger;
import com.example.corovaEvents.PlayerEvents.PlayerFirstJoin;
import com.example.corovaEvents.PlayerEvents.PlayerRejoin;
import com.example.corovaEvents.ServerEvents.*;
import com.example.corovaEvents.WorldEvents.*;
import com.example.corovaGuard.CorovaGuard;
import com.example.corovaGuard.Region;
import com.example.corovaGuard.RegionType;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class EventsSubCommand implements SubCommand {

    @Override
    public String getName() {
        return "events";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("e");
    }

    @Override
    public String getDescription() {
        return "Manage world events, minigames, player events, and the event join queue.";
    }

    @Override
    public String getSyntax() {
        return "/c e <worldevents|minigames|playerevents|join> [trigger|...]";
    }

    @Override
    public boolean perform(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + this.getSyntax());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "worldevents":
                if (args.length >= 2 && args[1].equalsIgnoreCase("trigger")) {
                    return this.handleTrigger(sender, args);
                }
                return this.handleWorldEvents(sender, args);
            case "minigames":
                if (args.length >= 2 && args[1].equalsIgnoreCase("trigger")) {
                    return this.handleTrigger(sender, args);
                }
                return this.handleMinigames(sender, args);
            case "playerevents":
                if (args.length >= 2 && args[1].equalsIgnoreCase("trigger")) {
                    return this.handleTrigger(sender, args);
                }
                sender.sendMessage(ChatColor.RED + "Usage: /c e playerevents trigger <event>");
                return true;
            case "serverevents":
                if (args.length >= 2 && args[1].equalsIgnoreCase("trigger")) {
                    return this.handleTrigger(sender, args);
                }
                return this.handleServerEvents(sender, args);
            case "join":
                return this.handleJoin(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown action: " + args[0]);
                return true;
        }
    }

    // -------------------------------------------------------------------------
    // World Events
    // -------------------------------------------------------------------------

    private boolean handleWorldEvents(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e worldevents <register|cancel> [args...]");
            return true;
        }

        if (args[1].equalsIgnoreCase("cancel")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /c e worldevents cancel <event|all>");
                return true;
            }
            String target = args[2].toLowerCase();
            CorovaEvents events = CorovaEvents.getInstance();
            if (target.equals("all")) {
                events.cancelAllWorldEvents();
                sender.sendMessage(ChatColor.GREEN + "All world events have been cancelled.");
            } else if (CorovaEvents.WORLD_EVENT_KEYS.contains(target)) {
                events.cancelWorldEvent(target);
                sender.sendMessage(ChatColor.GREEN + "World event '" + target + "' has been cancelled.");
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown world event: " + args[2]);
            }
            return true;
        }

        if (!args[1].equalsIgnoreCase("register")) {
            sender.sendMessage(ChatColor.RED + "Unknown worldevents action: " + args[1] + ". Use: register, cancel");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e worldevents register <event> [enable|disable]");
            return true;
        }

        String eventKey = args[2].toLowerCase();
        if (!CorovaEvents.WORLD_EVENT_KEYS.contains(eventKey)) {
            sender.sendMessage(ChatColor.RED + "Unknown world event: " + args[2]);
            sender.sendMessage(ChatColor.YELLOW + "Valid events: " + String.join(", ", CorovaEvents.WORLD_EVENT_KEYS));
            return true;
        }

        CorovaEvents events = CorovaEvents.getInstance();
        if (args.length < 4) {
            boolean enabled = events.isWorldEventEnabled(eventKey);
            sender.sendMessage(ChatColor.YELLOW + "World event " + ChatColor.WHITE + args[2]
                    + ChatColor.YELLOW + " is currently "
                    + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED")
                    + ChatColor.YELLOW + ".");
            return true;
        }

        String toggle = args[3].toLowerCase();
        if (toggle.equals("enable")) {
            events.setWorldEventEnabled(eventKey, true);
            sender.sendMessage(ChatColor.GREEN + "World event " + ChatColor.WHITE + args[2]
                    + ChatColor.GREEN + " has been ENABLED.");
        } else if (toggle.equals("disable")) {
            events.setWorldEventEnabled(eventKey, false);
            sender.sendMessage(ChatColor.RED + "World event " + ChatColor.WHITE + args[2]
                    + ChatColor.RED + " has been DISABLED.");
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid option: " + args[3] + ". Use: enable or disable");
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Minigames
    // -------------------------------------------------------------------------

    private boolean handleMinigames(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames <edit|SetEventEndLocation|RushNextStep|AddFirework|RemoveFirework|ListFireworks|ClearFireworks|RandomEvents|registeredevents|rewardtable|reward|cancel|queue>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "queuesign":
                return this.handleQueueSign(sender, args);

            case "cancel":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /c e minigames cancel <event|all>");
                    return true;
                }
                String target = args[2].toLowerCase();
                if (target.equalsIgnoreCase("all")) {
                    MinigameEventSystem.getInstance().cancelCurrentEvent();
                    sender.sendMessage(ChatColor.GREEN + "All minigame events cancelled.");
                } else {
                    MinigameEvent event = MinigameEventRegistrar.getEvent(target);
                    if (event != null) {
                        MinigameEventSystem.getInstance().cancelEvent(event);
                        sender.sendMessage(ChatColor.GREEN + "Minigame event '" + event.getName() + "' cancelled.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Unknown minigame: " + target);
                    }
                }
                return true;

            case "queue":
                return this.handleQueue(sender, args);

            case "randomevents":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /c e minigames RandomEvents <ON|OFF>");
                    return true;
                }
                boolean enable = args[2].equalsIgnoreCase("ON");
                CorovaEvents.getInstance().getEventsConfig().set("minigames.random_events_enabled", enable);
                CorovaEvents.getInstance().saveEventsConfig();
                CorovaEvents.getInstance().startRandomEventTask();
                sender.sendMessage(ChatColor.GREEN + "Random minigame events are now "
                        + (enable ? ChatColor.YELLOW + "ENABLED" : ChatColor.RED + "DISABLED")
                        + ChatColor.GREEN + ".");
                return true;

            case "registeredevents":
                return this.handleRegisteredEvents(sender, args);

            case "edit":
                this.handleMinigameEdit(sender, args);
                return true;

            case "seteventendlocation":
                this.handleSetEventEndLocation(sender);
                return true;

            case "rushnextstep":
                if (args.length >= 3) {
                    MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                    if (event != null) {
                        event.rushNextStep();
                        sender.sendMessage(ChatColor.GREEN + "Rushing next step for " + event.getName());
                    } else {
                        sender.sendMessage(ChatColor.RED + "Unknown minigame: " + args[2]);
                    }
                } else {
                    List<MinigameEvent> active = MinigameEventSystem.getInstance().getActiveEvents();
                    if (active.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "No minigames are currently occurring.");
                    } else if (active.size() == 1) {
                        active.get(0).rushNextStep();
                        sender.sendMessage(ChatColor.GREEN + "Rushing next step for " + active.get(0).getName());
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Multiple minigames are occurring. Please specify:");
                        for (MinigameEvent event : active) {
                            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.AQUA + event.getName());
                        }
                    }
                }
                return true;

            case "addfirework":
                this.handleAddFirework(sender, args);
                return true;

            case "removefirework":
                this.handleRemoveFirework(sender, args);
                return true;

            case "listfireworks":
                this.handleListFireworks(sender, args);
                return true;

            case "clearfireworks":
                this.handleClearFireworks(sender, args);
                return true;

            case "rewardtable":
                return this.handleRewardTable(sender, args);

            case "reward":
                if (args.length >= 3 && args[2].equalsIgnoreCase("table")) {
                    // Shift arguments for handleRewardTable
                    String[] shifted = new String[args.length - 1];
                    shifted[0] = args[0]; // "minigames"
                    shifted[1] = args[1]; // "reward" (will be ignored or replaced)
                    System.arraycopy(args, 3, shifted, 2, args.length - 3);
                    return this.handleRewardTable(sender, shifted);
                }
                sender.sendMessage(ChatColor.RED + "Usage: /c e minigames reward table <minigame> <add|remove|settakes|list|clear>");
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown minigames action: " + args[1]);
                return true;
        }
    }

    private boolean handleQueue(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames queue <list|watch> <Minigame>");
            return true;
        }

        String action = args[2].toLowerCase();
        if (action.equals("list")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /c e minigames queue list <Minigame>");
                return true;
            }
            MinigameEventSystem.getInstance().listQueue(sender, args[3]);
        } else if (action.equals("watch")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use the watch command.");
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /c e minigames queue watch <Minigame|all>");
                return true;
            }
            MinigameEventSystem.getInstance().toggleWatch(player, args[3]);
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown queue action: " + action);
        }
        return true;
    }

    private boolean handleRegisteredEvents(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames registeredevents <minigame> <arena> <add|remove>");
            return true;
        }

        String minigameName = args[2];
        MinigameEvent event = MinigameEventRegistrar.getEvent(minigameName);
        if (event == null) {
            sender.sendMessage(ChatColor.RED + "Unknown minigame: " + minigameName);
            return true;
        }

        if (args.length < 4) {
            List<String> pool = CorovaEvents.getInstance().getRegisteredEventArenas();
            String prefix = minigameName.toLowerCase() + ":";
            List<String> filtered = pool.stream()
                    .filter(e -> e.startsWith(prefix))
                    .map(e -> e.substring(prefix.length()))
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No arenas registered for " + event.getName() + " in the random event pool.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Registered arenas for " + event.getName() + ": "
                        + ChatColor.WHITE + String.join(", ", filtered));
            }
            return true;
        }

        String arenaName = args[3];
        if (args.length < 5) {
            List<String> pool = CorovaEvents.getInstance().getRegisteredEventArenas();
            boolean registered = pool.contains(minigameName.toLowerCase() + ":" + arenaName.toLowerCase());
            sender.sendMessage(ChatColor.YELLOW + "Arena " + ChatColor.WHITE + arenaName
                    + ChatColor.YELLOW + " for " + ChatColor.WHITE + event.getName()
                    + ChatColor.YELLOW + " is "
                    + (registered ? ChatColor.GREEN + "registered" : ChatColor.RED + "not registered")
                    + ChatColor.YELLOW + " in the random event pool.");
            return true;
        }

        String action = args[4].toLowerCase();
        CorovaEvents eventsInstance = CorovaEvents.getInstance();
        if (action.equals("add")) {
            ArenaData arena = event.getArena(arenaName);
            if (arena == null) {
                sender.sendMessage(ChatColor.RED + "Arena '" + arenaName + "' does not exist for " + event.getName() + ".");
                return true;
            }
            boolean added = eventsInstance.addRegisteredEventArena(minigameName, arenaName);
            if (added) {
                sender.sendMessage(ChatColor.GREEN + "Added " + ChatColor.WHITE + event.getName() + " / " + arena.getName()
                        + ChatColor.GREEN + " to the random event pool.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + event.getName() + " / " + arenaName + " is already in the random event pool.");
            }
        } else if (action.equals("remove")) {
            boolean removed = eventsInstance.removeRegisteredEventArena(minigameName, arenaName);
            if (removed) {
                sender.sendMessage(ChatColor.RED + "Removed " + ChatColor.WHITE + event.getName() + " / " + arenaName
                        + ChatColor.RED + " from the random event pool.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + event.getName() + " / " + arenaName + " was not in the random event pool.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid action: " + args[4] + ". Use: add or remove");
        }

        return true;
    }

    private boolean handleRewardTable(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /c e minigames reward table <minigame> <add|remove|settakes|list|clear>");
            return true;
        }

        String minigameName = args[2];
        String action = args[3].toLowerCase();
        MinigameRewardManager mgr = MinigameRewardManager.getInstance();
        if (mgr == null) {
            sender.sendMessage("§cMinigame reward system is not initialized.");
            return true;
        }

        MinigameRewardTable table = mgr.getOrCreate(minigameName);

        switch (action) {
            case "add": {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /c e minigames rewardtable " + minigameName + " add <itemId> [minAmount] [maxAmount] [weight]");
                    return true;
                }
                String itemId = args[4];
                int minAmt = args.length > 5 ? parseIntSilent(args[5], 1) : 1;
                int maxAmt = args.length > 6 ? parseIntSilent(args[6], 1) : minAmt;
                double weight = args.length > 7 ? parseDoubleSilent(args[7], 1.0) : 1.0;
                if (minAmt < 0 || maxAmt < minAmt || weight <= 0.0) {
                    sender.sendMessage("§cInvalid values. Ensure minAmount ≤ maxAmount and weight > 0.");
                    return true;
                }
                table.addEntry(new MinigameRewardEntry(itemId, minAmt, maxAmt, weight));
                mgr.saveRewardTables();
                sender.sendMessage("§a[Rewards] Added §e" + itemId + " §a(x" + minAmt + "-" + maxAmt + ", weight=" + weight + ") to §e" + minigameName + "§a's reward pool.");
                break;
            }
            case "remove": {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /c e minigames rewardtable " + minigameName + " remove <itemId>");
                    return true;
                }
                String itemId = args[4];
                boolean removed = table.removeEntry(itemId);
                if (removed) {
                    mgr.saveRewardTables();
                    sender.sendMessage("§c[Rewards] Removed §e" + itemId + " §cfrom §e" + minigameName + "§c's reward pool.");
                } else {
                    sender.sendMessage("§e[Rewards] No entry with id §f" + itemId + "§e found in §f" + minigameName + "§e's pool.");
                }
                break;
            }
            case "settakes": {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /c e minigames rewardtable " + minigameName + " settakes <1-9>");
                    return true;
                }
                int count = parseIntSilent(args[4], -1);
                if (count < 1 || count > 9) {
                    sender.sendMessage("§cTake count must be between 1 and 9.");
                    return true;
                }
                table.setTakeCount(count);
                mgr.saveRewardTables();
                sender.sendMessage("§a[Rewards] Winners of §e" + minigameName + " §amay now take §e" + count + " §aitem(s).");
                break;
            }
            case "list": {
                List<MinigameRewardEntry> pool = table.getPool();
                if (pool.isEmpty()) {
                    sender.sendMessage("§e[Rewards] §f" + minigameName + "§e has no reward entries configured.");
                    return true;
                }
                sender.sendMessage("§a[Rewards] §f" + minigameName + " §areward pool §7(takes: §e" + table.getTakeCount() + "§7, pool size: §e" + pool.size() + "§7)§a:");
                for (int i = 0; i < pool.size(); i++) {
                    MinigameRewardEntry e = pool.get(i);
                    sender.sendMessage("  §7" + (i + 1) + ". §f" + e.getItemId() + " §7x(" + e.getMinAmount() + "-" + e.getMaxAmount() + ") §7weight=§e" + e.getWeight());
                }
                break;
            }
            case "clear":
                table.clearPool();
                mgr.saveRewardTables();
                sender.sendMessage("§c[Rewards] Cleared §e" + minigameName + "§c's entire reward pool.");
                break;
            default:
                sender.sendMessage("§cUnknown action '§e" + action + "§c'. Use: add, remove, settakes, list, clear");
        }

        return true;
    }


    // -------------------------------------------------------------------------
    // Firework helpers
    // -------------------------------------------------------------------------

    private void handleAddFirework(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames AddFirework <EventStart|WaitingRoom|EventEnd> <Event> <EventArena> <n> [SpawnCount]");
            return;
        }

        String triggerName = args[2];
        FireworkSpawner.FireworkTrigger trigger;
        try {
            trigger = FireworkTrigger.valueOf(triggerName.toUpperCase()
                    .replace("EVENTSTART", "EVENT_START")
                    .replace("WAITINGROOM", "WAITING_ROOM")
                    .replace("EVENTEND", "EVENT_END"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid trigger. Use: EventStart, WaitingRoom, or EventEnd");
            return;
        }

        String eventName = args[3];
        String arenaName = args[4];
        String spawnerName = args[5];
        int spawnCount = 1;
        if (args.length > 6) {
            try {
                spawnCount = Integer.parseInt(args[6]);
                if (spawnCount < 1 || spawnCount > 20) {
                    sender.sendMessage(ChatColor.RED + "Spawn count must be between 1 and 20.");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid spawn count. Must be a number.");
                return;
            }
        }

        MinigameEvent event = MinigameEventRegistrar.getEvent(eventName);
        if (event == null) {
            sender.sendMessage(ChatColor.RED + "Unknown minigame: " + eventName);
            return;
        }
        if (MinigameEventSystem.getInstance().getFireworkSpawner().getSpawner(eventName, arenaName, trigger, spawnerName) != null) {
            sender.sendMessage(ChatColor.RED + "A firework spawner with name '" + spawnerName + "' already exists for this event/arena/trigger!");
            return;
        }

        Location spawnerLoc = player.getLocation().getBlock().getLocation().clone();
        spawnerLoc.add(0.5, 0.0, 0.5);
        MinigameEventSystem.getInstance().getFireworkSpawner().addSpawner(eventName, arenaName, trigger, spawnerName, spawnerLoc, spawnCount);
        MinigameEventSystem.getInstance().saveArenas();
        sender.sendMessage(ChatColor.GREEN + "Firework spawner '" + spawnerName + "' added for "
                + eventName + " - " + arenaName + " at " + trigger.name()
                + " (spawns " + spawnCount + " firework" + (spawnCount > 1 ? "s" : "") + ")!");
    }

    private void handleRemoveFirework(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames RemoveFirework <EventStart|WaitingRoom|EventEnd> <Event> <EventArena> <n>");
            return;
        }

        String triggerName = args[2];
        FireworkSpawner.FireworkTrigger trigger;
        try {
            trigger = FireworkTrigger.valueOf(triggerName.toUpperCase()
                    .replace("EVENTSTART", "EVENT_START")
                    .replace("WAITINGROOM", "WAITING_ROOM")
                    .replace("EVENTEND", "EVENT_END"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid trigger. Use: EventStart, WaitingRoom, or EventEnd");
            return;
        }

        String eventName = args[3];
        String arenaName = args[4];
        String spawnerName = args[5];
        boolean removed = MinigameEventSystem.getInstance().getFireworkSpawner().removeSpawner(eventName, arenaName, trigger, spawnerName);
        if (removed) {
            MinigameEventSystem.getInstance().saveArenas();
            sender.sendMessage(ChatColor.GREEN + "Firework spawner '" + spawnerName + "' removed!");
        } else {
            sender.sendMessage(ChatColor.RED + "No firework spawner found with name '" + spawnerName
                    + "' for " + eventName + " - " + arenaName + " at " + trigger.name());
        }
    }

    private void handleListFireworks(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames ListFireworks <EventStart|WaitingRoom|EventEnd> <Event> <EventArena>");
            return;
        }

        String triggerName = args[2];
        FireworkSpawner.FireworkTrigger trigger;
        try {
            trigger = FireworkTrigger.valueOf(triggerName.toUpperCase()
                    .replace("EVENTSTART", "EVENT_START")
                    .replace("WAITINGROOM", "WAITING_ROOM")
                    .replace("EVENTEND", "EVENT_END"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid trigger. Use: EventStart, WaitingRoom, or EventEnd");
            return;
        }

        String eventName = args[3];
        String arenaName = args[4];
        List<FireworkSpawner.SpawnerData> spawners = MinigameEventSystem.getInstance().getFireworkSpawner().getSpawners(eventName, arenaName, trigger);
        if (spawners.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No firework spawners found for " + eventName + " - " + arenaName + " at " + trigger.name());
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Firework Spawners for " + eventName + " - " + arenaName + " at " + trigger.name() + ":");
        for (FireworkSpawner.SpawnerData spawner : spawners) {
            Location loc = spawner.getLocation();
            sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + spawner.getName()
                    + ChatColor.GRAY + " at (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ") "
                    + ChatColor.YELLOW + "[" + spawner.getSpawnCount() + " firework" + (spawner.getSpawnCount() > 1 ? "s" : "") + "]");
        }
    }

    private void handleClearFireworks(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames ClearFireworks <EventStart|WaitingRoom|EventEnd> <Event> <EventArena>");
            return;
        }

        String triggerName = args[2];
        FireworkSpawner.FireworkTrigger trigger;
        try {
            trigger = FireworkTrigger.valueOf(triggerName.toUpperCase()
                    .replace("EVENTSTART", "EVENT_START")
                    .replace("WAITINGROOM", "WAITING_ROOM")
                    .replace("EVENTEND", "EVENT_END"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid trigger. Use: EventStart, WaitingRoom, or EventEnd");
            return;
        }

        String eventName = args[3];
        String arenaName = args[4];
        MinigameEventSystem.getInstance().getFireworkSpawner().clearSpawners(eventName, arenaName, trigger);
        MinigameEventSystem.getInstance().saveArenas();
        sender.sendMessage(ChatColor.GREEN + "All firework spawners cleared for " + eventName + " - " + arenaName + " at " + trigger.name() + "!");
    }

    // -------------------------------------------------------------------------
    // Join / SetEventEndLocation / MinigameEdit
    // -------------------------------------------------------------------------

    private boolean handleJoin(CommandSender sender, String[] args) {
        // Syntax: /event join [minigame] [all] OR /event join [player]
        // From EventsSubCommand, args[0] is always "join", so /event join [arg1] [arg2]
        // maps to args[1], args[2] etc.

        String minigameArg = null;
        boolean allArg = false;
        Player targetPlayer = null;

        if (args.length >= 2) {
            String firstArg = args[1];
            MinigameEvent event = MinigameEventRegistrar.getEvent(firstArg);
            if (event != null) {
                minigameArg = firstArg;
                if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
                    allArg = true;
                }
            } else if (firstArg.equalsIgnoreCase("all")) {
                allArg = true;
            } else {
                targetPlayer = Bukkit.getPlayer(firstArg);
                if (targetPlayer == null && !firstArg.equalsIgnoreCase("all")) {
                    // Fallback to minigameArg for queue system
                    minigameArg = firstArg;
                }
            }
        }

        if (allArg) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You must be OP to join all players.");
                return true;
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (minigameArg != null) {
                    MinigameEventSystem.getInstance().joinEvent(p, minigameArg);
                } else {
                    MinigameEventSystem.getInstance().joinEvent(p);
                }
            }
            String msg = (minigameArg != null) ? "Queued all players for " + minigameArg : "Queued all players for the next minigame.";
            sender.sendMessage(ChatColor.GREEN + msg);
            return true;
        }

        if (targetPlayer != null) {
            if (!sender.isOp() && !sender.equals(targetPlayer)) {
                sender.sendMessage(ChatColor.RED + "You must be OP to join another player.");
                return true;
            }
            MinigameEventSystem.getInstance().joinEvent(targetPlayer);
            if (!sender.equals(targetPlayer)) {
                sender.sendMessage(ChatColor.GREEN + "Queued " + targetPlayer.getName() + " for the next minigame.");
            }
            return true;
        }

        if (sender instanceof Player player) {
            if (minigameArg != null) {
                if (args.length >= 3 && !allArg) {
                    MinigameEventSystem.getInstance().joinEvent(player, minigameArg + "_" + args[2]);
                } else {
                    MinigameEventSystem.getInstance().joinEvent(player, minigameArg);
                }
            } else {
                MinigameEventSystem.getInstance().joinEvent(player);
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /event join [minigame] [all] or /event join [player]");
        return true;
    }

    private void handleSetEventEndLocation(CommandSender sender) {
        if (sender instanceof Player player) {
            MinigameEventSystem.getInstance().setEndLocation(player.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Event end location set to your current location!");
        } else {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
        }
    }

    private void handleMinigameEdit(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames edit <game> <arena> <LocationSettings|PlayerLimits|Delete>");
            return;
        }

        String gameName = args[2];
        String arenaName = args[3];
        String category = args[4];

        MinigameEvent event = MinigameEventRegistrar.getEvent(gameName);
        if (event == null) {
            sender.sendMessage(ChatColor.RED + "Unknown minigame: " + gameName);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }

        ArenaData arena = event.getArena(arenaName);
        if (arena == null) {
            if (category.equalsIgnoreCase("delete")) {
                sender.sendMessage(ChatColor.RED + "Arena '" + arenaName + "' does not exist.");
                return;
            }
            arena = new ArenaData(arenaName);
            event.addArena(arena);
        }

        if (category.equalsIgnoreCase("LocationSettings")) {
            this.handleLocationSettings(sender, player, event, arena, args);
        } else if (category.equalsIgnoreCase("PlayerLimits")) {
            this.handlePlayerLimits(sender, player, event, arena, args);
        } else if (category.equalsIgnoreCase("Laps")) {
            this.handleLapsSettings(sender, player, event, arena, args);
        } else if (category.equalsIgnoreCase("EventSettings")) {
            this.handleEventSettings(sender, player, event, arena, args);
        } else if (category.equalsIgnoreCase("delete")) {
            event.removeArena(arena);
            MinigameEventSystem.getInstance().saveArenas();
            sender.sendMessage(ChatColor.GREEN + "Deleted arena '" + arenaName + "' for minigame " + event.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown category: " + category + ". Use LocationSettings, PlayerLimits, Laps, EventSettings or Delete.");
        }
    }

    private void handleEventSettings(CommandSender sender, Player player, MinigameEvent event, ArenaData arena, String[] args) {
        if (args.length < 7) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames edit " + event.getName() + " " + arena.getName() + " EventSettings <FireResistanceDuration> <value>");
            return;
        }

        String action = args[5];
        if (action.equalsIgnoreCase("FireResistanceDuration")) {
            int value = parseIntSilent(args[6], -1);
            if (value < -1) {
                sender.sendMessage(ChatColor.RED + "Invalid duration: " + args[6] + ". Must be -1 (default) or >= 0.");
                return;
            }
            arena.setFireResistanceDuration(value);
            MinigameEventSystem.getInstance().saveArenas();
            sender.sendMessage(ChatColor.GREEN + "Fire Resistance Duration for arena " + arena.getName() + " set to " + value + " ticks.");
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown EventSettings action: " + action + ". Use 'FireResistanceDuration'.");
        }
    }

    private void handleLapsSettings(CommandSender sender, Player player, MinigameEvent event, ArenaData arena, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames edit " + event.getName() + " " + arena.getName() + " Laps <set|remove> [number]");
            return;
        }

        String action = args[5];
        if (action.equalsIgnoreCase("remove")) {
            arena.setLaps(1);
            MinigameEventSystem.getInstance().saveArenas();
            sender.sendMessage(ChatColor.GREEN + "Laps for arena " + arena.getName() + " has been reset to 1 (removed).");
            return;
        }

        if (!action.equalsIgnoreCase("set")) {
            sender.sendMessage(ChatColor.RED + "Unknown Laps action: " + action + ". Use 'set' or 'remove'.");
            return;
        }

        if (args.length < 7) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames edit " + event.getName() + " " + arena.getName() + " Laps set <number>");
            return;
        }

        int value = parseIntSilent(args[6], -1);
        if (value < 1) {
            sender.sendMessage(ChatColor.RED + "Invalid number of laps: " + args[6] + ". Must be at least 1.");
            return;
        }

        arena.setLaps(value);
        MinigameEventSystem.getInstance().saveArenas();
        sender.sendMessage(ChatColor.GREEN + "Laps for arena " + arena.getName() + " set to " + value);
    }

    private void handleLocationSettings(CommandSender sender, Player player, MinigameEvent event, ArenaData arena, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames edit " + event.getName() + " " + arena.getName() + " LocationSettings <Action> [args...]");
            return;
        }

        String action = args[5];

        // Custom actions that don't use radius
        if (action.equalsIgnoreCase("AddCheckpoint")) {
            arena.addCheckpoint(player.getLocation());
            MinigameEventSystem.getInstance().saveArenas();
            Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
            if (r != null) r.setMinekartCheckpoints(arena.getCheckpoints());
            player.sendMessage(ChatColor.GREEN + "Track checkpoint added for " + event.getName() + " (Arena: " + arena.getName() + ")");
            return;
        } else if (action.equalsIgnoreCase("RemoveCheckpoint")) {
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... RemoveCheckpoint <index>");
                return;
            }
            int index = parseIntSilent(args[6], -1);
            if (arena.removeCheckpoint(index)) {
                MinigameEventSystem.getInstance().saveArenas();
                Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
                if (r != null) r.setMinekartCheckpoints(arena.getCheckpoints());
                player.sendMessage(ChatColor.GREEN + "Track checkpoint " + index + " removed.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid index: " + args[6]);
            }
            return;
        } else if (action.equalsIgnoreCase("ClearCheckpoints")) {
            arena.clearCheckpoints();
            MinigameEventSystem.getInstance().saveArenas();
            Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
            if (r != null) r.setMinekartCheckpoints(arena.getCheckpoints());
            player.sendMessage(ChatColor.GREEN + "All track checkpoints cleared.");
            return;
        } else if (action.equalsIgnoreCase("ListCheckpoints")) {
            List<Location> cps = arena.getCheckpoints();
            player.sendMessage(ChatColor.GREEN + "Track Checkpoints (" + cps.size() + "):");
            for (int i = 0; i < cps.size(); i++) {
                Location l = cps.get(i);
                player.sendMessage(ChatColor.GRAY + " " + i + ": " + ChatColor.WHITE + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
            }
            return;
        } else if (action.equalsIgnoreCase("AddLapCheckpoint")) {
            arena.addLapCheckpoint(player.getLocation());
            MinigameEventSystem.getInstance().saveArenas();
            Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
            if (r != null) r.setLapCheckpoints(arena.getLapCheckpoints());
            player.sendMessage(ChatColor.GREEN + "Lap checkpoint added for " + event.getName());
            return;
        } else if (action.equalsIgnoreCase("RemoveLapCheckpoint")) {
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... RemoveLapCheckpoint <index>");
                return;
            }
            int index = parseIntSilent(args[6], -1);
            if (arena.removeLapCheckpoint(index)) {
                MinigameEventSystem.getInstance().saveArenas();
                Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
                if (r != null) r.setLapCheckpoints(arena.getLapCheckpoints());
                player.sendMessage(ChatColor.GREEN + "Lap checkpoint " + index + " removed.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid index: " + args[6]);
            }
            return;
        } else if (action.equalsIgnoreCase("ClearLapCheckpoints")) {
            arena.clearLapCheckpoints();
            MinigameEventSystem.getInstance().saveArenas();
            Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
            if (r != null) r.setLapCheckpoints(arena.getLapCheckpoints());
            player.sendMessage(ChatColor.GREEN + "All lap checkpoints cleared.");
            return;
        } else if (action.equalsIgnoreCase("ListLapCheckpoints")) {
            List<Location> cps = arena.getLapCheckpoints();
            player.sendMessage(ChatColor.GREEN + "Lap Checkpoints (" + cps.size() + "):");
            for (int i = 0; i < cps.size(); i++) {
                Location l = cps.get(i);
                player.sendMessage(ChatColor.GRAY + " " + i + ": " + ChatColor.WHITE + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
            }
            return;
        }

        double radiusX = 0;
        double radiusZ = 0;
        if (args.length > 6) {
            try {
                radiusX = Double.parseDouble(args[6]);
                radiusZ = args.length > 7 ? Double.parseDouble(args[7]) : radiusX;
            } catch (NumberFormatException ignored) {}
        }

        if (action.equalsIgnoreCase("SetEventStartLocation")) {
            arena.setStartLocation(player.getLocation());
            arena.setStartRadiusX(radiusX);
            arena.setStartRadiusZ(radiusZ);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Start location set for " + event.getName() + " with radius X: " + radiusX + " Z: " + radiusZ);

        } else if (action.equalsIgnoreCase("RemoveEventStartLocation")) {
            arena.setStartLocation(null);
            arena.setStartRadiusX(0.0);
            arena.setStartRadiusZ(0.0);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Start location removed for " + event.getName());

        } else if (action.equalsIgnoreCase("SetWaitingRoomLocation")) {
            arena.setWaitingRoomLocation(player.getLocation());
            arena.setWaitingRoomRadiusX(radiusX);
            arena.setWaitingRoomRadiusZ(radiusZ);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Waiting room location set for " + event.getName() + " with radius X: " + radiusX + " Z: " + radiusZ);

        } else if (action.equalsIgnoreCase("RemoveWaitingRoomLocation")) {
            arena.setWaitingRoomLocation(null);
            arena.setWaitingRoomRadiusX(0.0);
            arena.setWaitingRoomRadiusZ(0.0);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Waiting room location removed for " + event.getName());

        } else if (action.equalsIgnoreCase("SetEndLocation")) {
            arena.setEndLocation(player.getLocation());
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "End location set for arena " + arena.getName() + " (Game: " + event.getName() + ")");

        } else if (action.equalsIgnoreCase("RemoveEndLocation")) {
            arena.setEndLocation(null);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "End location removed for arena " + arena.getName());

        } else if (action.equalsIgnoreCase("SetTeamRedSpawnpoint") || action.equalsIgnoreCase("SetRedSpawnLocation")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setRedSpawnLocation(player.getLocation());
            arena.setRedSpawnRadiusX(radiusX);
            arena.setRedSpawnRadiusZ(radiusZ);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Red team spawn location set for " + event.getName() + " with radius X: " + radiusX + " Z: " + radiusZ);

        } else if (action.equalsIgnoreCase("RemoveTeamRedSpawnpoint") || action.equalsIgnoreCase("RemoveRedSpawnLocation")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setRedSpawnLocation(null);
            arena.setRedSpawnRadiusX(0.0);
            arena.setRedSpawnRadiusZ(0.0);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Red team spawn location removed for " + event.getName());

        } else if (action.equalsIgnoreCase("SetTeamBlueSpawnpoint") || action.equalsIgnoreCase("SetBlueSpawnLocation")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setBlueSpawnLocation(player.getLocation());
            arena.setBlueSpawnRadiusX(radiusX);
            arena.setBlueSpawnRadiusZ(radiusZ);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Blue team spawn location set for " + event.getName() + " with radius X: " + radiusX + " Z: " + radiusZ);

        } else if (action.equalsIgnoreCase("RemoveTeamBlueSpawnpoint") || action.equalsIgnoreCase("RemoveBlueSpawnLocation")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setBlueSpawnLocation(null);
            arena.setBlueSpawnRadiusX(0.0);
            arena.setBlueSpawnRadiusZ(0.0);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Blue team spawn location removed for " + event.getName());

        } else if (action.equalsIgnoreCase("SetTeamRedRespawnPoint")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setRedRespawnLocation(player.getLocation());
            arena.setRedRespawnRadiusX(radiusX);
            arena.setRedRespawnRadiusZ(radiusZ);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Red team respawn point set for " + event.getName() + " with radius X: " + radiusX + " Z: " + radiusZ);

        } else if (action.equalsIgnoreCase("RemoveTeamRedRespawnPoint")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setRedRespawnLocation(null);
            arena.setRedRespawnRadiusX(0.0);
            arena.setRedRespawnRadiusZ(0.0);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Red team respawn point removed for " + event.getName());

        } else if (action.equalsIgnoreCase("SetTeamBlueRespawnPoint")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setBlueRespawnLocation(player.getLocation());
            arena.setBlueRespawnRadiusX(radiusX);
            arena.setBlueRespawnRadiusZ(radiusZ);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Blue team respawn point set for " + event.getName() + " with radius X: " + radiusX + " Z: " + radiusZ);

        } else if (action.equalsIgnoreCase("RemoveTeamBlueRespawnPoint")) {
            if (!event.isTeamBased()) {
                player.sendMessage(ChatColor.RED + "This minigame is not team-based.");
                return;
            }
            arena.setBlueRespawnLocation(null);
            arena.setBlueRespawnRadiusX(0.0);
            arena.setBlueRespawnRadiusZ(0.0);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Blue team respawn point removed for " + event.getName());

        } else if (action.equalsIgnoreCase("SetJumpArea")) {
            if (!event.getRegionTypeIdentifier().equals("THIMBLE_ARENA")) {
                player.sendMessage(ChatColor.RED + "This action is only available for Thimble.");
                return;
            }
            arena.setJumpLocation(player.getLocation());
            arena.setJumpRadiusX(radiusX);
            arena.setJumpRadiusZ(radiusZ);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Jump area set for " + event.getName() + " with radius X: " + radiusX + " Z: " + radiusZ);

        } else if (action.equalsIgnoreCase("RemoveJumpArea")) {
            if (!event.getRegionTypeIdentifier().equals("THIMBLE_ARENA")) {
                player.sendMessage(ChatColor.RED + "This action is only available for Thimble.");
                return;
            }
            arena.setJumpLocation(null);
            arena.setJumpRadiusX(0.0);
            arena.setJumpRadiusZ(0.0);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Jump area removed for " + event.getName());

        } else if (action.equalsIgnoreCase("SetPlayerSpawn")) {
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... SetPlayerSpawn <name>");
                return;
            }
            String spawnName = args[6];
            Location raw = player.getLocation();
            Location centred = new Location(
                    raw.getWorld(),
                    raw.getBlockX() + 0.5,
                    raw.getBlockY(),
                    raw.getBlockZ() + 0.5,
                    raw.getYaw(),
                    raw.getPitch()
            );
            arena.setPlayerSpawn(spawnName, centred);
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Player spawn '" + spawnName + "' set for " + event.getName() + " (Arena: " + arena.getName() + ")");

        } else if (action.equalsIgnoreCase("RemovePlayerSpawn")) {
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... RemovePlayerSpawn <name>");
                return;
            }
            String spawnName = args[6];
            if (arena.removePlayerSpawn(spawnName)) {
                MinigameEventSystem.getInstance().saveArenas();
                player.sendMessage(ChatColor.GREEN + "Player spawn '" + spawnName + "' removed.");
            } else {
                player.sendMessage(ChatColor.RED + "No player spawn found with name: " + spawnName);
            }

        } else if (action.equalsIgnoreCase("ScanChests")) {
            Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
            if (r != null) {
                r.setChestsScanned(false);
                r.scanForChests();
                List<Location> chests = r.getSurvivalGamesChests();
                arena.setChestLocations(new ArrayList<>(chests));
                MinigameEventSystem.getInstance().saveArenas();
                player.sendMessage(ChatColor.GREEN + "Found and saved " + chests.size() + " chests for arena '" + arena.getName() + "'.");
            } else {
                player.sendMessage(ChatColor.RED + "No region found matching arena name: " + arena.getName());
            }

        } else if (action.equalsIgnoreCase("ClearChests")) {
            arena.clearChestLocations();
            MinigameEventSystem.getInstance().saveArenas();
            Region r = CorovaGuard.getInstance().getRegionManager().getRegion(arena.getName());
            if (r != null) {
                r.setChestsScanned(false);
                r.getSurvivalGamesChests().clear();
            }
            player.sendMessage(ChatColor.GREEN + "Chest locations cleared for arena '" + arena.getName() + "'.");

        } else if (action.equalsIgnoreCase("AddStagingRoom")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Staging rooms are only available for SpeedCraft.");
                return;
            }
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... AddStagingRoom <name>");
                return;
            }
            String roomName = args[6];
            if (arena.getStagingRoomsMap().containsKey(roomName)) {
                player.sendMessage(ChatColor.RED + "A staging room named '" + roomName + "' already exists. Remove it first or choose a different name.");
                return;
            }
            arena.addStagingRoom(roomName, player.getLocation());
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Staging room '" + roomName + "' added for arena '" + arena.getName() + "' at your current location.");

        } else if (action.equalsIgnoreCase("RemoveStagingRoom")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Staging rooms are only available for SpeedCraft.");
                return;
            }
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... RemoveStagingRoom <name>");
                return;
            }
            String roomName = args[6];
            if (arena.removeStagingRoom(roomName)) {
                MinigameEventSystem.getInstance().saveArenas();
                player.sendMessage(ChatColor.GREEN + "Staging room '" + roomName + "' removed from arena '" + arena.getName() + "'.");
            } else {
                player.sendMessage(ChatColor.RED + "No staging room found with name: " + roomName);
            }

        } else if (action.equalsIgnoreCase("ListStagingRooms")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Staging rooms are only available for SpeedCraft.");
                return;
            }
            Map<String, Location> rooms = arena.getStagingRoomsMap();
            if (rooms.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "No staging rooms configured for arena '" + arena.getName() + "'.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Staging rooms for '" + arena.getName() + "' (" + rooms.size() + "):");
                rooms.forEach((roomName, loc) -> player.sendMessage(ChatColor.GRAY + "  " + roomName + ": "
                        + ChatColor.WHITE + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            }

        } else if (action.equalsIgnoreCase("SetSpawnNode")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Spawn nodes are only available for SpeedCraft.");
                return;
            }
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... SetSpawnNode <name>");
                return;
            }
            String nodeName = args[6];
            if (arena.getSpawnNodesMap().containsKey(nodeName)) {
                player.sendMessage(ChatColor.RED + "A spawn node named '" + nodeName + "' already exists. Remove it first or choose a different name.");
                return;
            }
            arena.addSpawnNode(nodeName, player.getLocation());
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Spawn node '" + nodeName + "' added for arena '" + arena.getName() + "' at your current location. Survivors have an even chance of spawning at each configured node.");

        } else if (action.equalsIgnoreCase("RemoveSpawnNode")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Spawn nodes are only available for SpeedCraft.");
                return;
            }
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... RemoveSpawnNode <name>");
                return;
            }
            String nodeName = args[6];
            if (arena.removeSpawnNode(nodeName)) {
                MinigameEventSystem.getInstance().saveArenas();
                player.sendMessage(ChatColor.GREEN + "Spawn node '" + nodeName + "' removed from arena '" + arena.getName() + "'.");
            } else {
                player.sendMessage(ChatColor.RED + "No spawn node found with name: " + nodeName);
            }

        } else if (action.equalsIgnoreCase("ListSpawnNodes")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Spawn nodes are only available for SpeedCraft.");
                return;
            }
            Map<String, Location> nodes = arena.getSpawnNodesMap();
            if (nodes.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "No spawn nodes configured for arena '" + arena.getName() + "'.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Spawn nodes for '" + arena.getName() + "' (" + nodes.size() + "):");
                nodes.forEach((nodeName, loc) -> player.sendMessage(ChatColor.GRAY + "  " + nodeName + ": "
                        + ChatColor.WHITE + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            }

        } else if (action.equalsIgnoreCase("SetKillerSpawnNode")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Killer spawn nodes are only available for SpeedCraft.");
                return;
            }
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... SetKillerSpawnNode <name>");
                return;
            }
            String nodeName = args[6];
            if (arena.getKillerSpawnNodesMap().containsKey(nodeName)) {
                player.sendMessage(ChatColor.RED + "A killer spawn node named '" + nodeName + "' already exists. Remove it first or choose a different name.");
                return;
            }
            arena.addKillerSpawnNode(nodeName, player.getLocation());
            MinigameEventSystem.getInstance().saveArenas();
            player.sendMessage(ChatColor.GREEN + "Killer spawn node '" + nodeName + "' added for arena '" + arena.getName() + "' at your current location. Killers have an even chance of spawning at each configured node.");

        } else if (action.equalsIgnoreCase("RemoveKillerSpawnNode")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Killer spawn nodes are only available for SpeedCraft.");
                return;
            }
            if (args.length < 7) {
                player.sendMessage(ChatColor.RED + "Usage: ... RemoveKillerSpawnNode <name>");
                return;
            }
            String nodeName = args[6];
            if (arena.removeKillerSpawnNode(nodeName)) {
                MinigameEventSystem.getInstance().saveArenas();
                player.sendMessage(ChatColor.GREEN + "Killer spawn node '" + nodeName + "' removed from arena '" + arena.getName() + "'.");
            } else {
                player.sendMessage(ChatColor.RED + "No killer spawn node found with name: " + nodeName);
            }

        } else if (action.equalsIgnoreCase("ListKillerSpawnNodes")) {
            if (!event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                player.sendMessage(ChatColor.RED + "Killer spawn nodes are only available for SpeedCraft.");
                return;
            }
            Map<String, Location> nodes = arena.getKillerSpawnNodesMap();
            if (nodes.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "No killer spawn nodes configured for arena '" + arena.getName() + "'.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Killer spawn nodes for '" + arena.getName() + "' (" + nodes.size() + "):");
                nodes.forEach((nodeName, loc) -> player.sendMessage(ChatColor.GRAY + "  " + nodeName + ": "
                        + ChatColor.WHITE + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            }

        } else {
            player.sendMessage(ChatColor.RED + "Unknown edit action: " + action);
        }
    }

    private void handlePlayerLimits(CommandSender sender, Player player, MinigameEvent event, ArenaData arena, String[] args) {
        if (args.length < 7) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames edit " + event.getName() + " " + arena.getName() + " PlayerLimits <MinPlayers|MaxPlayers> <number>");
            return;
        }

        String limitType = args[5];
        int value = parseIntSilent(args[6], -1);

        if (value < 0) {
            sender.sendMessage(ChatColor.RED + "Invalid number: " + args[6]);
            return;
        }

        if (limitType.equalsIgnoreCase("MinPlayers")) {
            arena.setMinPlayers(value);
            sender.sendMessage(ChatColor.GREEN + "Min players for arena " + arena.getName() + " set to " + value);
        } else if (limitType.equalsIgnoreCase("MaxPlayers")) {
            arena.setMaxPlayers(value);
            sender.sendMessage(ChatColor.GREEN + "Max players for arena " + arena.getName() + " set to " + value);
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown limit type: " + limitType);
            return;
        }
        MinigameEventSystem.getInstance().saveArenas();
    }

    private boolean handleQueueSign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 7) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e minigames QueueSign <Set|Preset> <Minigame> <MinigameArena(s)> <MinPlayers> <MaxPlayers> [Rewards/NoRewards]");
            return true;
        }

        org.bukkit.block.Block target = player.getTargetBlockExact(5);
        if (target == null || !(target.getState() instanceof org.bukkit.block.Sign sign)) {
            sender.sendMessage(ChatColor.RED + "You must be looking at a sign.");
            return true;
        }

        String minigameName = args[3];
        MinigameEvent event = MinigameEventRegistrar.getEvent(minigameName);
        if (event == null) {
            sender.sendMessage(ChatColor.RED + "Unknown minigame: " + minigameName);
            return true;
        }

        String arenasRaw = args[4];
        List<String> arenas = Arrays.asList(arenasRaw.split(","));
        for (String arenaName : arenas) {
            if (event.getArena(arenaName) == null) {
                sender.sendMessage(ChatColor.RED + "Arena '" + arenaName + "' does not exist for " + event.getName());
                return true;
            }
        }

        int minPlayers = parseIntSilent(args[5], 2);
        int maxPlayers = parseIntSilent(args[6], 10);
        boolean rewards = true;
        if (args.length >= 8) {
            if (args[7].equalsIgnoreCase("NoRewards")) {
                rewards = false;
            }
        }

        com.example.corovaEvents.MinigameEvents.QueueSigns.QueueSignConfig.saveToSign(sign, event.getName(), arenas, minPlayers, maxPlayers, rewards);
        com.example.corovaEvents.MinigameEvents.QueueSigns.QueueSigns queueSign = new com.example.corovaEvents.MinigameEvents.QueueSigns.QueueSigns(sign.getLocation(), event.getName(), arenas, minPlayers, maxPlayers, rewards);
        com.example.corovaEvents.MinigameEvents.QueueSigns.QueueSignManager.getInstance().registerSign(queueSign);

        sender.sendMessage(ChatColor.GREEN + "Queue sign created for " + event.getName() + " (" + arenasRaw + ") with rewards " + (rewards ? "ENABLED" : "DISABLED") + "!");
        return true;
    }

    // -------------------------------------------------------------------------
    // Trigger dispatch
    // -------------------------------------------------------------------------

    private boolean handleTrigger(CommandSender sender, String[] args) {
        // New syntax: /c e <worldevents|minigames|playerevents> trigger <event> [arena]
        // args[0]=category, args[1]="trigger", args[2]=event, args[3]=arena (optional)
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e <WorldEvents|Minigames|PlayerEvents> trigger <event> [arena]");
            return true;
        }

        String category = args[0].toLowerCase();
        String eventName = args[2].toLowerCase();
        String arenaName = args.length > 3 ? args[3] : null;

        World world;
        if (sender instanceof Player p) {
            world = p.getWorld();
        } else {
            world = Bukkit.getWorlds().get(0);
        }

        CorovaEvents events = CorovaEvents.getInstance();
        switch (category) {
            case "worldevents":
                this.handleWorldEvent(sender, world, eventName, events);
                break;
            case "playerevents":
                this.handlePlayerEvent(sender, eventName, events);
                break;
            case "minigames":
                this.handleMinigameEvent(sender, eventName, arenaName);
                break;
            case "serverevents":
                if (eventName.equalsIgnoreCase("restart")) {
                    if (events.getServerRestart() != null) {
                        events.getServerRestart().trigger();
                        sender.sendMessage(ChatColor.GREEN + "Server restart sequence triggered!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Server restart system not initialized.");
                    }
                } else if (eventName.equalsIgnoreCase("restock")) {
                    new ServerShopRestock().trigger();
                    sender.sendMessage(ChatColor.GREEN + "Server shop restock triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Unknown server event: " + eventName);
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown category: " + args[0]);
        }

        return true;
    }

    private void handleWorldEvent(CommandSender sender, World world, String eventName, CorovaEvents events) {
        if (events == null) {
            sender.sendMessage(ChatColor.RED + "CorovaEvents not initialized!");
            return;
        }

        switch (eventName) {
            case "cursemoon": {
                CursedMoonEvent cursedMoon = events.getCursedMoon(world.getName());
                if (cursedMoon != null) {
                    cursedMoon.trigger();
                    sender.sendMessage(ChatColor.GREEN + "Cursed Moon event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Cursed Moon event not found for this world.");
                }
                break;
            }
            case "brokentime": {
                BrokenTime brokenTime = events.getBrokenTime(world.getName());
                if (brokenTime != null) {
                    brokenTime.trigger();
                    sender.sendMessage(ChatColor.GREEN + "Broken Time event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Broken Time event not found for this world.");
                }
                break;
            }
            case "netherinvasion": {
                NetherInvasion netherInvasion = events.getNetherInvasion(world.getName());
                if (netherInvasion != null) {
                    netherInvasion.trigger(true);
                    sender.sendMessage(ChatColor.GREEN + "Nether Invasion event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Nether Invasion event not found for this world.");
                }
                break;
            }
            case "meteorshower": {
                MeteorShower meteorShower = events.getMeteorShower(world.getName());
                if (meteorShower != null) {
                    meteorShower.trigger();
                    sender.sendMessage(ChatColor.GREEN + "Meteor Shower event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Meteor Shower event not found for this world.");
                }
                break;
            }
            case "sunwontsaveyou": {
                TheSunWontSaveYouToday sunWontSave = events.getSunWontSave(world.getName());
                if (sunWontSave != null) {
                    sunWontSave.trigger();
                    sender.sendMessage(ChatColor.GREEN + "The Sun Won't Save You Today event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "TheSunWontSaveYouToday event not found for this world.");
                }
                break;
            }
            case "solarflare": {
                SolarFlare solarFlare = events.getSolarFlare(world.getName());
                if (solarFlare != null) {
                    solarFlare.trigger();
                    sender.sendMessage(ChatColor.GREEN + "Solar Flare event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Solar Flare event not found for this world.");
                }
                break;
            }
            case "somethingfeelsoff": {
                SomethingFeelsOff somethingFeelsOff = events.getSomethingFeelsOff(world.getName());
                if (somethingFeelsOff != null) {
                    somethingFeelsOff.trigger();
                    sender.sendMessage(ChatColor.GREEN + "Something Feels Off event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Something Feels Off event not found for this world.");
                }
                break;
            }
            case "aurora": {
                Aurora aurora = events.getAurora(world.getName());
                if (aurora != null) {
                    aurora.trigger();
                    sender.sendMessage(ChatColor.AQUA + "Aurora event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Aurora event not found for this world.");
                }
                break;
            }
            case "dragonrespawn": {
                EnderDragonSpawnInEnd dragonRespawn = events.getDragonRespawn(world.getName());
                if (dragonRespawn != null) {
                    dragonRespawn.trigger();
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Ender Dragon Respawn event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Ender Dragon Respawn event not found for this world.");
                }
                break;
            }
            case "sleepambush": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Sleep Ambush can only be triggered by a player!");
                    break;
                }
                SleepAmbush sleepAmbush = events.getSleepAmbush(world.getName());
                if (sleepAmbush != null) {
                    sleepAmbush.trigger((Player) sender);
                    sender.sendMessage(ChatColor.RED + "Sleep Ambush event triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Sleep Ambush event not found for this world.");
                }
                break;
            }
            // -----------------------------------------------------------------
            // THE ANOMALY
            // -----------------------------------------------------------------
            case "anomaly": {
                TheAnomaly anomaly = TheAnomaly.getForWorld(world);
                if (anomaly != null) {
                    if (anomaly.isActive()) {
                        sender.sendMessage(ChatColor.DARK_PURPLE + "The Anomaly is already active in this world.");
                    } else {
                        anomaly.trigger();
                        sender.sendMessage(ChatColor.DARK_PURPLE + "The Anomaly event triggered!");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "The Anomaly is not initialized for this world.");
                }
                break;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Unknown world event: " + eventName);
        }
    }

    private void handlePlayerEvent(CommandSender sender, String eventName, CorovaEvents events) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Player events can only be triggered by players!");
            return;
        }

        if (events == null) {
            sender.sendMessage(ChatColor.RED + "CorovaEvents not initialized!");
            return;
        }

        switch (eventName) {
            case "firstjoin": {
                PlayerFirstJoin firstJoin = events.getPlayerFirstJoin();
                if (firstJoin != null) {
                    firstJoin.sendFirstJoinMessage(player);
                    sender.sendMessage(ChatColor.GREEN + "First join message triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "PlayerFirstJoin event not initialized.");
                }
                break;
            }
            case "rejoin": {
                PlayerRejoin rejoin = events.getPlayerRejoin();
                if (rejoin != null) {
                    rejoin.sendRejoinMessage(player);
                    sender.sendMessage(ChatColor.GREEN + "Rejoin message triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "PlayerRejoin event not initialized.");
                }
                break;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Unknown player event: " + eventName);
        }
    }

    private void handleMinigameEvent(CommandSender sender, String eventName, String arenaName) {
        MinigameEvent event = MinigameEventRegistrar.getEvent(eventName);
        if (event == null) {
            sender.sendMessage(ChatColor.RED + "Unknown minigame: " + eventName);
            return;
        }

        ArenaData arena = null;
        if (arenaName != null) {
            arena = event.getArena(arenaName);
            if (arena == null) {
                sender.sendMessage(ChatColor.RED + "Unknown arena '" + arenaName + "' for minigame " + event.getName());
                return;
            }
        }

        MinigameEventSystem.getInstance().startEvent(event, arena);
        if (arena != null) {
            sender.sendMessage(ChatColor.GREEN + "Minigame " + event.getName() + " started with arena " + arena.getName() + "!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Minigame " + event.getName() + " started!");
        }
    }

    private boolean handleServerEvents(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /c e serverevents <trigger|cancel|rushnextstep> [args...]");
            return true;
        }

        CorovaEvents events = CorovaEvents.getInstance();

        switch (args[1].toLowerCase()) {
            case "trigger":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /c e serverevents trigger <restart|restock>");
                    return true;
                }
                String eventName = args[2].toLowerCase();
                if (eventName.equals("restart")) {
                    if (events != null && events.getServerRestart() != null) {
                        events.getServerRestart().trigger();
                        sender.sendMessage(ChatColor.GREEN + "Server restart sequence triggered!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Server restart system not initialized.");
                    }
                } else if (eventName.equals("restock")) {
                    new ServerShopRestock().trigger();
                    sender.sendMessage(ChatColor.GREEN + "Server shop restock triggered!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Unknown server event: " + eventName);
                }
                return true;
            case "cancel":
                events.getServerRestart().cancel();
                sender.sendMessage(ChatColor.GREEN + "Server restart sequence cancelled.");
                return true;
            case "rushnextstep":
                events.getServerRestart().rushNextStep();
                sender.sendMessage(ChatColor.GREEN + "Server restart sequence rushed to next step.");
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown action: " + args[1]);
                return true;
        }
    }

    // -------------------------------------------------------------------------
    // Tab completions
    // -------------------------------------------------------------------------

    @Override
    public List<String> getSubcommandTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("WorldEvents", "Minigames", "PlayerEvents", "ServerEvents", "Join").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String action = args[0].toLowerCase();

        if (action.equalsIgnoreCase("worldevents")) {
            if (args.length == 2) {
                return Arrays.asList("trigger", "register", "cancel").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            // cancel branch: /c e worldevents cancel <event|all>
            if (args[1].equalsIgnoreCase("cancel")) {
                if (args.length == 3) {
                    List<String> options = new ArrayList<>(CorovaEvents.getInstance().getActiveWorldEventKeys());
                    options.add("All");
                    return options.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }
            // trigger branch: /c e worldevents trigger <event>
            if (args[1].equalsIgnoreCase("trigger")) {
                if (args.length == 3) {
                    return Arrays.asList(
                                    "Anomaly", "Aurora", "BrokenTime", "CurseMoon", "DragonRespawn",
                                    "MeteorShower", "NetherInvasion", "SleepAmbush", "SolarFlare",
                                    "SomethingFeelsOff", "SunWontSaveYou").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }
            // register branch: /c e worldevents register <event> [enable|disable]
            if (args[1].equalsIgnoreCase("register")) {
                if (args.length == 3) {
                    return CorovaEvents.WORLD_EVENT_KEYS.stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 4) {
                    return Arrays.asList("enable", "disable").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        if (action.equalsIgnoreCase("playerevents")) {
            if (args.length == 2) {
                return Collections.singletonList("trigger").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("trigger")) {
                return Arrays.asList("FirstJoin", "Rejoin").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (action.equalsIgnoreCase("serverevents")) {
            if (args.length == 2) {
                return Arrays.asList("trigger", "cancel", "rushnextstep").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("trigger")) {
                return Arrays.asList("restart", "restock").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (action.equalsIgnoreCase("minigames")) {
            if (args.length == 2) {
                return Arrays.asList("trigger", "Edit", "SetEventEndLocation", "RushNextStep", "AddFirework",
                                "RemoveFirework", "ListFireworks", "ClearFireworks", "RandomEvents",
                                "registeredevents", "rewardtable", "reward", "cancel", "queue", "QueueSign").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("rushnextstep")) {
                return MinigameEventSystem.getInstance().getActiveEvents().stream()
                        .map(e -> e.getName().replace(" ", "_"))
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            // cancel branch: /c e minigames cancel <event|all>
            if (args[1].equalsIgnoreCase("cancel")) {
                if (args.length == 3) {
                    List<String> options = new ArrayList<>();
                    for (MinigameEvent current : MinigameEventSystem.getInstance().getActiveEvents()) {
                        options.add(current.getName().replace(" ", "_"));
                    }
                    options.add("All");
                    return options.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }

            // trigger branch: /c e minigames trigger <event> [arena]
            if (args[1].equalsIgnoreCase("trigger")) {
                if (args.length == 3) {
                    return MinigameEventRegistrar.getAllEvents().stream()
                            .map(e -> e.getName().replace(" ", "_"))
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 4) {
                    MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                    if (event != null) {
                        return event.getArenas().stream()
                                .map(ArenaData::getName)
                                .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    return Collections.emptyList();
                }
                return Collections.emptyList();
            }

            String subAction = args[1].toLowerCase();

            if (subAction.equals("queuesign")) {
                if (args.length == 3) {
                    return Arrays.asList("Set", "Preset").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 4) {
                    return MinigameEventRegistrar.getAllEvents().stream()
                            .map(e -> e.getName().replace(" ", "_"))
                            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 5) {
                    MinigameEvent event = MinigameEventRegistrar.getEvent(args[3]);
                    if (event != null) {
                        return event.getArenas().stream()
                                .map(ArenaData::getName)
                                .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
                if (args.length == 6) return Collections.singletonList("<MinPlayers>");
                if (args.length == 7) return Collections.singletonList("<MaxPlayers>");
                if (args.length == 8) {
                    return Arrays.asList("Rewards", "NoRewards").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[7].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }

            if (subAction.equals("queue")) {
                if (args.length == 3) {
                    return Arrays.asList("list", "watch").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 4) {
                    List<String> options = new ArrayList<>();
                    if (args[2].equalsIgnoreCase("watch")) {
                        options.add("all");
                    }
                    options.addAll(MinigameEventRegistrar.getAllEvents().stream()
                            .map(e -> e.getName().replace(" ", "_"))
                            .collect(Collectors.toList()));
                    return options.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }

            if (subAction.equals("registeredevents")) {
                if (args.length == 3) {
                    return MinigameEventRegistrar.getAllEvents().stream()
                            .map(e -> e.getName().replace(" ", "_"))
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 4) {
                    MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                    if (event != null) {
                        return event.getArenas().stream()
                                .map(ArenaData::getName)
                                .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    return Collections.emptyList();
                }
                if (args.length == 5) {
                    return Arrays.asList("add", "remove").stream()
                            .filter(s -> s.startsWith(args[4].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }

            if (subAction.equals("rewardtable") || subAction.equals("reward")) {
                if (subAction.equals("reward")) {
                    if (args.length == 3) {
                        return Collections.singletonList("table").stream()
                                .filter(s -> s.startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    // Shift arguments and recurse for reward table logic
                    // /c e minigames reward table <minigame> ...
                    // args: [minigames, reward, table, <minigame>, ...]
                    // we want: [minigames, rewardtable, <minigame>, ...]
                    String[] shifted = new String[args.length - 1];
                    shifted[0] = args[0]; // "minigames"
                    shifted[1] = "rewardtable";
                    if (args.length > 3) {
                        System.arraycopy(args, 3, shifted, 2, args.length - 3);
                    }
                    return getSubcommandTabCompletions(sender, shifted);
                }

                if (args.length == 3) {
                    List<String> eventNames = MinigameEventRegistrar.getAllEvents().stream()
                            .map(e -> e.getName().toLowerCase().replace(" ", "_"))
                            .collect(Collectors.toList());
                    eventNames.add("vote");
                    return eventNames.stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 4) {
                    return Arrays.asList("add", "remove", "settakes", "list", "clear").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args.length == 5) {
                    String rtAction = args[3].toLowerCase();
                    if (rtAction.equals("remove")) {
                        MinigameRewardManager mgr = MinigameRewardManager.getInstance();
                        if (mgr != null && mgr.hasTable(args[2])) {
                            return mgr.getTable(args[2]).getPool().stream()
                                    .map(MinigameRewardEntry::getItemId)
                                    .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                    .collect(Collectors.toList());
                        }
                    }
                    if (rtAction.equals("add")) {
                        List<String> items = new ArrayList<>(getVanillaItemNames());
                        items.addAll(getCorovaItemNames());
                        return items.stream()
                                .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    if (rtAction.equals("settakes")) return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9");
                }
                if (args.length == 6 && args[3].equalsIgnoreCase("add")) return Collections.singletonList("<minAmount>");
                if (args.length == 7 && args[3].equalsIgnoreCase("add")) return Collections.singletonList("<maxAmount>");
                if (args.length == 8 && args[3].equalsIgnoreCase("add")) return Collections.singletonList("<weight>");
                return Collections.emptyList();
            }

            if (subAction.equals("edit") && args.length == 3) {
                return MinigameEventRegistrar.getAllEvents().stream()
                        .map(e -> e.getName().replace(" ", "_"))
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (subAction.equals("randomevents") && args.length == 3) {
                return Arrays.asList("ON", "OFF").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args.length == 3 && (subAction.equals("addfirework") || subAction.equals("removefirework")
                    || subAction.equals("listfireworks") || subAction.equals("clearfireworks"))) {
                return Arrays.asList("EventStart", "WaitingRoom", "EventEnd").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args.length == 4 && (subAction.equals("addfirework") || subAction.equals("removefirework")
                    || subAction.equals("listfireworks") || subAction.equals("clearfireworks"))) {
                return MinigameEventRegistrar.getAllEvents().stream()
                        .map(e -> e.getName().replace(" ", "_"))
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args.length == 4 && subAction.equals("edit")) {
                String eventName = args[2];
                MinigameEvent event = MinigameEventRegistrar.getEvent(eventName);
                if (event != null) {
                    return event.getArenas().stream()
                            .map(ArenaData::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }

            if (args.length == 5 && (subAction.equals("addfirework") || subAction.equals("removefirework")
                    || subAction.equals("listfireworks") || subAction.equals("clearfireworks"))) {
                String eventName = args[3];
                MinigameEvent event = MinigameEventRegistrar.getEvent(eventName);
                if (event != null) {
                    return event.getArenas().stream()
                            .map(ArenaData::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }

            if (args.length == 5 && subAction.equals("edit")) {
                return Arrays.asList("LocationSettings", "PlayerLimits", "Laps", "EventSettings", "Delete").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args.length == 6 && subAction.equals("edit")) {
                String category = args[4];
                if (category.equalsIgnoreCase("EventSettings")) {
                    return Arrays.asList("FireResistanceDuration").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (category.equalsIgnoreCase("LocationSettings")) {
                    MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                    List<String> options = new ArrayList<>(Arrays.asList(
                            "SetEventStartLocation", "RemoveEventStartLocation",
                            "SetWaitingRoomLocation", "RemoveWaitingRoomLocation",
                            "SetEndLocation", "RemoveEndLocation"));

                    if (event != null) {
                        if (event.getRegionTypeIdentifier().equals("SPEEDCRAFT_ARENA")) {
                            options.addAll(Arrays.asList(
                                    "AddStagingRoom", "RemoveStagingRoom", "ListStagingRooms",
                                    "SetSpawnNode", "RemoveSpawnNode", "ListSpawnNodes",
                                    "SetKillerSpawnNode", "RemoveKillerSpawnNode", "ListKillerSpawnNodes"));
                        }
                        if (event.getRegionTypeIdentifier().equals("THIMBLE_ARENA")) {
                            options.add("SetJumpArea");
                            options.add("RemoveJumpArea");
                        }
                        if (event.getRegionTypeIdentifier().equals("MINEKART_ARENA")) {
                            options.add("AddCheckpoint");
                            options.add("RemoveCheckpoint");
                            options.add("ClearCheckpoints");
                            options.add("ListCheckpoints");
                            options.add("AddLapCheckpoint");
                            options.add("RemoveLapCheckpoint");
                            options.add("ClearLapCheckpoints");
                            options.add("ListLapCheckpoints");
                        }
                        if (event.isTeamBased()) {
                            options.addAll(Arrays.asList(
                                    "SetTeamRedSpawnpoint", "RemoveTeamRedSpawnpoint",
                                    "SetTeamBlueSpawnpoint", "RemoveTeamBlueSpawnpoint",
                                    "SetTeamRedRespawnPoint", "RemoveTeamRedRespawnPoint",
                                    "SetTeamBlueRespawnPoint", "RemoveTeamBlueRespawnPoint"));
                        }
                        if (event.getRegionTypeIdentifier().equals("SURVIVAL_GAMES_ARENA")) {
                            options.add("SetPlayerSpawn");
                            options.add("RemovePlayerSpawn");
                            options.add("ScanChests");
                            options.add("ClearChests");
                        }
                    }
                    return options.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (category.equalsIgnoreCase("PlayerLimits")) {
                    return Arrays.asList("MinPlayers", "MaxPlayers").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (category.equalsIgnoreCase("Laps")) {
                    return Arrays.asList("set", "remove").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }

            if (args.length == 7 && subAction.equals("edit")) {
                String category = args[4];
                if (category.equalsIgnoreCase("PlayerLimits") || category.equalsIgnoreCase("Laps") || category.equalsIgnoreCase("EventSettings")) {
                    return Collections.singletonList("<number>");
                }
                if (category.equalsIgnoreCase("LocationSettings")) {
                    String actionType = args[5];
                    if (actionType.equalsIgnoreCase("SetPlayerSpawn")) {
                        return Collections.singletonList("<name>");
                    }
                    if (actionType.equalsIgnoreCase("RemovePlayerSpawn")) {
                        MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                        if (event != null) {
                            ArenaData arena = event.getArena(args[3]);
                            if (arena != null) {
                                return arena.getPlayerSpawns().keySet().stream()
                                        .filter(s -> s.toLowerCase().startsWith(args[6].toLowerCase()))
                                        .collect(Collectors.toList());
                            }
                        }
                        return Collections.singletonList("<name>");
                    }
                    if (actionType.equalsIgnoreCase("AddStagingRoom")) {
                        return Collections.singletonList("<name>");
                    }
                    if (actionType.equalsIgnoreCase("RemoveStagingRoom")) {
                        MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                        if (event != null) {
                            ArenaData arena = event.getArena(args[3]);
                            if (arena != null) {
                                return arena.getStagingRoomsMap().keySet().stream()
                                        .filter(s -> s.toLowerCase().startsWith(args[6].toLowerCase()))
                                        .collect(Collectors.toList());
                            }
                        }
                        return Collections.singletonList("<name>");
                    }
                    if (actionType.equalsIgnoreCase("SetSpawnNode") || actionType.equalsIgnoreCase("SetKillerSpawnNode")) {
                        return Collections.singletonList("<name>");
                    }
                    if (actionType.equalsIgnoreCase("RemoveSpawnNode")) {
                        MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                        if (event != null) {
                            ArenaData arena = event.getArena(args[3]);
                            if (arena != null) {
                                return arena.getSpawnNodesMap().keySet().stream()
                                        .filter(s -> s.toLowerCase().startsWith(args[6].toLowerCase()))
                                        .collect(Collectors.toList());
                            }
                        }
                        return Collections.singletonList("<name>");
                    }
                    if (actionType.equalsIgnoreCase("RemoveKillerSpawnNode")) {
                        MinigameEvent event = MinigameEventRegistrar.getEvent(args[2]);
                        if (event != null) {
                            ArenaData arena = event.getArena(args[3]);
                            if (arena != null) {
                                return arena.getKillerSpawnNodesMap().keySet().stream()
                                        .filter(s -> s.toLowerCase().startsWith(args[6].toLowerCase()))
                                        .collect(Collectors.toList());
                            }
                        }
                        return Collections.singletonList("<name>");
                    }
                }
            }

            if (args.length == 6 && subAction.equals("removefirework")) {
                try {
                    FireworkSpawner.FireworkTrigger trigger = FireworkTrigger.valueOf(args[2].toUpperCase()
                            .replace("EVENTSTART", "EVENT_START")
                            .replace("WAITINGROOM", "WAITING_ROOM")
                            .replace("EVENTEND", "EVENT_END"));
                    return MinigameEventSystem.getInstance().getFireworkSpawner()
                            .listSpawnerNames(args[3], args[4], trigger).stream()
                            .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }
        }

        if (action.equalsIgnoreCase("join")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("all");
                options.addAll(MinigameEventRegistrar.getAllEvents().stream()
                        .map(e -> e.getName().replace(" ", "_"))
                        .collect(Collectors.toList()));

                com.example.corovaEvents.MinigameEvents.QueueSigns.QueueSignManager queueMgr = com.example.corovaEvents.MinigameEvents.QueueSigns.QueueSignManager.getInstance();
                if (queueMgr != null) {
                    options.addAll(queueMgr.getAllQueueIdentifiers());
                }

                options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                return options.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                MinigameEvent event = MinigameEventRegistrar.getEvent(args[1]);
                if (event != null) {
                    return Collections.singletonList("all").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private List<String> getArenaRegions(RegionType type) {
        List<String> regions = new ArrayList<>();
        for (Region region : CorovaGuard.getInstance().getRegionManager().getRegions()) {
            if (type != null) {
                if (region.isType(type)) {
                    regions.add(region.getName());
                }
            } else {
                if (region.isType(RegionType.SPLEEF_ARENA) || region.isType(RegionType.TNTRUN_ARENA)
                        || region.isType(RegionType.SHLAP_ARENA) || region.isType(RegionType.THIMBLE_ARENA)
                        || region.isType(RegionType.MOB_ARENA) || region.isType(RegionType.RANDOMIZED_GUN_DEATHMATCH)
                        || region.isType(RegionType.MINEKART_ARENA) || region.isType(RegionType.SURVIVAL_GAMES_ARENA)
                        || region.isType(RegionType.SPEEDCRAFT_ARENA)) {
                    regions.add(region.getName());
                }
            }
        }
        return regions;
    }

    private List<String> getVanillaItemNames() {
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(m -> m.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> getCorovaItemNames() {
        try {
            Class<?> registryClass = Class.forName("com.example.corovaItems.ItemRegistry");
            Map<String, ?> items = (Map<String, ?>) registryClass.getMethod("getAllItems").invoke(null);
            return new ArrayList<>(items.keySet());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static int parseIntSilent(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDoubleSilent(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}