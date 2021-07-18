package de.luzifer.asm.commands;

import de.luzifer.asm.api.mob.Mob;
import de.luzifer.asm.api.mob.task.SpawnTask;
import de.luzifer.asm.api.mob.task.SpawnTaskData;
import de.luzifer.asm.api.mob.task.SpawnTaskId;
import de.luzifer.asm.api.user.User;
import de.luzifer.asm.api.user.UserService;
import de.luzifer.asm.config.Variables;
import de.luzifer.asm.utils.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ASMCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if(!(sender instanceof Player)) {

            sender.sendMessage("I'm sorry, you can't do that.");
            return true;
        }

        Player player = (Player) sender;
        User user = UserService.getOrCreateUser(player.getUniqueId());

        if(command.getName().equalsIgnoreCase("asyncspawnmob")) {

            if(!isPermitted(player)) {
                player.sendMessage(ChatUtil.formatMessage("§cYou don't have the permission to do that."));
                return true;
            }

            Location location = getTargetBlock(player).getLocation();

            switch (args.length) {
                case 1:
                    if(args[0].equalsIgnoreCase("list")) {

                        player.sendMessage(ChatUtil.formatMessage("§7You have §8[§a" + user.getTaskIds().size() + "§8] §7task(s) running:"));
                        for(SpawnTaskId taskId : user.getTaskIds())
                            player.sendMessage(ChatUtil.formatMessage("§7- §f" + taskId.getTaskId()));

                        return true;
                    }
                case 2:
                    if(args[0].equalsIgnoreCase("stop")) {

                        int taskId;
                        try {
                            taskId = Integer.parseInt(args[1]);
                        } catch (Exception e) {
                            player.sendMessage(ChatUtil.formatMessage("§7Please enter a valid TaskId."));
                            return true;
                        }

                        if(!user.getTaskIds().contains(SpawnTaskId.of(taskId))) {
                            player.sendMessage(ChatUtil.formatMessage("§7Couldn't find a Task with the id §f" + taskId));
                            return true;
                        }

                        Bukkit.getScheduler().cancelTask(taskId);

                        player.sendMessage(ChatUtil.formatMessage("§7Task stopped successfully. TaskId: §f" + taskId));
                        user.getTaskIds().remove(SpawnTaskId.of(taskId));
                        return true;
                    } else if(args[0].equalsIgnoreCase("spawn")) {

                        if(assertEntityTypeDoesNotExist(args[1], user))
                            return true;

                        spawnASingleEntity(args[1], user, location);
                        return true;
                    }
                case 3:
                    if(args[0].equalsIgnoreCase("spawn")) {

                        if(assertEntityTypeDoesNotExist(args[1], user))
                            return true;

                        int amount = getAmount(args[2], user);

                        if(checkAmountForInvalid(amount, player))
                            return true;

                        if(amount == 1) {

                            spawnASingleEntity(args[1], user, location);
                            return true;
                        }

                        prepareAndStartSpawnTask(amount, args[1], user, location);
                        player.sendMessage(ChatUtil.formatMessage("§7Spawning " + amount + " " + args[1].toUpperCase() + "s"));

                        return true;
                    }
                default:
                    sendHelpList(player);
            }
        }
        return true;
    }

    private boolean isPermitted(Player player) {
        return player.hasPermission(Variables.permission) || player.isOp();
    }

    private void sendHelpList(Player player) {

        player.sendMessage(ChatUtil.formatMessage("§a§oProtip: Use §6§o/asm"));

        player.sendMessage(ChatUtil.formatMessage("§6/asyncspawnmob"));
        player.sendMessage(ChatUtil.formatFollowMessage("§8All commands"));

        player.sendMessage(ChatUtil.formatMessage("§6/asyncspawnmob list"));
        player.sendMessage(ChatUtil.formatFollowMessage("§8List of running tasks"));

        player.sendMessage(ChatUtil.formatMessage("§6/asyncspawnmob spawn <type>"));
        player.sendMessage(ChatUtil.formatFollowMessage("§8Spawn 1 entity of type X"));

        player.sendMessage(ChatUtil.formatMessage("§6/asyncspawnmob spawn <type> <amount>"));
        player.sendMessage(ChatUtil.formatFollowMessage("§8Spawn X entities of type X"));

        player.sendMessage(ChatUtil.formatMessage("§6/asyncspawnmob stop <id>"));
        player.sendMessage(ChatUtil.formatFollowMessage("§8Stop SpawnTask with the id X"));
    }

    private boolean assertEntityTypeDoesNotExist(String entityTypeName, User user) {

        if(entityTypeName == null)
            throw new IllegalArgumentException();

        try {
            Mob.fromName(entityTypeName.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {

            user.asPlayer().sendMessage(ChatUtil.formatMessage("§7Couldn't find a Mob named: §c" + entityTypeName));
            return true;
        }
        return false;
    }

    private void spawnEntity(String entityTypeName, User user, Location spawnAt) {

        Mob mob = Mob.fromName(entityTypeName.toUpperCase());
        user.asPlayer().getWorld().spawnEntity(spawnAt.clone().add(0.5, 1, 0.5), mob.convertToEntityType());
    }

    private int getAmount(String amountString, User user) {

        int amount = 1;
        try {
            amount = Integer.parseInt(amountString);
        } catch (Exception e) {
            user.asPlayer().sendMessage(ChatUtil.formatMessage("§7Invalid amount: Amount has been set to 1."));
        }
        return amount;
    }

    private void spawnASingleEntity(String entityName, User user, Location location) {

        spawnEntity(entityName, user, location);
        user.asPlayer().sendMessage(ChatUtil.formatMessage("§7Spawned 1 " + entityName.toUpperCase()));
    }

    private boolean checkAmountForInvalid(int amount, Player player) {

        if(amount == 0) {

            player.sendMessage(ChatUtil.formatMessage("§7Can't spawn 0 entities."));
            return true;
        }

        if(amount >= Variables.maxSpawningAmount) {

            player.sendMessage(ChatUtil.formatMessage("§7No more mobs than §c" + Variables.maxSpawningAmount + "§7 may be spawned."));
            return true;
        }
        return false;
    }

    private void prepareAndStartSpawnTask(int amount, String entityName, User user, Location location) {

        List<SpawnTaskData> spawnTaskDataList = new ArrayList<>();
        for(int i = 0; i < amount; i++) {
            spawnTaskDataList.add(new SpawnTaskData(entityName, location));
        }

        SpawnTask spawnTask = new SpawnTask(spawnTaskDataList, user);
        spawnTask.start();
    }

    private Block getTargetBlock(Player player) {

        BlockIterator iterator = new BlockIterator(player, Variables.spawningDistance);
        Block lastBlock = iterator.next();
        while (iterator.hasNext()) {
            lastBlock = iterator.next();
            if (lastBlock.getType() == Material.AIR) {
                continue;
            }
            break;
        }
        return lastBlock;
    }

}
