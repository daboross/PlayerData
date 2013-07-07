/*
 * Author: Dabo Ross
 * Website: www.daboross.net
 * Email: daboross@daboross.net
 */
package net.daboross.bukkitdev.playerdata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import net.daboross.bukkitdev.playerdata.api.PlayerData;
import net.daboross.bukkitdev.playerdata.api.PlayerHandler;
import net.daboross.bukkitdev.playerdata.helpers.comparators.PlayerDataFirstJoinComparator;
import net.daboross.bukkitdev.playerdata.helpers.comparators.PlayerDataLastSeenComparator;
import net.daboross.bukkitdev.playerdata.libraries.dargumentchecker.ArgumentCheck;
import net.daboross.bukkitdev.playerdata.libraries.dxml.DXMLException;
import net.daboross.bukkitdev.playerdata.parsers.xml.XMLParserFinder;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 *
 * @author daboross
 */
public class PlayerHandlerImpl implements PlayerHandler {

    /**
     * List lock for the PlayerData lists. synchronize on this object when:
     * <br>1. Reading the list from another thread (not the server thread)
     * <br>2. Changing the list from the server thread (you shouldn't ever
     * change the list from outside of the server thread)
     */
    private final Object LIST_LOCK = new Object();
    private final List<PlayerDataImpl> playerDataList = new ArrayList<PlayerDataImpl>();
    private final List<PlayerDataImpl> playerDataListFirstJoin = new ArrayList<PlayerDataImpl>();
    private final PlayerDataBukkit playerDataBukkit;
    private final File dataFolder;

    /**
     * Use this to create a new PlayerHandlerImpl when PlayerDataBukkit is
     * loaded. There should only be one PlayerHandlerImpl instance.
     */
    PlayerHandlerImpl(PlayerDataBukkit playerDataMain) {
        this.playerDataBukkit = playerDataMain;
        File pluginFolder = playerDataMain.getDataFolder();
        dataFolder = new File(pluginFolder, "xml");
        if (!dataFolder.isDirectory()) {
            dataFolder.mkdirs();
        }
    }

    private PlayerDataImpl login(Player p, boolean startingServer) {
        if (p == null) {
            throw new IllegalArgumentException("Null Argument");
        }
        synchronized (LIST_LOCK) {
            for (int i = 0; i < playerDataList.size(); i++) {
                PlayerDataImpl pData = playerDataList.get(i);
                if (pData.getUsername().equals(p.getName())) {
                    pData.loggedIn(p, this, startingServer);
                    return pData;
                }
            }
        }
        PlayerDataImpl pd = new PlayerDataImpl(p);
        pd.loggedIn(p, this, startingServer);
        synchronized (LIST_LOCK) {
            if (!playerDataListFirstJoin.contains(pd)) {
                playerDataListFirstJoin.add(pd);
            }
            while (playerDataList.contains(pd)) {
                playerDataList.remove(pd);
            }
            playerDataList.add(0, pd);
            return pd;
        }
    }

    private PlayerData logout(Player p, boolean endingServer) {
        PlayerDataImpl pd = getPlayerData(p);
        pd.loggedOut(p, this, endingServer);
        if (!endingServer) {
            synchronized (LIST_LOCK) {
                int pos = playerDataList.indexOf(pd);
                while (playerDataList.contains(pd)) {
                    playerDataList.remove(pd);
                }
                while (pos < playerDataList.size() && playerDataList.get(pos).isOnline()) {
                    pos++;
                }
                playerDataList.add(pos, pd);
            }
        }
        return pd;
    }

