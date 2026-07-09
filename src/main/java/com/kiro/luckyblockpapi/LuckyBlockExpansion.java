package com.kiro.luckyblockpapi;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LuckyBlockExpansion extends PlaceholderExpansion {

    private final Map<String, int[]> cache = new ConcurrentHashMap<>();
    private File dbFile;
    private int taskId = -1;

    @Override public String getIdentifier() { return "luckyblock"; }
    @Override public String getAuthor() { return "Kiro"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public boolean register() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().warning("[LuckyBlockPAPI] 找不到 SQLite 驱动，扩展未启动");
            return false;
        }

        File pluginsDir = PlaceholderAPIPlugin.getInstance().getDataFolder().getParentFile();
        dbFile = findDb(pluginsDir, 6);
        if (dbFile == null) {
            Bukkit.getLogger().warning("[LuckyBlockPAPI] 在 plugins 文件夹里没找到 luckyblock.db");
        } else {
            Bukkit.getLogger().info("[LuckyBlockPAPI] 使用数据库: " + dbFile.getAbsolutePath());
        }

        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        // 每 15 秒后台异步读一次数据库
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                PlaceholderAPIPlugin.getInstance(), this::reload, 20L, 15L * 20L).getTaskId();

        return super.register();
    }

    private File findDb(File dir, int depth) {
        if (dir == null || depth < 0 || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().equalsIgnoreCase("luckyblock.db")) return f;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File r = findDb(f, depth - 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void reload() {
        if (dbFile == null || !dbFile.exists()) {
            File pluginsDir = PlaceholderAPIPlugin.getInstance().getDataFolder().getParentFile();
            dbFile = findDb(pluginsDir, 6);
            if (dbFile == null) return;
        }
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        Map<String, int[]> fresh = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement pragma = conn.createStatement()) {
                pragma.execute("PRAGMA busy_timeout=3000");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, breaktotal, breakprogress FROM luckyblock");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    if (uuid == null) continue;
                    fresh.put(uuid.toLowerCase(),
                            new int[]{rs.getInt("breaktotal"), rs.getInt("breakprogress")});
                }
            }
            cache.clear();
            cache.putAll(fresh);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[LuckyBlockPAPI] 读取数据库失败: " + e.getMessage());
        }
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        int[] data = cache.get(player.getUniqueId().toString().toLowerCase());
        switch (params.toLowerCase()) {
            case "breaktotal":
                return data == null ? "0" : String.valueOf(data[0]);
            case "breakprogress":
                return data == null ? "0" : String.valueOf(data[1]);
            case "breakremaining":
                return data == null ? "64" : String.valueOf(Math.max(0, 64 - data[1]));
        }
        return null;
    }
}
