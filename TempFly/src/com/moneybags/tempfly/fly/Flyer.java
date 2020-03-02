package com.moneybags.tempfly.fly;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.moneybags.tempfly.TempFly;
import com.moneybags.tempfly.aesthetic.ActionBarAPI;
import com.moneybags.tempfly.aesthetic.TitleAPI;
import com.moneybags.tempfly.hook.WorldGuardAPI;
import com.moneybags.tempfly.time.RelativeTimeRegion;
import com.moneybags.tempfly.time.TimeHandle;
import com.moneybags.tempfly.util.U;
import com.moneybags.tempfly.util.V;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class Flyer {
	
	private Player p;
	private double time;
	private double start;
	private int idle = 0;
	private BukkitTask timer;
	private String listName;
	private String tagName;
	private List<RelativeTimeRegion> rtEncompassing = new ArrayList<>();
	
	public Flyer(Player p) {
		this.p = p;
		this.time = TimeHandle.getTime(p.getUniqueId());
		this.start = time;
		this.listName = p.getPlayerListName();
		this.tagName = p.getDisplayName();
		p.setAllowFlight(true);
		if (!p.isOnGround()) {
			p.setFlying(true);	
		} else {
			p.setFlying(false);
		}
		
		asessRtRegions();
		asessRtWorlds();
		this.timer = new Timer().runTaskTimer(TempFly.plugin, 0, 20);
	}
	
	public boolean isFlying() {
		return p.isFlying();
	}
	
	public boolean isIdle() {
		return idle >= V.idleThreshold;
	}
	
	public double getTime() {
		return time;
	}
	
	public void setTime(double time) {
		this.time = time;
		this.start = time;
	}
	
	public void resetIdleTimer() {
		this.idle = 0;
	}
	
	public List<RelativeTimeRegion> getRtEncompassing() {
		return rtEncompassing;
	}
	
	public void asessRtWorlds() {
		for (RelativeTimeRegion rt : FlyHandle.getRtRegions()) {
			String world = p.getWorld().getName();
			if (rt.isWorld()) {
				String rtName = rt.getName();
				if ((rtName.equals(world)) && !(rtEncompassing.contains(rt))) {
					rtEncompassing.add(rt);
				} else if (!(rtName.equals(world)) && (rtEncompassing.contains(rt))) {
					rtEncompassing.remove(rt);
				}
			}
		}
	}
	
	public void asessRtRegions() {
		List<String> regions = new ArrayList<>();
		if (WorldGuardAPI.isEnabled()) {
			ApplicableRegionSet prot = WorldGuardAPI.getRegionSet(p.getLocation());
			if (prot != null) {
				for(ProtectedRegion r : prot) {
					regions.add(r.getId());
				}	
			}
		}	
		for (RelativeTimeRegion rt : FlyHandle.getRtRegions()) {
			String rtName = rt.getName();
			if (rt.isWorld()) {
				continue;
			}
			if ((regions.contains(rtName)) && !(rtEncompassing.contains(rt))) {
				rtEncompassing.add(rt);
			} else if (!(regions.contains(rtName)) && (rtEncompassing.contains(rt))) {
			 rtEncompassing.remove(rt);	
			}
		}
	}
	
	public void removeFlyer() {
		timer.cancel();
		GameMode m = p.getGameMode();
		updateList(true);
		updateName(true);
		if ((m.equals(GameMode.CREATIVE)) || (m.equals(GameMode.SPECTATOR))) {
			return;
		}
		p.setFlying(false);
		p.setAllowFlight(false);
	}
	
	public Player getPlayer() {
		return p;
	}
	
	public void playEffect() {
		if (V.hideVanish) {
			for (MetadataValue meta : p.getMetadata("vanished")) {
				if (meta.asBoolean()) {
					return;
				}
			}
		}
		if (!TempFly.oldParticles()) {
			Particle particle = null;
			try {
				particle = Particle.valueOf(V.particleType.toUpperCase());
			} catch (Exception e) {
				U.logW("A particle effect listed in the config does not exist, please ensure you are using the correct particle for your server version.: (" + V.particleType + ")");
				particle = Particle.VILLAGER_HAPPY;
			}
			p.getWorld().spawnParticle(particle, p.getLocation(), 1, 0.1, 0.1, 0.1);	
		} else {
			Effect particle = null;
			try {
				particle = Effect.valueOf(V.particleType.toUpperCase());
			} catch (Exception e) {
				//U.logW("A particle effect listed in the config does not exist: (" + V.particleType + ")");
				particle = Effect.valueOf("HAPPY_VILLAGER");
			}
			p.getWorld().playEffect(p.getLocation(), particle, 2);
		}
	}
	
	private void updateList(boolean kill) {
		if (!V.list) {
			return;
		}
		if (!isFlying() || kill) {
			p.setPlayerListName(listName);
		} else {
			p.setPlayerListName(V.listName
					.replaceAll("\\{PLAYER}", p.getName())
					.replaceAll("\\{OLD_TAG}", listName));
		}
	}
	
	private void updateName(boolean kill) {
		if (!V.tag) {
			return;
		}
		if (!isFlying() || kill) {
			p.setDisplayName(tagName);
		} else {
			p.setDisplayName(V.tagName
					.replaceAll("\\{PLAYER}", p.getName())
					.replaceAll("\\{OLD_TAG}", tagName));
		}
	}
	
	public class Timer extends BukkitRunnable {

		@Override
		public void run() {
			p.setAllowFlight(true);
			if (p.hasPermission("tempfly.time.infinite")) {
				return;
			}
			idle++;
			updateList(false);
			updateName(false);
			if (isIdle()) {
				if (V.idleDrop) {
					FlyHandle.removeFlyer(p);
				}
				if (!V.idleTimer) {
					return;
				}
			}
			if (!(isFlying()) && (!V.groundTimer)) {
				return;
			}
			if (time > 0) {
				double cost = 1;
				for (RelativeTimeRegion rtr : rtEncompassing) {
					cost = cost*rtr.getFactor();
				}
				
				time = time-cost;
				if (time <= 0) {
					if (!V.protTime) {
						FlyHandle.addDamageProtection(p);	
					}
					FlyHandle.removeFlyer(p);
					U.m(p, V.invalidTimeSelf);
				}
				
				if (V.warningTimes.contains((long)time)) {
					String title = TimeHandle.regexString(V.warningTitle, time);
					String subtitle = TimeHandle.regexString(V.warningSubtitle, time);
					TitleAPI.sendTitle(p, 15, 30, 15, title, subtitle);
				}
				if (V.actionBar) {
					if (V.actionProgress) {
						double percent = (((float)time/start)*100);
						String bar = "";
						bar = bar.concat("&8[&a");
						boolean neg = true;
						for (double i = 0; i < 100; i += 7.69) {
							if ((percent <= i) && (neg)) {
								bar = bar.concat("&c");
								neg = false;
							}
							bar = bar.concat("=");
						}
						bar = bar.concat("&8]");
						ActionBarAPI.sendActionBar(p, U.cc(bar));
					} else {
						ActionBarAPI.sendActionBar(p, TimeHandle.regexString(V.actionText, getTime()));
					}
				}
			} else {
				if (!V.protTime) {
					FlyHandle.addDamageProtection(p);	
				}
				FlyHandle.removeFlyer(p);
				U.m(p, V.invalidTimeSelf);
			}
		}
	}
}
