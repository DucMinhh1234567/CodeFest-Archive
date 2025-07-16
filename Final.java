import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.npcs.Ally;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "164996"; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "FullBot"; // Tên bot
    private static final String SECRET_KEY = "sk-H__B6olPTeelwSs3R46pmQ:fHUi994TZvUp1hE6v4J-6tL3oRiTyKirLStD1yjP2jW717K3lk3qNujJIFwEGK8rguQS5sCVPFykXuHtdmD3Tg"; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new FullBotListener(hero);
        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
        System.out.println("FullBot đã khởi động!");
    }
}

class FullBotListener implements Emitter.Listener {
    private final Hero hero;
    private Player currentTargetEnemy = null;
    private Obstacle currentTargetChest = null;
    private final CooldownManager cdMgr = new CooldownManager();

    public FullBotListener(Hero hero) {
        this.hero = hero;
    }

    @Override
    public void call(Object... args) {
        try {
            if (args == null || args.length == 0) return;
            GameMap gameMap = hero.getGameMap();
            gameMap.updateOnUpdateMap(args[0]);
            Player player = gameMap.getCurrentPlayer();
            if (player == null || player.getHealth() == 0) {
                System.out.println("Player đã chết hoặc dữ liệu không khả dụng.");
                return;
            }

            if (moveToSpiritIfNeeded(gameMap, player)) {
                return; // Nếu đang di chuyển tới tinh linh → không làm gì thêm
            }

            // === SMART SUPPORT ITEM USAGE ===
            smartUseSupportItems(gameMap, player);

            // === CHEST BREAKING ===
            handleChestBreaking(gameMap, player, getNodesToAvoid(gameMap));

            // === ITEM COLLECTING & SWAPPING ===
            handleItemCollectAndSwap(gameMap, player, getNodesToAvoid(gameMap));

            // === SẴN SÀNG ĐÁNH NHAU NẾU CẦN ===
            int weaponCount = 0;
            if (hero.getInventory().getGun() != null) weaponCount++;
            Weapon meleeWeapon = hero.getInventory().getMelee();
            if (meleeWeapon != null && !"HAND".equals(meleeWeapon.getId())) weaponCount++;

            // === KIỂM TRA ĐIỀU KIỆN CHIẾN ĐẤU ===
            // Kiểm tra xem có rương hoặc item có thể xử lý không
            // Nếu hết rương và hết item có thể xử lý, chỉ đánh địch trong phạm vi 3x3
            if (weaponCount == 1 && !hasEnoughWeapons()) {
                System.out.println("[COMBAT MODE] Hết rương và item, chỉ đánh địch trong phạm vi 3x3!");

                // Tìm kẻ địch trong phạm vi 3x3
                Player nearbyEnemy = null;
                double minDist = Double.MAX_VALUE;
                for (Player enemy : gameMap.getOtherPlayerInfo()) {
                    if (enemy.getHealth() <= 0) continue;
                    int dx = Math.abs(enemy.getX() - player.getX());
                    int dy = Math.abs(enemy.getY() - player.getY());
                    if (dx <= 2 && dy <= 2) { // Phạm vi 3x3
                        double dist = PathUtils.distance(player, enemy);
                        if (dist < minDist) {
                            minDist = dist;
                            nearbyEnemy = enemy;
                        }
                    }
                }

                if (nearbyEnemy != null) {
                    // Có địch trong phạm vi 3x3, đánh ngay
                    System.out.println("[COMBAT MODE] Có địch trong phạm vi 3x3, tấn công!");
                    int step = gameMap.getStepNumber();             // bạn đã có gameMap
                    cdMgr.syncInventory(hero.getInventory(), step); // cập nhật kho

                    // Xác định địch gần nhất (đã có hàm getNearestEnemy / findNearestEnemy)
                    Player target = getNearestEnemy(gameMap, player);       // dùng hàm của bạn
                    boolean enemyClose = false;
                    if (target != null) {
                        int dist = manhattan(player.getX(), player.getY(),
                                target.getX(), target.getY());
                        enemyClose = dist <= 2;  // cận chiến?
                    }

                    Weapon best = cdMgr.chooseBest(step, enemyClose);

                    if (best != null && target != null) {           // Có vũ khí sẵn sàng
                        String dir = getEnemyAttackDirection(player, target); // hàm cũ của bạn
                        if (dir != null) {
                            switch (best.getType()) {
                                case GUN      -> hero.shoot(dir);
                                case MELEE    -> hero.attack(dir);
                                case SPECIAL  -> hero.useSpecial(dir);
                            }
                            cdMgr.markUsed(best, step);             // quan trọng!
                            return;                                 // kết thúc tick
                        }
                    }

                    boolean didThrow = handleThrowableAttack(gameMap, player);
                    if (didThrow) return; // Ném được thì dừng tại đây
                    handleEnemyHuntingAll(gameMap, player, getNodesToAvoid(gameMap));
                    return;
                } else {
                    // Không có địch trong phạm vi 3x3, đứng yên
                    System.out.println("[COMBAT MODE] Không có địch trong phạm vi 3x3, đứng yên chờ địch!");
                    handleItemCollectAndSwap(gameMap, player, getNodesToAvoid(gameMap));
                    return;
                }
            }

            if (hero.getInventory().getThrowable() != null) weaponCount++;
            if (hero.getInventory().getSpecial() != null) weaponCount++;

            if (weaponCount >= 2 && !hasEnoughWeapons()) {
                // Tìm kẻ địch trong phạm vi 13x13 (bán kính 6)
                Player nearestEnemy = null;
                double minDist = Double.MAX_VALUE;
                for (Player enemy : gameMap.getOtherPlayerInfo()) {
                    if (enemy.getHealth() <= 0) continue;
                    int dx = Math.abs(enemy.getX() - player.getX());
                    int dy = Math.abs(enemy.getY() - player.getY());
                    if (dx <= 6 && dy <= 6) {
                        double dist = PathUtils.distance(player, enemy);
                        if (dist < minDist) {
                            minDist = dist;
                            nearestEnemy = enemy;
                        }
                    }
                }
                if (nearestEnemy != null) {
                    // Tiếp cận và đánh nhau với kẻ địch gần nhất trong phạm vi
                    System.out.println("[READY COMBAT] Có kẻ địch trong phạm vi 13x13, tiếp cận và giao chiến!");
                    int step = gameMap.getStepNumber();             // bạn đã có gameMap
                    cdMgr.syncInventory(hero.getInventory(), step); // cập nhật kho

                    // Xác định địch gần nhất (đã có hàm getNearestEnemy / findNearestEnemy)
                    Player target = getNearestEnemy(gameMap, player);       // dùng hàm của bạn
                    boolean enemyClose = false;
                    if (target != null) {
                        int dist = manhattan(player.getX(), player.getY(),
                                target.getX(), target.getY());
                        enemyClose = dist <= 2;  // cận chiến?
                    }

                    Weapon best = cdMgr.chooseBest(step, enemyClose);

                    if (best != null && target != null) {           // Có vũ khí sẵn sàng
                        String dir = getEnemyAttackDirection(player, target); // hàm cũ của bạn
                        if (dir != null) {
                            switch (best.getType()) {
                                case GUN      -> hero.shoot(dir);
                                case MELEE    -> hero.attack(dir);
                                case SPECIAL  -> hero.useSpecial(dir);
                            }
                            cdMgr.markUsed(best, step);             // quan trọng!
                            return;                                 // kết thúc tick
                        }
                    }

                    boolean didThrow = handleThrowableAttack(gameMap, player);
                    if (didThrow) return; // Ném được thì dừng tại đây
                    handleEnemyHuntingAll(gameMap, player, getNodesToAvoid(gameMap));
                    return;
                } else {
                    // Không có địch gần, tiếp tục nhặt đồ cho tới khi đủ vũ khí
                    System.out.println("[READY COMBAT] Chưa có địch gần, tiếp tục ưu tiên nhặt đồ!");
                    handleItemCollectAndSwap(gameMap, player, getNodesToAvoid(gameMap));
                    return;
                }
            }

            // === ENEMY HUNTING (GUN, MELEE, SPECIAL) ===
            // Chỉ đánh nhau khi có đủ từ 3 loại vũ khí trở lên
            if (hasEnoughWeapons()) {
                int step = gameMap.getStepNumber();             // bạn đã có gameMap
                cdMgr.syncInventory(hero.getInventory(), step); // cập nhật kho

                // Xác định địch gần nhất (đã có hàm getNearestEnemy / findNearestEnemy)
                Player target = getNearestEnemy(gameMap, player);       // dùng hàm của bạn
                boolean enemyClose = false;
                if (target != null) {
                    int dist = manhattan(player.getX(), player.getY(),
                            target.getX(), target.getY());
                    enemyClose = dist <= 2;  // cận chiến?
                }

                Weapon best = cdMgr.chooseBest(step, enemyClose);

                if (best != null && target != null) {           // Có vũ khí sẵn sàng
                    String dir = getEnemyAttackDirection(player, target); // hàm cũ của bạn
                    if (dir != null) {
                        switch (best.getType()) {
                            case GUN      -> hero.shoot(dir);
                            case MELEE    -> hero.attack(dir);
                            case SPECIAL  -> hero.useSpecial(dir);
                        }
                        cdMgr.markUsed(best, step);             // quan trọng!
                        return;                                 // kết thúc tick
                    }
                }

                boolean didThrow = handleThrowableAttack(gameMap, player);
                if (didThrow) return; // Ném được thì dừng tại đây
                handleEnemyHuntingAll(gameMap, player, getNodesToAvoid(gameMap));
            } else {
                System.out.println("[COMBAT] Chưa đủ vũ khí để đánh nhau. Cần ít nhất 3 loại vũ khí.");
            }

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Player getNearestEnemy(GameMap gameMap, Player player) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player enemy : gameMap.getOtherPlayerInfo()) {
            if (enemy.getHealth() <= 0) continue;
            double dist = PathUtils.distance(player, enemy);
            if (dist < minDist) {
                minDist = dist;
                nearest = enemy;
            }
        }
        return nearest;
    }

