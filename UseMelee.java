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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class Main {
    // File này kết hợp file tìm địch và file nhặt đồ đấy
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "193605"; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "EnemyHunter"; // Tên bot
    private static final String SECRET_KEY = "sk-H__B6olPTeelwSs3R46pmQ:fHUi994TZvUp1hE6v4J-6tL3oRiTyKirLStD1yjP2jW717K3lk3qNujJIFwEGK8rguQS5sCVPFykXuHtdmD3Tg"; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new UseMeleeListener(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);

        System.out.println("Enemy Hunter Bot đã khởi động!");
        System.out.println("Bot sẽ tìm và đi đến vị trí của các kẻ địch còn sống...");
    }
}

class UseMeleeListener implements Emitter.Listener {
    private final Hero hero;
    private Player currentTargetEnemy = null;

    public UseMeleeListener(Hero hero) {
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

            // Lấy danh sách các node cần tránh
            List<Node> nodesToAvoid = getNodesToAvoid(gameMap);

            // Kiểm tra xem có MELEE weapon khác HAND không
            Weapon meleeWeapon = hero.getInventory().getMelee();
            boolean hasMelee = meleeWeapon != null && !"HAND".equals(meleeWeapon.getId());

            if (hasMelee) {
                System.out.println("Đã có MELEE: " + meleeWeapon.getId() + " - Bắt đầu tìm địch!");
                // Tìm và đi đến kẻ địch gần nhất
                handleEnemyHunting(gameMap, player, nodesToAvoid);
            } else {
                System.out.println("Chưa có MELEE, tìm MELEE...");
                // Tìm đồ Melee trước, khi có đồ Melee mới tìm địch
                handleItemCollecting(gameMap, player, nodesToAvoid);
            }



        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================================================================
    // Tấn công kẻ địch bằng melee weapon khi ở cạnh chúng
    private void attackEnemyWithMelee(Player player, Player enemy) throws IOException {
        // Kiểm tra xem có melee weapon không
        Weapon meleeWeapon = hero.getInventory().getMelee();
        if (meleeWeapon == null || "HAND".equals(meleeWeapon.getId())) {
            System.out.println("Không có melee weapon tốt, sử dụng tay không để tấn công!");
        } else {
            System.out.println("Sử dụng melee weapon: " + meleeWeapon.getId() + " để tấn công!");
        }

        // Xác định hướng tấn công
        String attackDirection = getEnemyAttackDirection(player, enemy);

        if (attackDirection != null) {
            System.out.println("Tấn công kẻ địch tại (" + enemy.getX() + ", " + enemy.getY() +
                    ") - HP: " + enemy.getHealth() + " - Score: " + enemy.getScore() +
                    " - Hướng: " + attackDirection);

            // Thực hiện tấn công
            hero.attack(attackDirection);
        } else {
            System.out.println("Không thể xác định hướng tấn công kẻ địch!");
        }
    }


    // Xác định hướng tấn công dựa trên vị trí tương đối của kẻ địch
    private String getEnemyAttackDirection(Player player, Player enemy) {
        int playerX = player.getX();
        int playerY = player.getY();
        int enemyX = enemy.getX();
        int enemyY = enemy.getY();

        // Xác định hướng đánh dựa trên vị trí tương đối
        if (enemyX == playerX - 1 && enemyY == playerY) return "l"; // Kẻ địch ở bên trái
        if (enemyX == playerX + 1 && enemyY == playerY) return "r"; // Kẻ địch ở bên phải
        if (enemyX == playerX && enemyY == playerY + 1) return "u"; // Kẻ địch ở trên
        if (enemyX == playerX && enemyY == playerY - 1) return "d"; // Kẻ địch ở dưới

        return null; // Không ở cạnh kẻ địch
    }
    // ================================================================

    // ==================== Hàm tìm kẻ địch =============================
    private void handleEnemyHunting(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        // Lấy danh sách tất cả các player khác (kẻ địch)
        List<Player> enemies = gameMap.getOtherPlayerInfo();

        // Lọc ra những kẻ địch còn sống
        List<Player> livingEnemies = enemies.stream()
                .filter(enemy -> enemy.getHealth() > 0)
                .collect(Collectors.toList());

        if (livingEnemies.isEmpty()) {
            System.out.println("Không còn kẻ địch nào còn sống!");
            return;
        }

        // Nếu đang theo dõi một kẻ địch cụ thể và kẻ đó vẫn còn sống
        if (currentTargetEnemy != null && isEnemyStillAlive(gameMap, currentTargetEnemy)) {
            handleCurrentEnemyTracking(player, nodesToAvoid);
            return;
        }

        // Tìm kẻ địch gần nhất
        Player nearestEnemy = findNearestEnemy(livingEnemies, player);
        if (nearestEnemy == null) {
            System.out.println("Không tìm thấy kẻ địch nào!");
            return;
        }

        // Đặt kẻ địch mới làm mục tiêu
        currentTargetEnemy = nearestEnemy;

        System.out.println("Tìm thấy kẻ địch gần nhất tại vị trí (" + nearestEnemy.getX() + ", " + nearestEnemy.getY() +
                ") - HP: " + nearestEnemy.getHealth() + " - Score: " + nearestEnemy.getScore());

        // Di chuyển đến kẻ địch
        String pathToEnemy = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestEnemy, true);

        if (pathToEnemy != null) {
            if (pathToEnemy.isEmpty()) {
                // Đã ở cạnh kẻ địch, có thể tấn công
                System.out.println("Đã đến cạnh kẻ địch! Sẵn sàng tấn công.");
                // ------------------------------------------------------------
                attackEnemyWithMelee(player, nearestEnemy);
                // ------------------------------------------------------------
            } else {
                // Di chuyển đến kẻ địch
                System.out.println("Di chuyển đến kẻ địch: " + pathToEnemy);
                hero.move(pathToEnemy);
            }
        } else {
            System.out.println("Không tìm thấy đường đi đến kẻ địch!");
            currentTargetEnemy = null; // Reset target
        }
    }

