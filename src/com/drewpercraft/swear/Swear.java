package com.drewpercraft.swear;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.JailsAPI;


public class Swear extends JavaPlugin implements Listener {

	private static final Logger log = Logger.getLogger("Minecraft");

	public static Economy economy = null;
	
	List<String> blacklist;
	List<String> whitelist;
	List<String> regexp;
	Double fine;
	Double damage;
	String playerMessage;
	String broadcast;
	Boolean smite;
	String owner;
	Player player;
	
	Boolean useJailAPI = false;
	
	class KickPlayer implements Callable<Void> {
		Player player;
		String message;
		
		KickPlayer(Player p, String m) { 
			player = p;
			message = m;
		}
		
		@Override
		public Void call() {
			player.kickPlayer(message);
			return null;
		}
	}
	
	public void onEnable(){ 
		if (!setupEconomy() ) {
            log.info(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
		
		reload();
		
		if (getServer().getPluginManager().getPlugin("Jail") != null) {
            useJailAPI = true;
            log.info("Jail plugin found. Players will be jailed instead of kicked.");
        }
		
		getServer().getPluginManager().registerEvents(this, this);
		log.info("Swear.Jar has been enabled.");
	}
	
	
	
	public void onDisable(){ 
		log.info("Swear.Jar has been disabled.");
	}
	
	@EventHandler
	public void onChat(AsyncPlayerChatEvent event) {
				
		player = event.getPlayer();
		if (!(player instanceof Player)) return;
		
		String message = event.getMessage();
		message = message.toLowerCase();
		
		// Remove all the white listed words
		Iterator<String> cleanWordList = whitelist.iterator();
		while (cleanWordList.hasNext()) {
			String cleanWord = cleanWordList.next();
			message = message.replaceAll(cleanWord, "");
		}
		
		
		Iterator<String> dirtyWordList = blacklist.iterator();
		boolean pottyMouth = false;
		String smartMessage = message.replaceAll("[^a-z ]", "").replaceAll(" ? ", "");
		while (dirtyWordList.hasNext()) {
			String dirtyWord = dirtyWordList.next();
			if (message.contains(dirtyWord)) {
				if (player.isPermissionSet("swear.debug")) {
					player.sendMessage("Found dirtyWord: " + dirtyWord + " in " + message);
				}
				pottyMouth = true;
				break;
			}
			// Now to make it smart...
			
			if (smartMessage.contains(dirtyWord)) {
				if (player.isPermissionSet("swear.debug")) {
					player.sendMessage("Found smartMessage: " + dirtyWord + " in " + smartMessage);
				}
				pottyMouth = true;
				player.sendMessage("Nice try....");
				break;
			}
		}
		
		if (!pottyMouth) {
			// Regexp take a little more time, so only run this if we haven't determined them to be a potty mouth yet
			Iterator<String> regexpList = regexp.iterator();
			while (regexpList.hasNext()) {
				String r = regexpList.next();
				Pattern pattern = Pattern.compile(r);
				Matcher matcher = pattern.matcher(message);
				if (matcher.find()) {
					if (player.isPermissionSet("swear.debug")) {
						player.sendMessage("Found regexp: " + r + " in " + message);
					}
					pottyMouth = true;
					log.info("Matched on pattern: " + r);
					break;
				}
			}
		}
		
		if (pottyMouth) {
			event.setCancelled(true);
			player.damage(damage);
			if (smite) {
				player.getWorld().strikeLightningEffect(player.getLocation());
			}
			if (fine > 0) {
				double donation = fine;
				if (!economy.has(player, donation)) {
					donation = economy.getBalance(player); 
				}
				economy.withdrawPlayer(player, donation);
				economy.depositPlayer(owner, donation);
				log.info("Swear jar added " + donation + " to " + owner + ".");

				Player ownerPlayer = getServer().getPlayer(owner);
				if (ownerPlayer instanceof Player && ownerPlayer.isOnline()) {
					getServer().getPlayer(owner).sendMessage("$" + donation + " was put in your swear jar because " + player.getName() + " said: " + event.getMessage());
				}
				if (donation < fine) {
				    if (useJailAPI) {
				    	try {
				    		if (!JailsAPI.getJailManager().isPlayerJailed(player.getUniqueId())) { 
					    		Prisoner prisoner = new Prisoner(player, JailsAPI.getTimeFromString("5m"), "Potty Mouth");
					    		Jail jail = JailsAPI.getJailManager().getNearestJail(player);
					    		if (jail.getFirstEmptyCell() == null) {
					    			//Find any jail
					    			Iterator<Jail> jailIT = JailsAPI.getJailManager().getJails().iterator();
					    			while (jailIT.hasNext()) {
					    				jail = jailIT.next();
					    				if (jail.getFirstEmptyCell() != null) {
					    					break;
					    				}
					    			}
					    		}
					    		//If there are no empty cells, kick the player
					    		if (jail.getFirstEmptyCell() == null) {
					    			Bukkit.getServer().getScheduler().callSyncMethod(this, new KickPlayer(player, "You ran out of money for the swear jar."));
					    		}
					    		JailsAPI.getPrisonerManager().prepareJail(jail, jail.getFirstEmptyCell(), player, prisoner);
				    		}else{
				    			JailsAPI.getJailManager().getPrisoner(player.getUniqueId()).addTime(JailsAPI.getTimeFromString("1m"));
				    		}
				    	}
				    	catch (Exception e) {
				    		log.warning("Attempt to jail player failed");
				    		log.warning(e.getMessage());
				    	}
				    }else{
				    	Bukkit.getServer().getScheduler().callSyncMethod(this, new KickPlayer(player, "You ran out of money for the swear jar."));
				    }
				}
			}
			
			if (playerMessage.length() > 0) {
				player.sendMessage(playerMessage.replace("{PLAYER}", player.getDisplayName()));
			}
			
			if (broadcast.length() > 0) {
				player.getServer().broadcastMessage(broadcast.replace("{PLAYER}", player.getDisplayName()));
			}
			
			log.info(player.getName() + " has made a donation to the swear jar for saying " + event.getMessage());
		}
		
		// Now substitute all the wtf's for Well That's Fantastic!
		message = event.getMessage();
		message = message.replaceAll("wtf", "well that's fantastic");
		message = message.replaceAll("WTF", "Well That's Fantastic");
        if (message.toLowerCase().startsWith("yo ") && !player.getName().equals("hitechwizard")) {
            message = "Yo™ is Copyright © 2015 by Hitechwizard. Unauthorized use without royalty payment is strictly prohibited.";
        }
		event.setMessage(message);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (sender instanceof Player) {
			if (command.getName().equalsIgnoreCase("swear")) {
				
				boolean success = false;
				String userCommand;
				if (args.length < 1) {
					userCommand = "info";
				}else{
					userCommand = args[0];
				}
				
				// All of the info type commands
				if (userCommand.equalsIgnoreCase("owner")) success = commandOwner(sender, args);
				if (userCommand.equalsIgnoreCase("fine")) success = commandFine(sender, args);
				if (userCommand.equalsIgnoreCase("damage")) success = commandDamage(sender, args);
				if (userCommand.equalsIgnoreCase("info")) {
					commandOwner(sender, args);
					commandFine(sender, args);
					commandDamage(sender, args);
					success = true;
				}
				if (userCommand.equalsIgnoreCase("version")) success = commandVersion(sender, args);
				if (success) return true;
				
				
				// All admin type commands
				success = false;
				if (sender.isPermissionSet("swear.config")) {
					if (userCommand.equalsIgnoreCase("setowner")) success = commandSetOwner(sender, args);
					if (userCommand.equalsIgnoreCase("setfine")) success = commandSetFine(sender, args);
					if (userCommand.equalsIgnoreCase("setdamage")) success = commandSetDamage(sender, args);
					if (userCommand.equalsIgnoreCase("addword")) success = commandAddWord(sender, args);
					if (userCommand.equalsIgnoreCase("delword")) success = commandDelWord(sender, args);
					if (userCommand.equalsIgnoreCase("whitelist")) success = commandWhitelist(sender, args);
					
					if (success) {
						try {
							this.getConfig().save("plugins/Swear/config.yml");
						} catch (IOException e) {
							log.info("Swear jar could not save the configuration file.");
						}
					}
					if (userCommand.equalsIgnoreCase("refund")) success = commandRefund(sender, args);
					if (userCommand.equalsIgnoreCase("show")) success = commandShow(sender, args);
					if (userCommand.equalsIgnoreCase("reload")) success = commandReload(sender, args);
				}else{
					sender.sendMessage("You do not have permission to that command.");
					success = false;
				}
				return success;
			}
		}
		return false;
	}
	
	public boolean commandVersion(CommandSender sender, String[] args) {
		sender.sendMessage("&6Swear.jar: &9" + getDescription().getVersion());
		return true;
	}
	
	public boolean commandOwner(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.info")) {
			sender.sendMessage("Swear Jar proceeds go to " + owner);
			return true;
		}
		return false;
	}
	
	public boolean commandSetOwner(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			Player newOwner = Bukkit.getServer().getPlayerExact(args[1]);
			if (newOwner instanceof Player) {
				owner = args[1];
				this.getConfig().set("owner", owner);
				sender.sendMessage("Swear Jar proceeds now go to " + owner);
				return true;
			}
			sender.sendMessage("Could not find player " + args[1]);
		}
		return false;
	}
	