    // ================== WEAPON CHECK ==================
    private boolean hasEnoughWeapons() {
        int weaponCount = 0;

        // Kiểm tra Gun
        if (hero.getInventory().getGun() != null) {
            weaponCount++;
        }

        // Kiểm tra Melee (không tính HAND)
        Weapon meleeWeapon = hero.getInventory().getMelee();
        if (meleeWeapon != null && !"HAND".equals(meleeWeapon.getId())) {
            weaponCount++;
        }

        // Kiểm tra Throwable
        if (hero.getInventory().getThrowable() != null) {
            weaponCount++;
        }

        // Kiểm tra Special
        if (hero.getInventory().getSpecial() != null) {
            weaponCount++;
        }

        System.out.println("[WEAPON CHECK] Số lượng vũ khí hiện tại: " + weaponCount + "/4");
        return weaponCount >= 3;
    }

    // ================== SMART SUPPORT ITEM USAGE ==================
    private void smartUseSupportItems(GameMap gameMap, Player player) throws IOException {
        List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
        float currentHp = player.getHealth();
        int maxHp = 100;

        // Kiểm tra xem có cần dùng item healing thấp nhất để nhặt item tốt hơn không
        if (supportItems.size() >= 4) {
            SupportItem worstHealingItem = findWorstHealingItem(supportItems);
            if (worstHealingItem != null) {
                Element betterSupportItem = findBetterSupportItemNearby(gameMap, player);
                if (betterSupportItem != null) {
                    System.out.println("[SUPPORT UPGRADE] Dùng item healing thấp nhất để nhặt item tốt hơn: " + worstHealingItem.getId());
                    hero.useItem(worstHealingItem.getId());
                    return;
                }
            }
        }

        // 1. Heal items
        String[] hpItemIds = {"GOD_LEAF", "SPIRIT_TEAR", "MERMAID_TAIL", "PHOENIX_FEATHERS", "UNICORN_BLOOD"};
        int[] hpValues = {10, 15, 20, 40, 80};
        int minAbs = Integer.MAX_VALUE, bestIdx = -1;
        for (int i = 0; i < supportItems.size(); i++) {
            Element item = supportItems.get(i);
            String id = item.getId();
            for (int j = 0; j < hpItemIds.length; j++) {
                if (id.equals(hpItemIds[j])) {
                    int abs = (int)(currentHp + hpValues[j] - maxHp);
                    if (abs >= 50) continue;
                    abs = Math.abs(abs);
                    if (abs < minAbs && currentHp <= 70) {
                        minAbs = abs;
                        bestIdx = i;
                    }
                }
            }
        }
        if (bestIdx != -1) {
            System.out.println("[SUPPORT] Sử dụng item hồi máu: " + supportItems.get(bestIdx).getId());
            hero.useItem(supportItems.get(bestIdx).getId());
            return;
        }
        // 2. Compass
        for (Element item : supportItems) {
            if (item.getId().equals("COMPASS")) {
                List<Player> enemies = gameMap.getOtherPlayerInfo();
                for (Player enemy : enemies) {
                    if (enemy.getHealth() > 0) {
                        int dx = Math.abs(enemy.getX() - player.getX());
                        int dy = Math.abs(enemy.getY() - player.getY());
                        if (dx <= 3 && dy <= 3) {
                            System.out.println("[SUPPORT] Sử dụng La bàn khi có địch gần!");
                            hero.useItem("COMPASS");
                            return;
                        }
                    }
                }
            }
        }
        // 3. Elixir
        for (Element item : supportItems) {
            if (item.getId().equals("ELIXIR")) {
                if (hero.getEffects() != null && !hero.getEffects().isEmpty()) {
                    System.out.println("[SUPPORT] Sử dụng Thần dược để giải hiệu ứng!");
                    hero.useItem("ELIXIR");
                    return;
                }
            }
        }
        // 4. Magic stick
        for (Element item : supportItems) {
            if (item.getId().equals("MAGIC")) {
                List<Player> enemies = gameMap.getOtherPlayerInfo();
                int closeEnemies = 0;
                for (Player enemy : enemies) {
                    if (enemy.getHealth() > 0) {
                        int dx = Math.abs(enemy.getX() - player.getX());
                        int dy = Math.abs(enemy.getY() - player.getY());
                        if (dx <= 2 && dy <= 2) closeEnemies++;
                    }
                }
                if (closeEnemies >= 2 || currentHp <= maxHp * 0.3) {
                    System.out.println("[SUPPORT] Sử dụng Gậy thần để tàng hình!");
                    hero.useItem("MAGIC");
                    return;
                }
            }
        }
    }