    private void handleCurrentEnemyTracking(Player player, List<Node> nodesToAvoid) throws IOException {
        if (currentTargetEnemy == null) return;

        // Cập nhật vị trí mới
        Player updatedEnemy = getUpdatedEnemyPosition(currentTargetEnemy);
        if (updatedEnemy == null) {
            System.out.println("Kẻ địch không còn tồn tại, tìm kẻ địch khác...");
            currentTargetEnemy = null;
            return;
        }

        // Cập nhật vị trí mới của kẻ địch
        currentTargetEnemy = updatedEnemy;

        // Kiểm tra khoảng cách với kẻ địch hiện tại (vị trí mới)
        int distanceToEnemy = PathUtils.distance(player, currentTargetEnemy);

        if (distanceToEnemy <= 1) {
            // Đã ở cạnh kẻ địch
            System.out.println("Đã đến cạnh kẻ địch! Khoảng cách: " + distanceToEnemy);
            // ------------------------------------------------------------
            attackEnemyWithMelee(player, currentTargetEnemy);
            // ------------------------------------------------------------
        } else {
            // Vẫn đang theo dõi kẻ địch, tiếp tục di chuyển đến vị trí mới
            String pathToEnemy = PathUtils.getShortestPath(hero.getGameMap(), nodesToAvoid, player, currentTargetEnemy, true);
            if (pathToEnemy != null && !pathToEnemy.isEmpty()) {
                System.out.println("Tiếp tục theo dõi kẻ địch tại vị trí mới (" + currentTargetEnemy.getX() + ", " + currentTargetEnemy.getY() + "): " + pathToEnemy);
                hero.move(pathToEnemy);
            } else {
                System.out.println("Mất dấu kẻ địch, tìm kẻ địch khác...");
                currentTargetEnemy = null;
            }
        }
    }