	public boolean commandFine(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.info")) {
			sender.sendMessage("The fine for swearing is $" + String.format("%5.2f", fine));
		}
		return false;
	}
	
	public boolean commandSetFine(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			fine = Double.parseDouble(args[1]);
			this.getConfig().set("fine", fine);
			sender.sendMessage("The swearing fine is now set to " + String.format("%5.2f", fine));	
			return true;
		}
		return false;
	}
	
	public boolean commandDamage(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.info")) {
			sender.sendMessage("Players that swear will be dealt " + damage + " damage.");
			return true;
		}
		return false;
	}
	
	public boolean commandSetDamage(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			damage = Double.parseDouble(args[1]);
			this.getConfig().set("damage", damage);
			sender.sendMessage("The damage dealt for swearing is now set to " + String.format("%2d", damage));	
			return true;
		}
		return false;
	}
	
	public boolean commandAddWord(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			blacklist.add(args[1]);
			this.getConfig().set("blacklist", blacklist);
			sender.sendMessage("Word added to blacklist.");	
			return true;
		}
		return false;		
	}

	public boolean commandDelWord(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			Integer found = blacklist.indexOf(args[1]);
			if (found > -1) {
				blacklist.remove(found);
				this.getConfig().set("blacklist", blacklist);
				sender.sendMessage("Word removed from blacklist.");
			}else{
				sender.sendMessage("Could not find word in blacklist.");
				return false;
			}
			return true;
		}
		return false;		
	}
	
	public boolean commandWhitelist(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			whitelist.add(args[1]);
			this.getConfig().set("whitelist", whitelist);
			sender.sendMessage("Word added to whitelist.");	
			return true;
		}
		return false;		
	}
	
	public boolean commandShow(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			sender.sendMessage("Blacklist: " + blacklist.toString());
			sender.sendMessage("Whitelist: " + whitelist.toString());
			return true;
		}else{
			return false;
		}
	}
	
	public boolean commandReload(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			reload();
			sender.sendMessage("Swear config reloaded.");
			return true;
		}else{
			return false;
		}
	}
	
	public boolean commandRefund(CommandSender sender, String[] args) {
		if (sender.isPermissionSet("swear.config")) {
			String refundTo = args[1];
			Double amount = fine;
			if (args.length == 3) {
				amount = Double.parseDouble(args[2]);
			}
			
			if (amount < 0) return false;
			
			economy.withdrawPlayer(owner, amount);
			economy.depositPlayer(refundTo, amount);
			log.info("Swear jar refunded " + amount + " to " + refundTo + ".");

			Player ownerPlayer = getServer().getPlayer(owner);
			if (ownerPlayer instanceof Player && ownerPlayer.isOnline()) {
				sender.sendMessage("$" + amount + " was refunded from your swear jar.");
			}
			
			Player refundPlayer = getServer().getPlayer(refundTo);
			if (refundPlayer instanceof Player && refundPlayer.isOnline()) {
				getServer().getPlayer(refundTo).sendMessage("$" + amount + " was refunded from the swear jar.");
			}

			return true;
		}else{
			return false;
		}
		
	}
	public void reload() {
		blacklist = this.getConfig().getStringList("blacklist");
		whitelist = this.getConfig().getStringList("whitelist");
		regexp = this.getConfig().getStringList("regexp");
		fine = this.getConfig().getDouble("fine");
		damage = this.getConfig().getDouble("damage");
		playerMessage = this.getConfig().getString("message");
		broadcast = this.getConfig().getString("broadcast");
		smite = this.getConfig().getBoolean("smite");
		owner = this.getConfig().getString("owner");
	}
	
	
	
	/*****************************/
	/*  Vault Specific functions */
	/*****************************/
	private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
}