    void savePData(PlayerData pd) {
        if (pd == null) {
            return;
        }
        File file = new File(dataFolder, pd.getUsername() + ".xml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ex) {
                playerDataBukkit.getLogger().log(Level.SEVERE, "Exception creating new file " + file.getAbsolutePath(), ex);
                return;
            }
        }
        if (file.canWrite()) {
            try {
                XMLParserFinder.save(pd, file);
            } catch (DXMLException ex) {
                playerDataBukkit.getLogger().log(Level.SEVERE, "Exception saving data to file " + file.getAbsolutePath(), ex);
            }
        } else {
            playerDataBukkit.getLogger().log(Level.SEVERE, "Can\'t write to file {0}", file.getAbsolutePath());
        }
    }

    void endServer() {
        Player[] ls = Bukkit.getOnlinePlayers();
        for (Player p : ls) {
            this.logout(p, true);
        }
    }

    PlayerData login(Player p) {
        return login(p, false);
    }

    PlayerData logout(Player p) {
        return logout(p, false);
    }

    /**
     * This function reads all PlayerData files and loads them into the
     * database. Also logs in all logged in players, and creates empty files
     * from Bukkit if there are no files.
     *
     * @return True if load was successful. False if load failed. If load failed
     * then it is expected that this PlayerHandler won't ever be used again.
     */
    boolean init() {
        synchronized (LIST_LOCK) {
            playerDataList.clear();
            playerDataListFirstJoin.clear();
            File[] playerFiles = dataFolder.listFiles();
            if (playerFiles.length == 0) {
                OfflinePlayer[] players = Bukkit.getServer().getOfflinePlayers();
                for (OfflinePlayer p : players) {
                    PlayerDataImpl pd = new PlayerDataImpl(p);
                    if (!playerDataList.contains(pd)) {
                        playerDataList.add(pd);
                    }
                    if (!playerDataListFirstJoin.contains(pd)) {
                        playerDataListFirstJoin.add(pd);
                    }
                }
                saveAllData();
            } else {
                for (File fl : playerFiles) {
                    if (!fl.isFile()) {
                        playerDataBukkit.getLogger().log(Level.SEVERE, "There is a non-file in xml directory: {0}", fl.getAbsolutePath());
                        playerDataBukkit.getLogger().log(Level.SEVERE, "PlayerData won\'t load until you fix this!");
                        return false;
                    } else if (fl.canRead()) {
                        String[] split = fl.getName().split("\\.");
                        String type = split[split.length - 1];
                        if (type.equals("xml")) {
                            PlayerDataImpl pData;
                            try {
                                pData = XMLParserFinder.read(fl);
                            } catch (DXMLException dxmle) {
                                playerDataBukkit.getLogger().log(Level.SEVERE, "Error Parsing File: {0}", dxmle.getMessage());
                                playerDataBukkit.getLogger().log(Level.SEVERE, "PlayerData won\'t load until you fix this!");
                                return false;
                            }
                            if (!playerDataList.contains(pData)) {
                                playerDataList.add(pData);
                            }
                            if (!playerDataListFirstJoin.contains(pData)) {
                                playerDataListFirstJoin.add(pData);
                            }
                        } else {
                            playerDataBukkit.getLogger().log(Level.SEVERE, "There is a file with an unknown type '" + type + "' in the xml directory! File: {0}", fl.getAbsolutePath());
                            playerDataBukkit.getLogger().log(Level.SEVERE, "PlayerData won\'t load until you fix this!");
                            return false;
                        }
                    } else {
                        playerDataBukkit.getLogger().log(Level.SEVERE, "Can't read file in xml directory! File: {0}", fl.getAbsolutePath());
                        playerDataBukkit.getLogger().log(Level.SEVERE, "PlayerData won\'t load until you fix this!");
                        return false;
                    }
                }
                playerDataBukkit.getLogger().log(Level.INFO, "Loaded {0} data files", playerDataList.size());
            }
            Collections.sort(playerDataList, new PlayerDataLastSeenComparator());
            Collections.sort(playerDataListFirstJoin, new PlayerDataFirstJoinComparator());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.login(p, true);
        }
        return true;
    }

    @Override
    public PlayerDataBukkit getPlayerDataBukkit() {
        return playerDataBukkit;
    }

    @Override
    public String getFullUsername(String partialName) {
        ArgumentCheck.notNull(partialName);
        PlayerData playerData = getPlayerDataPartial(partialName);
        return playerData == null ? null : playerData.getUsername();
    }

    @Override
    public PlayerDataImpl getPlayerData(String name) {
        if (name == null) {
            return null;
        }
        synchronized (LIST_LOCK) {
            for (int i = 0; i < playerDataList.size(); i++) {
                if (playerDataList.get(i).getUsername().equalsIgnoreCase(name)) {
                    return playerDataList.get(i);
                }
            }
        }
        return null;
    }

    @Override
    public PlayerData getPlayerDataPartial(String partialName) {
        ArgumentCheck.notNull(partialName);
        PlayerData[] possibleMatches = new PlayerData[2];
        synchronized (LIST_LOCK) {
            for (int i = 0; i < playerDataList.size(); i++) {
                PlayerData pd = playerDataList.get(i);
                if (pd.getUsername().equalsIgnoreCase(partialName)) {
                    return pd;
                }
                if (StringUtils.containsIgnoreCase(pd.getUsername(), partialName)) {
                    if (possibleMatches[0] == null) {
                        possibleMatches[0] = pd;
                    }
                }
                if (StringUtils.containsIgnoreCase(ChatColor.stripColor(pd.getDisplayname()), partialName)) {
                    if (possibleMatches[1] == null) {
                        possibleMatches[1] = pd;
                    }
                }
            }
        }
        for (int i = 0; i < possibleMatches.length; i++) {
            if (possibleMatches[i] != null) {
                return possibleMatches[i];
            }
        }
        return null;
    }

    @Override
    public PlayerDataImpl getPlayerData(Player player) {
        if (player == null) {
            return null;
        }
        synchronized (LIST_LOCK) {
            for (int i = 0; i < playerDataList.size(); i++) {
                PlayerDataImpl pData = playerDataList.get(i);
                if (pData.getUsername().equalsIgnoreCase(player.getName())) {
                    return pData;
                }
            }
            PlayerDataImpl pData = new PlayerDataImpl(player);
            if (!playerDataList.contains(pData)) {
                playerDataList.add(pData);
            }
            if (!playerDataListFirstJoin.contains(pData)) {
                playerDataListFirstJoin.add(pData);
            }
            return pData;
        }
    }

    @Override
    public List<PlayerData> getAllPlayerDatasWithExtraData(String dataName) {
        if (dataName == null) {
            return null;
        }
        synchronized (LIST_LOCK) {
            List<PlayerData> returnArrayList = new ArrayList<PlayerData>();
            for (PlayerDataImpl pData : playerDataList) {
                if (pData.hasExtraData(dataName)) {
                    returnArrayList.add(pData);
                }
            }
            return returnArrayList;
        }
    }

    /**
     * This function gets all PDatas loaded, which should be one for each Player
     * who has ever joined the server. This returns an unmodifiable list.
     *
     * @return A copy of the list of PDatas that PlayerHandlerImpl keeps.
     */
    @Override
    public List<PlayerDataImpl> getAllPlayerDatas() {
        synchronized (LIST_LOCK) {
            return Collections.unmodifiableList(playerDataList);
        }
    }

    @Override
    public List<PlayerDataImpl> getAllPlayerDatasFirstJoin() {
        synchronized (LIST_LOCK) {
            return Collections.unmodifiableList(playerDataListFirstJoin);
        }
    }

    @Override
    public void saveAllData() {
        synchronized (LIST_LOCK) {
            for (PlayerDataImpl pd : playerDataList) {
                pd.updateStatus();
                savePData(pd);
            }
        }
    }
}