    // Hàm mới để lấy vị trí cập nhật của kẻ địch từ server
    private Player getUpdatedEnemyPosition(Player oldEnemy) {
        GameMap gameMap = hero.getGameMap();
        List<Player> currentEnemies = gameMap.getOtherPlayerInfo();

        for (Player currentEnemy : currentEnemies) {
            if (currentEnemy.getID().equals(oldEnemy.getID()) && currentEnemy.getHealth() > 0) {
                return currentEnemy; // Trả về kẻ địch với vị trí mới nhất
            }
        }
        return null; // Kẻ địch không còn tồn tại hoặc đã chết
    }

    private Player findNearestEnemy(List<Player> enemies, Player player) {
        Player nearestEnemy = null;
        double minDistance = Double.MAX_VALUE;

        for (Player enemy : enemies) {
            // Chỉ xem xét những kẻ địch còn sống và trong safe zone
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

    // Kiểm tra kẻ địch có nằm trong safe zone không
    private boolean isEnemyInSafeZone(Player enemy) {
        GameMap gameMap = hero.getGameMap();
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(enemy, safeZone, mapSize);
    }

    private boolean isEnemyStillAlive(GameMap gameMap, Player enemy) {
        // Kiểm tra xem kẻ địch có còn sống không
        List<Player> currentEnemies = gameMap.getOtherPlayerInfo();
        for (Player currentEnemy : currentEnemies) {
            if (currentEnemy.getID().equals(enemy.getID())) {
                return currentEnemy.getHealth() > 0;
            }
        }
        return false;
    }
    // ===================== Kết thúc hàm tìm kẻ địch =============================


    // ===================== Hàm nhặt item =============================
    private void handleItemCollecting(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        Element nearestItem = findNearestMissingItem(gameMap, player);
        if (nearestItem == null) {
            System.out.println("Không còn item nào chưa có để nhặt!");
            return;
        }

        Element currentTargetItem = nearestItem;
        System.out.println("Tìm thấy item chưa có gần nhất tại (" + nearestItem.getX() + ", " + nearestItem.getY() + ")");

        String pathToItem = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestItem, true);
        if (pathToItem != null) {
            if (pathToItem.isEmpty()) {
                // Đã ở vị trí item, nhặt item
                hero.pickupItem();
            } else {
                // Di chuyển đến item
                System.out.println("Di chuyển đến item: " + pathToItem);
                hero.move(pathToItem);
            }
        } else {
            System.out.println("Không tìm thấy đường đi đến item!");
            currentTargetItem = null;
        }
    }

    // Tìm item chưa có gần nhất
    private Element findNearestMissingItem(GameMap gameMap, Player player) {
        Set<String> missingTypes = getMissingItemTypes();
        return findNearestItemOfNearestType(gameMap, player, missingTypes);
    }

    // Tìm loại item gần nhất trước, sau đó tìm item gần nhất của loại đó
    private Element findNearestItemOfNearestType(GameMap gameMap, Player player, Set<String> targetTypes) {
        List<Element> allItems = new ArrayList<>();
        allItems.addAll(gameMap.getListWeapons());
        allItems.addAll(gameMap.getListArmors());
        allItems.addAll(gameMap.getListSupportItems());

        // Tính khoảng cách gần nhất cho từng loại còn thiếu
        Map<String, Double> typeMinDistances = new HashMap<>();
        Map<String, Element> typeNearestItems = new HashMap<>();

        // Khởi tạo khoảng cách vô cùng cho tất cả loại
        for (String type : targetTypes) {
            typeMinDistances.put(type, Double.MAX_VALUE);
            typeNearestItems.put(type, null);
        }

        // Duyệt qua tất cả item để tính khoảng cách gần nhất cho từng loại
        for (Element item : allItems) {
            String typeName = item.getClass().getSimpleName();
            String itemType = null;

            // Xác định loại item
            if (typeName.equals("Weapon")) {
                Weapon w = (Weapon) item;
                itemType = w.getType().name();
            } else if (typeName.equals("Armor")) {
                Armor a = (Armor) item;
                itemType = a.getType().name();
            } else if (typeName.equals("SupportItem")) {
                itemType = "SUPPORT";
            }

            // Kiểm tra có phải loại cần tìm không
            if (itemType == null || !targetTypes.contains(itemType)) {
                continue;
            }

            // Kiểm tra có trong bo không
            if (!isItemInSafeZone(item, gameMap)) {
                continue;
            }

            double distance = PathUtils.distance(player, item);
            if (distance < typeMinDistances.get(itemType)) {
                typeMinDistances.put(itemType, distance);
                typeNearestItems.put(itemType, item);
            }
        }

        // Tìm loại có khoảng cách gần nhất
        String nearestType = null;
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<String, Double> entry : typeMinDistances.entrySet()) {
            if (entry.getValue() < minDistance) {
                minDistance = entry.getValue();
                nearestType = entry.getKey();
            }
        }

        // Trả về item gần nhất của loại đã chọn
        return typeNearestItems.get(nearestType);
    }

