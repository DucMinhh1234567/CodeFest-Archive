import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.support_items.SupportItem;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "112783"; // Nhập Game ID vào đây
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

            // === SMART SUPPORT ITEM USAGE ===
            smartUseSupportItems(gameMap, player);

            // === CHEST BREAKING ===
            handleChestBreaking(gameMap, player, getNodesToAvoid(gameMap));

            // === ITEM COLLECTING & SWAPPING ===
            handleItemCollectAndSwap(gameMap, player, getNodesToAvoid(gameMap));

            // === ENEMY HUNTING (GUN, MELEE, SPECIAL) ===
            // Chỉ đánh nhau khi có đủ từ 3 loại vũ khí trở lên
            if (hasEnoughWeapons()) {
                handleEnemyHuntingAll(gameMap, player, getNodesToAvoid(gameMap));
            } else {
                System.out.println("[COMBAT] Chưa đủ vũ khí để đánh nhau. Cần ít nhất 3 loại vũ khí.");
            }

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
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
                    if (abs < minAbs && currentHp < maxHp) {
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
        Element nearestItem = null;
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
        int mapSize = gameMap.getMapSize();
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
        Weapon meleeWeapon = hero.getInventory().getMelee();
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
        nodes.addAll(gameMap.getListEnemies());
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
}