    // Tìm item healing có giá trị thấp nhất (ngoài COMPASS, MAGIC, ELIXIR)
    private SupportItem findWorstHealingItem(List<SupportItem> supportItems) {
        SupportItem worstItem = null;
        int minHealing = Integer.MAX_VALUE;

        for (SupportItem item : supportItems) {
            String id = item.getId();
            // Bỏ qua COMPASS, MAGIC, ELIXIR
            if ("COMPASS".equals(id) || "MAGIC".equals(id) || "ELIXIR".equals(id)) {
                continue;
            }

            int healing = item.getHealingHP();
            if (healing < minHealing) {
                minHealing = healing;
                worstItem = item;
            }
        }

        return worstItem;
    }

    // Tìm item support tốt hơn ở gần
    private Element findBetterSupportItemNearby(GameMap gameMap, Player player) {
        List<SupportItem> currentSupportItems = hero.getInventory().getListSupportItem();
        List<SupportItem> nearbySupportItems = gameMap.getListSupportItems();

        for (SupportItem nearbyItem : nearbySupportItems) {
            // Kiểm tra khoảng cách (trong phạm vi 5 ô)
            int dx = Math.abs(nearbyItem.getX() - player.getX());
            int dy = Math.abs(nearbyItem.getY() - player.getY());
            if (dx > 5 || dy > 5) continue;

            // Kiểm tra xem item này có tốt hơn item thấp nhất hiện tại không
            SupportItem worstCurrent = findWorstHealingItem(currentSupportItems);
            if (worstCurrent != null && nearbyItem.getHealingHP() > worstCurrent.getHealingHP()) {
                return nearbyItem;
            }
        }

        return null;
    }

    //hàm chạy tới tinh linh=========================================
    private boolean moveToSpiritIfNeeded(GameMap gameMap, Player player) throws IOException {
        List<Ally> allies = gameMap.getListAllies();

        // Lọc ra các tinh linh có id SPIRIT và nằm trong bo
        List<Ally> spiritsInSafeZone = allies.stream()
                .filter(a -> a.getId().equals("SPIRIT"))
                .filter(a -> PathUtils.checkInsideSafeArea(a, gameMap.getSafeZone(), gameMap.getMapSize()))
                .toList();

        if (spiritsInSafeZone.isEmpty()) {
            System.out.println("[SPIRIT] Không có tinh linh nào trong bo.");
            return false;
        }

        // Tìm tinh linh gần nhất
        Node playerNode = new Node(player.getX(), player.getY());
        Ally nearestSpirit = null;
        int minDistance = Integer.MAX_VALUE;

        for (Ally spirit : spiritsInSafeZone) {
            Node spiritNode = new Node(spirit.getX(), spirit.getY());
            int dist = PathUtils.distance(playerNode, spiritNode);
            if (dist < minDistance) {
                minDistance = dist;
                nearestSpirit = spirit;
            }
        }

        // Nếu cần hồi máu và có tinh linh
        if (nearestSpirit != null && player.getHealth() <= 40) {
            int spiritDistance = PathUtils.distance(playerNode, nearestSpirit);
            if (spiritDistance <= 10) {
                if (isInSpiritHealingRange(player, nearestSpirit)) {
                    System.out.println("[SPIRIT] Đã ở trong vùng hồi máu 3x3, đứng yên...");
                    return true;
                }

                // Nếu chưa ở vùng hồi, tìm ô gần nhất trong vùng 3x3 quanh tinh linh để di chuyển đến
                Node playerPos = new Node(player.getX(), player.getY());
                List<Node> avoid = getNodesToAvoid(gameMap);
                List<Node> healingArea = getHealingAreaAroundSpirit(nearestSpirit, gameMap);

                // Tìm ô gần nhất trong vùng hồi mà có đường đi
                String bestPath = null;
                int minStep = Integer.MAX_VALUE;

                for (Node target : healingArea) {
                    String path = PathUtils.getShortestPath(gameMap, avoid, playerPos, target, true);
                    if (path != null && path.length() < minStep) {
                        minStep = path.length();
                        bestPath = path;
                    }
                }

                if (bestPath != null) {
                    System.out.println("[SPIRIT] Di chuyển vào vùng hồi quanh tinh linh: " + bestPath);
                    hero.move(bestPath);
                } else {
                    System.out.println("[SPIRIT] Không tìm được đường vào vùng hồi quanh tinh linh.");
                }

                return true;
            }
        }

        return false;
    }

    // Kiểm tra xem player có đứng trong vùng hồi máu quanh tinh linh không (ô 3x3)
    private boolean isInSpiritHealingRange(Player player, Ally spirit) {
        int dx = Math.abs(player.getX() - spirit.getX());
        int dy = Math.abs(player.getY() - spirit.getY());
        return dx <= 1 && dy <= 1; // player nằm trong vùng 3x3 quanh tinh linh
    }