    // Xác định các loại item còn thiếu
    private Set<String> getMissingItemTypes() {
        Set<String> missingTypes = new HashSet<>();

        // // Kiểm tra gun
        // if (hero.getInventory().getGun() == null) {
        //     missingTypes.add("GUN");
        // }

        // Kiểm tra melee (chỉ thiếu nếu chưa có hoặc chỉ có HAND)
        Weapon meleeWeapon = hero.getInventory().getMelee();
        if (meleeWeapon == null || "HAND".equals(meleeWeapon.getId())) {
            missingTypes.add("MELEE");
        }

        // // Kiểm tra throwable
        // if (hero.getInventory().getThrowable() == null) {
        //     missingTypes.add("THROWABLE");
        // }

        // // Kiểm tra special
        // if (hero.getInventory().getSpecial() == null) {
        //     missingTypes.add("SPECIAL");
        // }

        // // Kiểm tra armor
        // if (hero.getInventory().getArmor() == null) {
        //     missingTypes.add("ARMOR");
        // }

        // // Kiểm tra helmet
        // if (hero.getInventory().getHelmet() == null) {
        //     missingTypes.add("HELMET");
        // }

        // // Support items - chỉ thêm nếu chưa đủ 4 slot
        // if (hero.getInventory().getListSupportItem().size() < 4) {
        //     missingTypes.add("SUPPORT");
        // }

        return missingTypes;
    }

    // Kiểm tra item có nằm trong bo không
    private boolean isItemInSafeZone(Element item, GameMap gameMap) {
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(item, safeZone, mapSize);
    }
    // ===================== Kết thúc hàm nhặt item =============================

    // GLOBAL FUNCTION
    private List<Node> getNodesToAvoid(GameMap gameMap) {
        List<Node> nodes = new ArrayList<>(gameMap.getListIndestructibles());
        nodes.removeAll(gameMap.getObstaclesByTag("CAN_GO_THROUGH"));
        nodes.addAll(gameMap.getOtherPlayerInfo());
        nodes.addAll(gameMap.getObstaclesByTag("TRAP"));
        nodes.addAll(gameMap.getListEnemies());
        nodes.addAll(getChests(gameMap));
        return nodes;
    }

    // Temporary function to get chests
    private List<Obstacle> getChests(GameMap gameMap) {
        return gameMap.getObstaclesByTag("DESTRUCTIBLE")
                .stream()
                .filter(obs -> {
                    String id = obs.getId();
                    return "CHEST".equals(id) || "DRAGON_EGG".equals(id);
                })
                .collect(Collectors.toList());
    }
}