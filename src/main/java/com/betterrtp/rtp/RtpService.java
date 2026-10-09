package com.betterrtp.rtp;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.ConfigManager;
import com.betterrtp.config.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RtpService {

    private final BetterRTPPlugin plugin;
    private final SafeLocationFinder locationFinder;

    // يستخدم للنظام القديم
    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();

    // يستخدم للنظام الجديد: البحث أثناء العد التنازلي
    private final Map<UUID, RtpSession> activeSessions = new HashMap<>();

    public RtpService(BetterRTPPlugin plugin) {
        this.plugin = plugin;
        this.locationFinder = new SafeLocationFinder(plugin);
    }

    public void executeRtp(Player player) {
        UUID uuid = player.getUniqueId();

        if (!player.hasPermission("betterrtp.admin") && plugin.getCooldownManager().hasCooldown(uuid)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(uuid);
            player.sendMessage(Messages.get("cooldown").replace("%time%", String.valueOf(remaining)));
            return;
        }

        // إلغاء أي عملية RTP سابقة للاعب نفسه
        cancelPending(uuid);

        ConfigManager config = plugin.getConfigManager();
        int delay = config.getTeleportDelaySeconds();
        boolean bypassDelay = delay <= 0 || player.hasPermission("betterrtp.bypass.delay");

        // إذا كان الوضع الجديد مفعلًا ولا يوجد تجاوز للتأخير، نستخدم البحث أثناء العد
        if (config.isSearchDuringCountdown() && !bypassDelay) {
            startSearchDuringCountdown(player, delay);
        } else {
            // النظام القديم: البحث أولًا ثم العد أو النقل الفوري
            executeLegacyRtp(player, delay, bypassDelay);
        }
    }

    /*
     * النظام القديم:
     * يبحث عن الموقع أولًا، وبعد إيجاد الموقع يبدأ العد التنازلي.
     */
    private void executeLegacyRtp(Player player, int delay, boolean bypassDelay) {
        player.sendMessage(Messages.get("teleporting"));

        locationFinder.findSafeLocation(player.getWorld()).thenAccept(location -> {
            if (!player.isOnline()) {
                return;
            }

            if (location == null) {
                player.sendMessage(Messages.get("no-safe-location"));
                return;
            }

            if (bypassDelay) {
                teleportNow(player, location);
            } else {
                startTeleportCountdown(player, location, delay);
            }
        });
    }

    /*
     * النظام الجديد:
     * يبدأ العد التنازلي مباشرة، ويبدأ البحث عن موقع آمن بسرعة خفيفة.
     */
    private void startSearchDuringCountdown(Player player, int delay) {
        World world = player.getWorld();
        SafeLocationFinder.Bounds bounds = locationFinder.getBounds(world);

        RtpSession session = new RtpSession(
                player,
                world,
                bounds,
                Math.max(1, plugin.getConfigManager().getMaxAttempts())
        );

        activeSessions.put(player.getUniqueId(), session);

        player.sendMessage(Messages.get("teleporting"));

        ConfigManager config = plugin.getConfigManager();

        startSessionSearch(
                session,
                config.getSearchIntervalTicks(),
                config.getAttemptsPerInterval()
        );

        startSessionCountdown(session, delay);
    }

    /*
     * تشغيل مهمة البحث الخاصة بالجلسة.
     * يمكن تشغيلها بسرعة عادية أثناء العد، أو بسرعة أعلى بعد انتهاء العد.
     */
    private void startSessionSearch(RtpSession session, int intervalTicks, int attemptsPerInterval) {
        if (session.searchTask != null) {
            session.searchTask.cancel();
        }

        final int interval = Math.max(1, intervalTicks);
        final int attemptsPerStep = Math.max(1, attemptsPerInterval);

        session.searchTask = new BukkitRunnable() {
            @Override
            public void run() {
                UUID uuid = session.player.getUniqueId();

                if (!activeSessions.containsKey(uuid) || activeSessions.get(uuid) != session) {
                    cancel();
                    return;
                }

                if (!session.player.isOnline()) {
                    cancelPending(uuid);
                    return;
                }

                if (session.foundLocation != null || session.searchFailed) {
                    cancel();
                    return;
                }

                for (int i = 0; i < attemptsPerStep; i++) {
                    if (session.attemptsUsed >= session.maxAttempts) {
                        session.searchFailed = true;
                        break;
                    }

                    session.attemptsUsed++;

                    Location location = locationFinder.attemptSafeLocation(session.world, session.bounds);

                    if (location != null) {
                        session.foundLocation = location;
                        break;
                    }
                }

                // إذا وجدنا موقعًا
                if (session.foundLocation != null) {
                    cancel();

                    // إذا كان العد التنازلي قد انتهى، ننقل اللاعب مباشرة.
                    // أما إذا لم ينته، ننتظر حتى ينتهي العد.
                    if (session.countdownFinished) {
                        completeSessionWithLocation(session);
                    }

                    return;
                }

                // إذا فشلت كل المحاولات
                if (session.searchFailed) {
                    cancel();
                    handleSearchFailure(session);
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    /*
     * العد التنازلي للنظام الجديد.
     */
    private void startSessionCountdown(RtpSession session, int delay) {
        session.countdownTask = new BukkitRunnable() {
            private int count = delay;

            @Override
            public void run() {
                UUID uuid = session.player.getUniqueId();

                if (!activeSessions.containsKey(uuid) || activeSessions.get(uuid) != session) {
                    cancel();
                    return;
                }

                if (!session.player.isOnline()) {
                    cancelPending(uuid);
                    return;
                }

                if (count <= 0) {
                    cancel();

                    session.countdownFinished = true;

                    // إذا كان الموقع قد تم إيجاده قبل نهاية العد
                    if (session.foundLocation != null) {
                        completeSessionWithLocation(session);
                        return;
                    }

                    // إذا كان البحث قد فشل نهائيًا
                    if (session.searchFailed) {
                        handleSearchFailure(session);
                        return;
                    }

                    // إذا انتهى العد ولم نجد موقعًا بعد، نسرّع البحث إن كان الخيار مفعلًا
                    ConfigManager config = plugin.getConfigManager();

                    if (config.isBoostSearchAfterCountdown()) {
                        startSessionSearch(
                                session,
                                config.getBoostIntervalTicks(),
                                config.getBoostAttemptsPerInterval()
                        );
                    }

                    return;
                }

                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /*
     * إنهاء الجلسة ونقل اللاعب بعد إيجاد موقع آمن.
     */
    private void completeSessionWithLocation(RtpSession session) {
        activeSessions.remove(session.player.getUniqueId());

        if (session.countdownTask != null) {
            session.countdownTask.cancel();
        }

        if (session.searchTask != null) {
            session.searchTask.cancel();
        }

        if (session.player.isOnline() && session.foundLocation != null) {
            teleportNow(session.player, session.foundLocation);
        }
    }

    /*
     * إنهاء الجلسة عند الفشل في إيجاد موقع آمن.
     */
    private void handleSearchFailure(RtpSession session) {
        activeSessions.remove(session.player.getUniqueId());

        if (session.countdownTask != null) {
            session.countdownTask.cancel();
        }

        if (session.searchTask != null) {
            session.searchTask.cancel();
        }

        if (session.player.isOnline()) {
            session.player.sendMessage(Messages.get("no-safe-location"));
        }
    }

    private void teleportNow(Player player, Location location) {
        player.teleportAsync(location).thenAccept(success -> {
            if (success && player.isOnline()) {
                plugin.getCooldownManager().setCooldown(
                        player.getUniqueId(),
                        plugin.getConfigManager().getCooldownSeconds()
                );

                String msg = Messages.get("success")
                        .replace("%x%", String.valueOf(location.getBlockX()))
                        .replace("%y%", String.valueOf(location.getBlockY()))
                        .replace("%z%", String.valueOf(location.getBlockZ()))
                        .replace("%world%", location.getWorld().getName());

                player.sendMessage(msg);
            }
        });
    }

    /*
     * العد التنازلي للنظام القديم.
     */
    private void startTeleportCountdown(Player player, Location targetLoc, int delay) {
        cancelPending(player.getUniqueId());

        BukkitTask task = new BukkitRunnable() {
            private int count = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelPending(player.getUniqueId());
                    cancel();
                    return;
                }

                if (count <= 0) {
                    pendingTeleports.remove(player.getUniqueId());
                    teleportNow(player, targetLoc);
                    cancel();
                    return;
                }

                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pendingTeleports.put(player.getUniqueId(), task);
    }

    public void cancelPending(UUID uuid) {
        // إلغاء النظام القديم
        BukkitTask oldTask = pendingTeleports.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        // إلغاء النظام الجديد
        RtpSession session = activeSessions.remove(uuid);
        if (session != null) {
            if (session.countdownTask != null) {
                session.countdownTask.cancel();
            }

            if (session.searchTask != null) {
                session.searchTask.cancel();
            }
        }
    }

    public boolean isPending(UUID uuid) {
        return pendingTeleports.containsKey(uuid) || activeSessions.containsKey(uuid);
    }

    public void cancelAllTasks() {
        pendingTeleports.values().forEach(BukkitTask::cancel);
        pendingTeleports.clear();

        activeSessions.values().forEach(session -> {
            if (session.countdownTask != null) {
                session.countdownTask.cancel();
            }

            if (session.searchTask != null) {
                session.searchTask.cancel();
            }
        });

        activeSessions.clear();
    }

    /*
     * جلسة RTP للنظام الجديد.
     */
    private static final class RtpSession {
        private final Player player;
        private final World world;
        private final SafeLocationFinder.Bounds bounds;
        private final int maxAttempts;

        private BukkitTask countdownTask;
        private BukkitTask searchTask;

        private Location foundLocation;
        private boolean countdownFinished;
        private boolean searchFailed;
        private int attemptsUsed;

        private RtpSession(Player player, World world, SafeLocationFinder.Bounds bounds, int maxAttempts) {
            this.player = player;
            this.world = world;
            this.bounds = bounds;
            this.maxAttempts = maxAttempts;
        }
    }
}