    private List<Node> getHealingAreaAroundSpirit(Ally spirit, GameMap gameMap) {
        List<Node> area = new ArrayList<>();
        int x = spirit.getX();
        int y = spirit.getY();
        int mapSize = gameMap.getMapSize();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < mapSize && ny < mapSize) {
                    area.add(new Node(nx, ny));
                }
            }
        }

        return area;
    }

    // ================== CHEST BREAKING ==================
    private void handleChestBreaking(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        List<Obstacle> destructibleChests = gameMap.getObstaclesByTag("DESTRUCTIBLE");
        if (destructibleChests.isEmpty()) return;
        if (currentTargetChest != null && isChestStillExists(gameMap, currentTargetChest)) {
            handleCurrentChestAttack(player);
            return;
        }
        Obstacle nearestChest = findNearestChest(destructibleChests, player);
        if (nearestChest == null) return;
        currentTargetChest = nearestChest;
        String pathToChest = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestChest, true);
        if (pathToChest != null) {
            if (pathToChest.isEmpty()) {
                handleCurrentChestAttack(player);
            } else {
                hero.move(pathToChest);
            }
        } else {
            currentTargetChest = null;
        }
    }
    private void handleCurrentChestAttack(Player player) throws IOException {
        if (currentTargetChest == null) return;
        int distanceToChest = PathUtils.distance(player, currentTargetChest);
        if (distanceToChest <= 1) {
            String attackDirection = getChestAttackDirection(player, currentTargetChest);
            if (attackDirection != null) {
                hero.attack(attackDirection);
            }
        } else {
            currentTargetChest = null;
        }
    }
    private String getChestAttackDirection(Player player, Obstacle chest) {
        int playerX = player.getX(), playerY = player.getY();
        int chestX = chest.getX(), chestY = chest.getY();
        if (chestX == playerX - 1 && chestY == playerY) return "l";
        if (chestX == playerX + 1 && chestY == playerY) return "r";
        if (chestX == playerX && chestY == playerY + 1) return "u";
        if (chestX == playerX && chestY == playerY - 1) return "d";
        return null;
    }
    private Obstacle findNearestChest(List<Obstacle> chests, Player player) {
        Obstacle nearestChest = null;
        double minDistance = Double.MAX_VALUE;
        for (Obstacle chest : chests) {
            if (chest.getCurrentHp() > 0 && isChestInSafeZone(chest, player)) {
                double distance = PathUtils.distance(player, chest);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestChest = chest;
                }
            }
        }
        return nearestChest;
    }
    private boolean isChestStillExists(GameMap gameMap, Obstacle chest) {
        Element element = gameMap.getElementByIndex(chest.getX(), chest.getY());
        if (element != null && element.getType() == chest.getType()) {
            if (element instanceof Obstacle currentChest) {
                return currentChest.getCurrentHp() > 0;
            }
        }
        return false;
    }
    private boolean isChestInSafeZone(Obstacle chest, Player player) {
        GameMap gameMap = hero.getGameMap();
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(chest, safeZone, mapSize);
    }

    // ================== ITEM COLLECTING & SWAPPING ==================
    private void handleItemCollectAndSwap(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        // Tìm item gần nhất (missing hoặc upgrade)
        Element missingItem = findNearestMissingItem(gameMap, player);
        double missingItemDistance = missingItem != null ? PathUtils.distance(player, missingItem) : Double.MAX_VALUE;
        Element upgradeItem = findNearestUpgradeItem(gameMap, player);
        double upgradeItemDistance = upgradeItem != null ? PathUtils.distance(player, upgradeItem) : Double.MAX_VALUE;

        // Chọn item gần nhất
        Element nearestItem;
        double nearestItemDistance = Double.MAX_VALUE;
        if (missingItemDistance <= upgradeItemDistance) {
            nearestItem = missingItem;
            nearestItemDistance = missingItemDistance;
        } else {
            nearestItem = upgradeItem;
            nearestItemDistance = upgradeItemDistance;
        }

        // Tìm rương gần nhất
        Obstacle nearestChest = findNearestChestForComparison(gameMap, player);
        double nearestChestDistance = nearestChest != null ? PathUtils.distance(player, nearestChest) : Double.MAX_VALUE;

        // So sánh khoảng cách và quyết định hành động
        if (nearestChestDistance < nearestItemDistance) {
            // Rương gần hơn, ưu tiên đập rương
            System.out.println("[PRIORITY] Rương gần hơn (" + nearestChestDistance + " < " + nearestItemDistance + ") - Ưu tiên đập rương!");
            handleChestBreaking(gameMap, player, nodesToAvoid);
        } else if (nearestItem != null) {
            // Item gần hơn, ưu tiên nhặt item
            System.out.println("[PRIORITY] Item gần hơn (" + nearestItemDistance + " < " + nearestChestDistance + ") - Ưu tiên nhặt item!");
            if (nearestItem == missingItem) {
                handleItemCollecting(gameMap, player, nodesToAvoid, missingItem);
            } else {
                handleItemUpgrade(gameMap, player, nodesToAvoid, upgradeItem);
            }
        } else if (nearestChest != null) {
            // Không có item nào, nhưng có rương
            System.out.println("[PRIORITY] Không có item, chỉ có rương - Đập rương!");
            handleChestBreaking(gameMap, player, nodesToAvoid);
        }
    }
    private void handleItemCollecting(GameMap gameMap, Player player, List<Node> nodesToAvoid, Element targetItem) throws IOException {
        String pathToItem = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, targetItem, true);
        if (pathToItem != null) {
            if (pathToItem.isEmpty()) {
                hero.pickupItem();
            } else {
                hero.move(pathToItem);
            }
        }
    }
    private void handleItemUpgrade(GameMap gameMap, Player player, List<Node> nodesToAvoid, Element upgradeItem) throws IOException {
        Node dropPosition = findDropPosition(gameMap, upgradeItem);
        if (dropPosition == null) return;
        String pathToDropPosition = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, dropPosition, true);
        if (pathToDropPosition != null && !pathToDropPosition.isEmpty()) {
            hero.move(pathToDropPosition);
            return;
        }
        if (PathUtils.distance(player, dropPosition) <= 1) {
            performItemSwapWithDrop(upgradeItem, dropPosition);
        }
    }
    private Node findDropPosition(GameMap gameMap, Element targetItem) {
        int targetX = targetItem.getX(), targetY = targetItem.getY();
        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] dir : directions) {
            int dropX = targetX + dir[0], dropY = targetY + dir[1];
            if (isValidPosition(dropX, dropY, gameMap) && isEmptyPosition(dropX, dropY, gameMap)) {
                return new Node(dropX, dropY);
            }
        }
        return findNearestEmptyPosition(gameMap, targetX, targetY);
    }
    private boolean isValidPosition(int x, int y, GameMap gameMap) {
        int mapSize = gameMap.getMapSize();
        return x >= 0 && x < mapSize && y >= 0 && y < mapSize;
    }
    private boolean isEmptyPosition(int x, int y, GameMap gameMap) {
        List<Element> allItems = new ArrayList<>();
        allItems.addAll(gameMap.getListWeapons());
        allItems.addAll(gameMap.getListArmors());
        allItems.addAll(gameMap.getListSupportItems());
        for (Element item : allItems) {
            if (item.getX() == x && item.getY() == y) return false;
        }
        List<Obstacle> obstacles = gameMap.getListIndestructibles();
        for (Node obstacle : obstacles) {
            if (obstacle.getX() == x && obstacle.getY() == y) return false;
        }
        return true;
    }
    private Node findNearestEmptyPosition(GameMap gameMap, int targetX, int targetY) {
        double minDistance = Double.MAX_VALUE;
        Node bestPosition = null;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                int x = targetX + dx, y = targetY + dy;
                if (isValidPosition(x, y, gameMap) && isEmptyPosition(x, y, gameMap)) {
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestPosition = new Node(x, y);
                    }
                }
            }
        }
        return bestPosition;
    }
    private void performItemSwapWithDrop(Element newItem, Node dropPosition) throws IOException {
        String itemType = getItemType(newItem);
        if (itemType == null) return;
        String currentItemId = getCurrentItemId(itemType);
        if (currentItemId != null) {
            hero.revokeItem(currentItemId);
        }
        GameMap gameMap = hero.getGameMap();
        Player player = gameMap.getCurrentPlayer();
        String pathToNewItem = PathUtils.getShortestPath(gameMap, getNodesToAvoid(gameMap), player, newItem, true);
        if (pathToNewItem != null && !pathToNewItem.isEmpty()) {
            hero.move(pathToNewItem);
        } else if (PathUtils.distance(player, newItem) <= 1) {
            hero.pickupItem();
        }
    }
    private String getItemType(Element item) {
        String typeName = item.getClass().getSimpleName();
        switch (typeName) {
            case "Weapon" -> {
                Weapon w = (Weapon) item;
                return w.getType().name();
            }
            case "Armor" -> {
                Armor a = (Armor) item;
                return a.getType().name();
            }
            case "SupportItem" -> {
                return "SUPPORT";
            }
        }
        return null;
    }
    private String getCurrentItemId(String itemType) {
        return switch (itemType) {
            case "GUN" -> {
                Weapon gun = hero.getInventory().getGun();
                yield gun != null ? gun.getId() : null;
            }
            case "MELEE" -> {
                Weapon melee = hero.getInventory().getMelee();
                yield melee != null ? melee.getId() : null;
            }
            case "THROWABLE" -> {
                Weapon throwable = hero.getInventory().getThrowable();
                yield throwable != null ? throwable.getId() : null;
            }
            case "SPECIAL" -> {
                Weapon special = hero.getInventory().getSpecial();
                yield special != null ? special.getId() : null;
            }
            case "ARMOR" -> {
                Armor armor = hero.getInventory().getArmor();
                yield armor != null ? armor.getId() : null;
            }
            case "HELMET" -> {
                Armor helmet = hero.getInventory().getHelmet();
                yield helmet != null ? helmet.getId() : null;
            }
            default -> null;
        };
    }
    private Element findNearestMissingItem(GameMap gameMap, Player player) {
        Set<String> missingTypes = getMissingItemTypes();
        return findNearestItemOfNearestType(gameMap, player, missingTypes);
    }
    private Element findNearestItemOfNearestType(GameMap gameMap, Player player, Set<String> targetTypes) {
        List<Element> allItems = new ArrayList<>();
        allItems.addAll(gameMap.getListWeapons());
        allItems.addAll(gameMap.getListArmors());
        allItems.addAll(gameMap.getListSupportItems());
        Map<String, Double> typeMinDistances = new HashMap<>();
        Map<String, Element> typeNearestItems = new HashMap<>();
        for (String type : targetTypes) {
            typeMinDistances.put(type, Double.MAX_VALUE);
            typeNearestItems.put(type, null);
        }
        for (Element item : allItems) {
            String typeName = item.getClass().getSimpleName();
            String itemType = null;
            switch (typeName) {
                case "Weapon" -> {
                    Weapon w = (Weapon) item;
                    itemType = w.getType().name();
                }
                case "Armor" -> {
                    Armor a = (Armor) item;
                    itemType = a.getType().name();
                }
                case "SupportItem" -> itemType = "SUPPORT";
            }
            if (itemType == null || !targetTypes.contains(itemType)) continue;
            if (!isItemInSafeZone(item, gameMap)) continue;
            double distance = PathUtils.distance(player, item);
            if (distance < typeMinDistances.get(itemType)) {
                typeMinDistances.put(itemType, distance);
                typeNearestItems.put(itemType, item);
            }
        }
        String nearestType = null;
        double minDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Double> entry : typeMinDistances.entrySet()) {
            if (entry.getValue() < minDistance) {
                minDistance = entry.getValue();
                nearestType = entry.getKey();
            }
        }
        return typeNearestItems.get(nearestType);
    }
    private Set<String> getMissingItemTypes() {
        Set<String> missingTypes = new HashSet<>();
        if (hero.getInventory().getGun() == null) missingTypes.add("GUN");
        Weapon meleeWeapon = hero.getInventory().getMelee();
        if (meleeWeapon == null || "HAND".equals(meleeWeapon.getId())) missingTypes.add("MELEE");
        if (hero.getInventory().getThrowable() == null) missingTypes.add("THROWABLE");
        if (hero.getInventory().getSpecial() == null) missingTypes.add("SPECIAL");
        if (hero.getInventory().getArmor() == null) missingTypes.add("ARMOR");
        if (hero.getInventory().getHelmet() == null) missingTypes.add("HELMET");
        if (hero.getInventory().getListSupportItem().size() < 4) missingTypes.add("SUPPORT");
        return missingTypes;
    }
    private boolean isItemInSafeZone(Element item, GameMap gameMap) {
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(item, safeZone, mapSize);
    }
    private Element findNearestUpgradeItem(GameMap gameMap, Player player) {
        List<Element> allItems = new ArrayList<>();
        allItems.addAll(gameMap.getListWeapons());
        allItems.addAll(gameMap.getListArmors());
        allItems.addAll(gameMap.getListSupportItems());
        Element bestUpgradeItem = null;
        double minDistance = Double.MAX_VALUE;
        for (Element item : allItems) {
            if (!isItemInSafeZone(item, gameMap)) continue;
            if (isItemBetterThanCurrent(item)) {
                double distance = PathUtils.distance(player, item);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestUpgradeItem = item;
                }
            }
        }
        return bestUpgradeItem;
    }
    private boolean isItemBetterThanCurrent(Element item) {
        String itemType = getItemType(item);
        if (itemType == null) return false;
        return switch (itemType) {
            case "GUN" -> isWeaponBetter((Weapon) item, hero.getInventory().getGun());
            case "MELEE" -> isWeaponBetter((Weapon) item, hero.getInventory().getMelee());
            case "THROWABLE" -> isWeaponBetter((Weapon) item, hero.getInventory().getThrowable());
            case "SPECIAL" -> isWeaponBetter((Weapon) item, hero.getInventory().getSpecial());
            case "ARMOR" -> isArmorBetter((Armor) item, hero.getInventory().getArmor());
            case "HELMET" -> isArmorBetter((Armor) item, hero.getInventory().getHelmet());
            case "SUPPORT" -> {
                if (hero.getInventory().getListSupportItem().size() >= 4) {
                    yield isSupportItemBetter((SupportItem) item);
                }
                yield false;
            }
            default -> false;
        };
    }
    private boolean isWeaponBetter(Weapon newWeapon, Weapon currentWeapon) {
        if (currentWeapon == null) return true;
        if ("HAND".equals(currentWeapon.getId())) return true;
        int newDamage = newWeapon.getDamage();
        int currentDamage = currentWeapon.getDamage();
        return newDamage > currentDamage;
    }
    private boolean isArmorBetter(Armor newArmor, Armor currentArmor) {
        if (currentArmor == null) return true;
        int newHP = (int) newArmor.getHealthPoint();
        int currentHP = (int) currentArmor.getHealthPoint();
        return newHP > currentHP;
    }
    private boolean isSupportItemBetter(SupportItem newSupportItem) {
        List<SupportItem> currentSupportItems = hero.getInventory().getListSupportItem();
        if (currentSupportItems.isEmpty()) return true;
        SupportItem worstSupportItem = currentSupportItems.stream().min(Comparator.comparingInt(SupportItem::getHealingHP)).orElse(null);
        return newSupportItem.getHealingHP() > worstSupportItem.getHealingHP();
    }

    // ================== ENEMY HUNTING (GUN, MELEE, SPECIAL, THROWABLE, GENERIC) ==================
    private void handleEnemyHuntingAll(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        smartUseSupportItems(gameMap, player);

        // Ưu tiên xử lý đồ (nhặt, đổi, nâng cấp) nếu có item trong phạm vi 9x9 quanh bot
        if (hasItemToProcessNearby(gameMap, player, 4)) {
            System.out.println("[ENEMY HUNT] Có item gần trong phạm vi 9x9, ưu tiên xử lý đồ trước!");
            handleItemCollectAndSwap(gameMap, player, nodesToAvoid);
            return;
        }
        // --- Throwable logic (ưu tiên cao nhất) ---
        if (hero.getInventory().getThrowable() != null) {
            if (handleThrowableAttack(gameMap, player)) {
                return; // Ném được thì dừng tại đây
            }
            // Nếu không ném được, tiếp tục thử gun, melee, special
        }

        // --- Gun logic ---
        if (hero.getInventory().getGun() != null) {
            if (findAndShootNearestEnemyInRange(gameMap, player)) {
                return;
            }
        }

        // --- Melee logic ---
        Weapon meleeWeapon = hero.getInventory().getMelee();
        boolean hasMelee = meleeWeapon != null && !"HAND".equals(meleeWeapon.getId());
        if (hasMelee) {
            handleEnemyHuntingMelee(gameMap, player, nodesToAvoid);
            return;
        }

        // --- Special logic ---
        if (hero.getInventory().getSpecial() != null) {
            handleEnemyHuntingSpecial(gameMap, player, nodesToAvoid);
            return;
        }

        // --- Generic enemy hunting ---
        handleEnemyHuntingGeneric(gameMap, player, nodesToAvoid);
    }
    // --- Gun logic ---
    private boolean findAndShootNearestEnemyInRange(GameMap gameMap, Player player) throws IOException {
        Weapon gun = hero.getInventory().getGun();

        // Nếu là shotgun, sử dụng logic riêng
        if ("SHOTGUN".equals(gun.getId())) {
            return handleShotgunCombat(gameMap, player);
        }

        // Logic cho các loại súng khác
        int[] gunRange = gun.getRange();
        int minRange = gunRange[0], maxRange = gunRange[1];
        List<Player> enemies = gameMap.getOtherPlayerInfo();
        List<Player> livingEnemies = enemies.stream().filter(e -> e.getHealth() > 0).toList();
        Player targetEnemy = null;
        int minDistance = Integer.MAX_VALUE;
        for (Player enemy : livingEnemies) {
            int distance = PathUtils.distance(player, enemy);
            if (distance >= minRange && distance <= maxRange) {
                String dir = getShootDirection(player, enemy);
                if (dir != null && !isShootPathBlocked(player, enemy, dir, gameMap)) {
                    if (distance < minDistance) {
                        minDistance = distance;
                        targetEnemy = enemy;
                    }
                }
            }
        }
        if (targetEnemy != null) {
            return attackEnemyWithGun(player, targetEnemy, gameMap);
        }
        return false;
    }

    // Hàm riêng xử lý logic shotgun
    private boolean handleShotgunCombat(GameMap gameMap, Player player) throws IOException {
        List<Player> enemies = gameMap.getOtherPlayerInfo();
        List<Player> livingEnemies = enemies.stream().filter(e -> e.getHealth() > 0).toList();

        if (livingEnemies.isEmpty()) return false;

        // Tìm kẻ địch gần nhất
        Player nearestEnemy = findNearestEnemy(livingEnemies, player);
        if (nearestEnemy == null) return false;

        int distance = PathUtils.distance(player, nearestEnemy);
        System.out.println("[SHOTGUN] Kẻ địch gần nhất ở khoảng cách: " + distance);

        // Nếu đã áp sát (distance <= 1), bắn ngay
        if (distance <= 1) {
            String shootDirection = getShootDirection(player, nearestEnemy);
            if (shootDirection != null && !isShootPathBlocked(player, nearestEnemy, shootDirection, gameMap)) {
                System.out.println("[SHOTGUN] Đã áp sát, bắn ngay!");
                hero.shoot(shootDirection);
                return true;
            }
        }

        // Nếu chưa áp sát, di chuyển đến gần hơn
        System.out.println("[SHOTGUN] Chưa áp sát, di chuyển đến gần hơn...");
        String pathToEnemy = PathUtils.getShortestPath(gameMap, getNodesToAvoid(gameMap), player, nearestEnemy, true);
        if (pathToEnemy != null && !pathToEnemy.isEmpty()) {
            hero.move(pathToEnemy);
            return true;
        }

        return false;
    }

    private boolean attackEnemyWithGun(Player player, Player targetEnemy, GameMap gameMap) throws IOException {
        Weapon gun = hero.getInventory().getGun();
        int[] gunRange = gun.getRange();
        int minRange = gunRange[0], maxRange = gunRange[1];
        int distance = PathUtils.distance(player, targetEnemy);
        if (distance < minRange || distance > maxRange) return false;
        String shootDirection = getShootDirection(player, targetEnemy);
        if (shootDirection == null) return false;
        if (isShootPathBlocked(player, targetEnemy, shootDirection, gameMap)) return false;
        hero.shoot(shootDirection);
        return true;
    }
    private String getShootDirection(Player player, Player targetEnemy) {
        int playerX = player.getX(), playerY = player.getY();
        int enemyX = targetEnemy.getX(), enemyY = targetEnemy.getY();
        if (playerY == enemyY) {
            if (enemyX < playerX) return "l";
            else if (enemyX > playerX) return "r";
        }
        if (playerX == enemyX) {
            if (enemyY > playerY) return "u";
            else if (enemyY < playerY) return "d";
        }
        return null;
    }
    private boolean isShootPathBlocked(Player player, Player targetEnemy, String direction, GameMap gameMap) {
        int playerX = player.getX(), playerY = player.getY();
        int enemyX = targetEnemy.getX(), enemyY = targetEnemy.getY();
        List<Obstacle> blockingObstacles = gameMap.getObstaclesByTag("CAN_SHOOT_THROUGH");
        List<Obstacle> allObstacles = gameMap.getListObstacles();
        List<Obstacle> shootBlockingObstacles = new ArrayList<>();
        for (Obstacle obstacle : allObstacles) {
            if (!blockingObstacles.contains(obstacle)) shootBlockingObstacles.add(obstacle);
        }
        int currentX = playerX, currentY = playerY;
        while (currentX != enemyX || currentY != enemyY) {
            switch (direction) {
                case "l": currentX--; break;
                case "r": currentX++; break;
                case "u": currentY++; break;
                case "d": currentY--; break;
            }
            for (Obstacle obstacle : shootBlockingObstacles) {
                if (obstacle.getX() == currentX && obstacle.getY() == currentY) return true;
            }
        }
        return false;
    }
    // --- Melee logic ---
    private void handleEnemyHuntingMelee(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        List<Player> enemies = gameMap.getOtherPlayerInfo();
        List<Player> livingEnemies = enemies.stream().filter(e -> e.getHealth() > 0).collect(Collectors.toList());
        if (livingEnemies.isEmpty()) return;
        if (currentTargetEnemy != null && isEnemyStillAlive(gameMap, currentTargetEnemy)) {
            handleCurrentEnemyTrackingMelee(player, nodesToAvoid);
            return;
        }
        Player nearestEnemy = findNearestEnemy(livingEnemies, player);
        if (nearestEnemy == null) return;
        currentTargetEnemy = nearestEnemy;
        String pathToEnemy = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestEnemy, true);
        if (pathToEnemy != null) {
            if (pathToEnemy.isEmpty()) {
                attackEnemyWithMelee(player, nearestEnemy);
            } else {
                hero.move(pathToEnemy);
            }
        } else {
            currentTargetEnemy = null;
        }
    }
    private void handleCurrentEnemyTrackingMelee(Player player, List<Node> nodesToAvoid) throws IOException {
        if (currentTargetEnemy == null) return;
        Player updatedEnemy = getUpdatedEnemyPosition(currentTargetEnemy);
        if (updatedEnemy == null) {
            currentTargetEnemy = null;
            return;
        }
        currentTargetEnemy = updatedEnemy;
        int distanceToEnemy = PathUtils.distance(player, currentTargetEnemy);
        if (distanceToEnemy <= 1) {
            attackEnemyWithMelee(player, currentTargetEnemy);
        } else {
            String pathToEnemy = PathUtils.getShortestPath(hero.getGameMap(), nodesToAvoid, player, currentTargetEnemy, true);
            if (pathToEnemy != null && !pathToEnemy.isEmpty()) {
                hero.move(pathToEnemy);
            } else {
                currentTargetEnemy = null;
            }
        }
    }
    private void attackEnemyWithMelee(Player player, Player enemy) throws IOException {
        String attackDirection = getEnemyAttackDirection(player, enemy);
        if (attackDirection != null) {
            hero.attack(attackDirection);
        }
    }
    private String getEnemyAttackDirection(Player player, Player enemy) {
        int playerX = player.getX(), playerY = player.getY();
        int enemyX = enemy.getX(), enemyY = enemy.getY();
        if (enemyX == playerX - 1 && enemyY == playerY) return "l";
        if (enemyX == playerX + 1 && enemyY == playerY) return "r";
        if (enemyX == playerX && enemyY == playerY + 1) return "u";
        if (enemyX == playerX && enemyY == playerY - 1) return "d";
        return null;
    }
    // --- Special logic ---
    private void handleEnemyHuntingSpecial(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        List<Player> enemies = gameMap.getOtherPlayerInfo();
        List<Player> livingEnemies = enemies.stream().filter(e -> e.getHealth() > 0).collect(Collectors.toList());
        if (livingEnemies.isEmpty()) return;
        if (currentTargetEnemy != null && isEnemyStillAlive(gameMap, currentTargetEnemy)) {
            handleCurrentEnemyTrackingSpecial(player, nodesToAvoid);
            return;
        }
        Player nearestEnemy = findNearestEnemy(livingEnemies, player);
        if (nearestEnemy == null) return;
        currentTargetEnemy = nearestEnemy;
        String pathToEnemy = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestEnemy, true);
        if (pathToEnemy != null) {
            if (pathToEnemy.isEmpty()) {
                tryUseSpecialWeaponOnEnemy(player, nearestEnemy);
            } else {
                hero.move(pathToEnemy);
            }
        } else {
            currentTargetEnemy = null;
        }
    }
    private void handleCurrentEnemyTrackingSpecial(Player player, List<Node> nodesToAvoid) throws IOException {
        if (currentTargetEnemy == null) return;
        Player updatedEnemy = getUpdatedEnemyPosition(currentTargetEnemy);
        if (updatedEnemy == null) {
            currentTargetEnemy = null;
            return;
        }
        currentTargetEnemy = updatedEnemy;
        int distanceToEnemy = PathUtils.distance(player, currentTargetEnemy);
        tryUseSpecialWeaponOnEnemy(player, currentTargetEnemy);
        if (distanceToEnemy <= 1) {
            String direction = getDirectionToEnemy(player, currentTargetEnemy);
            if (direction != null) hero.attack(direction);
        } else {
            String pathToEnemy = PathUtils.getShortestPath(hero.getGameMap(), nodesToAvoid, player, currentTargetEnemy, true);
            if (pathToEnemy != null && !pathToEnemy.isEmpty()) {
                hero.move(pathToEnemy);
            } else {
                currentTargetEnemy = null;
            }
        }
    }
    private void tryUseSpecialWeaponOnEnemy(Player player, Player enemy) throws IOException {
        Weapon special = hero.getInventory().getSpecial();
        if (special == null) return;
        String specialId = special.getId();
        int range = switch (specialId) {
            case "ROPE" -> 6;
            case "BELL" -> 7;
            case "SAHUR_BAT" -> 5;
            default -> 0;
        };
        int distance = PathUtils.distance(player, enemy);
        if (distance <= range) {
            String direction = getDirectionToEnemy(player, enemy);
            if (direction != null) hero.useSpecial(direction);
        }
    }
    private String getDirectionToEnemy(Player player, Player enemy) {
        int dx = enemy.getX() - player.getX();
        int dy = enemy.getY() - player.getY();
        if (dx == 0 && dy > 0) return "u";
        if (dx == 0 && dy < 0) return "d";
        if (dy == 0 && dx > 0) return "r";
        if (dy == 0 && dx < 0) return "l";
        return null;
    }
    // --- Generic enemy hunting ---
    private void handleEnemyHuntingGeneric(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        List<Player> enemies = gameMap.getOtherPlayerInfo();
        List<Player> livingEnemies = enemies.stream().filter(e -> e.getHealth() > 0).collect(Collectors.toList());
        if (livingEnemies.isEmpty()) return;
        if (currentTargetEnemy != null && isEnemyStillAlive(gameMap, currentTargetEnemy)) {
            handleCurrentEnemyTrackingGeneric(player, nodesToAvoid);
            return;
        }
        Player nearestEnemy = findNearestEnemy(livingEnemies, player);
        if (nearestEnemy == null) return;
        currentTargetEnemy = nearestEnemy;
        String pathToEnemy = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestEnemy, true);
        if (pathToEnemy != null) {
            if (pathToEnemy.isEmpty()) {
                // Sẵn sàng tấn công nếu muốn
            } else {
                hero.move(pathToEnemy);
            }
        } else {
            currentTargetEnemy = null;
        }
    }
    private void handleCurrentEnemyTrackingGeneric(Player player, List<Node> nodesToAvoid) throws IOException {
        if (currentTargetEnemy == null) return;
        Player updatedEnemy = getUpdatedEnemyPosition(currentTargetEnemy);
        if (updatedEnemy == null) {
            currentTargetEnemy = null;
            return;
        }
        currentTargetEnemy = updatedEnemy;
        int distanceToEnemy = PathUtils.distance(player, currentTargetEnemy);
        if (distanceToEnemy <= 1) {
            // Sẵn sàng tấn công nếu muốn
        } else {
            String pathToEnemy = PathUtils.getShortestPath(hero.getGameMap(), nodesToAvoid, player, currentTargetEnemy, true);
            if (pathToEnemy != null && !pathToEnemy.isEmpty()) {
                hero.move(pathToEnemy);
            } else {
                currentTargetEnemy = null;
            }
        }
    }

    // ================== THROWABLE WEAPON FUNCTIONS ==================
    private static final Map<String, int[]> THROWABLE_RANGE = new HashMap<String, int[]>() {{
        put("BANANA", new int[]{1, 6});
        put("SMOKE", new int[]{1, 3});
        put("METEORITE_FRAGMENT", new int[]{1, 6});
        put("CRYSTAL", new int[]{1, 6});
        put("SEED", new int[]{1, 5});
    }};

    private int[] getThrowableRange(Weapon throwable) {
        int[] range = THROWABLE_RANGE.get(throwable.getId());
        if (range != null) return range;
        return throwable.getRange();
    }

    private static final Map<String, int[]> THROWABLE_EXPLODE_RANGE = new HashMap<String, int[]>() {{
        put("BANANA", new int[]{3, 3});
        put("SMOKE", new int[]{7, 7});
        put("METEORITE_FRAGMENT", new int[]{3, 3});
        put("CRYSTAL", new int[]{3, 3});
        put("SEED", new int[]{3, 3});
    }};

    private int[] getThrowableExplodeRange(Weapon throwable) {
        int[] range = THROWABLE_EXPLODE_RANGE.get(throwable.getId());
        if (range != null) return range;
        return new int[]{throwable.getExplodeRange()};
    }

    private boolean handleThrowableAttack(GameMap gameMap, Player player) throws IOException {
        Weapon throwable = hero.getInventory().getThrowable();
        if (throwable == null) return false;

        List<Player> enemies = gameMap.getOtherPlayerInfo().stream()
                .filter(e -> e.getHealth() > 0)
                .toList();

        int[] throwRange = getThrowableRange(throwable);
        int[] explodeRange = getThrowableExplodeRange(throwable);

        int px = player.getX(), py = player.getY();
        int minThrow = throwRange[0];
        int maxThrow = throwRange[1];
        int halfHeight = explodeRange[0] / 2;
        int halfWidth = explodeRange[1] / 2;

        // Duyệt 4 hướng: up, down, left, right
        String[] directions = {"u", "d", "l", "r"};
        int[][] deltas = {{0, maxThrow}, {0, -maxThrow}, {-maxThrow, 0}, {maxThrow, 0}};

        for (int i = 0; i < directions.length; i++) {
            String dir = directions[i];
            int dx = deltas[i][0];
            int dy = deltas[i][1];
            int tx = px + dx;
            int ty = py + dy;

            // Kiểm tra có enemy nào nằm trong vùng nổ centered tại (tx, ty)
            for (Player enemy : enemies) {
                int ex = enemy.getX(), ey = enemy.getY();
                if (Math.abs(ex - tx) <= halfWidth && Math.abs(ey - ty) <= halfHeight) {
                    hero.throwItem(dir);
                    System.out.println("💣 Throwing " + throwable.getId() + " to " + dir
                            + " (will explode at " + tx + "," + ty + ")"
                            + " | Target enemy at " + ex + "," + ey
                            + " | ExplodeRange: " + Arrays.toString(explodeRange));
                    return true;
                }
            }
        }
        return false;
    }

    // ================== UTILITY & GLOBAL FUNCTIONS ==================
    private Player getUpdatedEnemyPosition(Player oldEnemy) {
        GameMap gameMap = hero.getGameMap();
        List<Player> currentEnemies = gameMap.getOtherPlayerInfo();
        for (Player currentEnemy : currentEnemies) {
            if (currentEnemy.getID().equals(oldEnemy.getID()) && currentEnemy.getHealth() > 0) {
                return currentEnemy;
            }
        }
        return null;
    }
    private Player findNearestEnemy(List<Player> enemies, Player player) {
        Player nearestEnemy = null;
        double minDistance = Double.MAX_VALUE;
        for (Player enemy : enemies) {
            if (enemy.getHealth() > 0 && isEnemyInSafeZone(enemy)) {
                double distance = PathUtils.distance(player, enemy);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestEnemy = enemy;
                }
            }
        }
        return nearestEnemy;
    }
    private boolean isEnemyInSafeZone(Player enemy) {
        GameMap gameMap = hero.getGameMap();
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(enemy, safeZone, mapSize);
    }
    private boolean isEnemyStillAlive(GameMap gameMap, Player enemy) {
        List<Player> currentEnemies = gameMap.getOtherPlayerInfo();
        for (Player currentEnemy : currentEnemies) {
            if (currentEnemy.getID().equals(enemy.getID())) {
                return currentEnemy.getHealth() > 0;
            }
        }
        return false;
    }
    private List<Node> getNodesToAvoid(GameMap gameMap) {
        List<Node> nodes = new ArrayList<>(gameMap.getListIndestructibles());
        nodes.removeAll(gameMap.getObstaclesByTag("CAN_GO_THROUGH"));
        nodes.addAll(gameMap.getOtherPlayerInfo());
        nodes.addAll(gameMap.getObstaclesByTag("TRAP"));
        List<Enemy> enemies = gameMap.getListEnemies();
        int mapSize = gameMap.getMapSize();
        for (Node enemy : enemies) {
            int ex = enemy.getX();
            int ey = enemy.getY();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int nx = ex + dx;
                    int ny = ey + dy;
                    // Kiểm tra hợp lệ trong map
                    if (nx >= 0 && ny >= 0 && nx < mapSize && ny < mapSize) {
                        Node dangerNode = new Node(nx, ny);
                        if (!nodes.contains(dangerNode)) {
                            nodes.add(dangerNode);
                        }
                    }
                }
            }
        }
        nodes.addAll(getChests(gameMap));
        return nodes;
    }
    private List<Obstacle> getChests(GameMap gameMap) {
        return gameMap.getObstaclesByTag("DESTRUCTIBLE").stream().filter(obs -> {
            String id = obs.getId();
            return "CHEST".equals(id) || "DRAGON_EGG".equals(id);
        }).collect(Collectors.toList());
    }

    // Hàm tìm rương gần nhất để so sánh với item
    private Obstacle findNearestChestForComparison(GameMap gameMap, Player player) {
        List<Obstacle> destructibleChests = gameMap.getObstaclesByTag("DESTRUCTIBLE");
        if (destructibleChests.isEmpty()) return null;

        Obstacle nearestChest = null;
        double minDistance = Double.MAX_VALUE;

        for (Obstacle chest : destructibleChests) {
            if (chest.getCurrentHp() > 0 && isChestInSafeZone(chest, player)) {
                double distance = PathUtils.distance(player, chest);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestChest = chest;
                }
            }
        }
        return nearestChest;
    }

    // Kiểm tra có item cần xử lý (nhặt, đổi, nâng cấp) trong phạm vi bán kính radius (ô Manhattan)
    private boolean hasItemToProcessNearby(GameMap gameMap, Player player, int radius) {
        // Tìm item thiếu hoặc nâng cấp gần nhất
        Element missingItem = findNearestMissingItem(gameMap, player);
        Element upgradeItem = findNearestUpgradeItem(gameMap, player);
        boolean found = false;
        if (missingItem != null) {
            int dist = Math.abs(missingItem.getX() - player.getX()) + Math.abs(missingItem.getY() - player.getY());
            if (dist <= radius) found = true;
        }
        if (!found && upgradeItem != null) {
            int dist = Math.abs(upgradeItem.getX() - player.getX()) + Math.abs(upgradeItem.getY() - player.getY());
            if (dist <= radius) found = true;
        }
        return found;
    }
    /* ==============================================================
     *  COOL-DOWN MANAGER – static inner class của MainBot
     * ============================================================== */
    private static class CooldownManager {

        private static final double STEP_SEC = 0.5;   // 1 step = 0,5 s

        /* ----- Slot ghi trạng thái của 1 vũ khí ----- */
        private static final class Slot {
            Weapon w;
            int    nextStep;           // step được phép dùng lại
            int    usesLeft;           // -1 = vô hạn
            double scorePerHit;        // tuỳ chỉnh

            Slot(Weapon w, int now) {
                this.w        = w;
                this.nextStep = now;                           // dùng ngay
                this.usesLeft = w.getUseCount() <= 0 ? -1 : w.getUseCount();
                this.scorePerHit = w.getDamage() * w.getHitPoints();
            }
            boolean ready(int step) { return step >= nextStep && (usesLeft != 0); }
            void mark(int step) {
                nextStep = step + cdToStep(w);
                if (usesLeft > 0) usesLeft--;
            }
            static int cdToStep(Weapon w) {
                return (int) Math.ceil(w.getCooldown() / STEP_SEC);
            }
        }

        /* ---------------- runtime cache ---------------- */
        private final Map<String, Slot> slots = new HashMap<>();
        private       int lastInvHash = 0;

        /** Gọi mỗi tick để đồng bộ kho đồ */
        void syncInventory(Inventory inv, int step) {
            int h = Objects.hash(inv.getGun(), inv.getMelee(),
                    inv.getThrowable(), inv.getSpecial());
            if (h == lastInvHash) return;           // không đổi → thôi
            lastInvHash = h;

            slots.clear();
            add(inv.getGun(), step);
            add(inv.getMelee(), step);
            add(inv.getThrowable(), step);
            add(inv.getSpecial(), step);
        }

        private void add(Weapon w, int step) { if (w != null) slots.put(w.getId(), new Slot(w, step)); }

        /** Tra về vũ khí sẵn sàng có “điểm/CD” cao nhất */
        Weapon chooseBest(int step, boolean enemyClose) {
            double bestScore = -1;
            Slot   pick      = null;

            for (Slot s : slots.values()) {
                if (!s.ready(step)) continue;

                double score = s.scorePerHit / Slot.cdToStep(s.w);
                if (enemyClose && "MELEE".equals(s.w.getType().name()))   score *= 1.3;
                if ("THROWABLE".equals(s.w.getType().name()))             score += 50;


                if (score > bestScore) { bestScore = score; pick = s; }
            }
            return pick == null ? null : pick.w;
        }

        /** Gọi sau khi thật sự dùng vũ khí */
        void markUsed(Weapon w, int step) {
            Slot s = slots.get(w.getId());
            if (s != null) s.mark(step);
        }
    }
    private int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}