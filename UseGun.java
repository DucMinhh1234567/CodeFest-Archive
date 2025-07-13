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

public class UseGun {
    // File này kết hợp file tìm địch và file nhặt đồ đấy
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "176602"; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "UseGun"; // Tên bot
    private static final String SECRET_KEY = "sk-H__B6olPTeelwSs3R46pmQ:fHUi994TZvUp1hE6v4J-6tL3oRiTyKirLStD1yjP2jW717K3lk3qNujJIFwEGK8rguQS5sCVPFykXuHtdmD3Tg"; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new UseGunListener(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);

        System.out.println("Enemy Hunter Bot đã khởi động!");
        System.out.println("Bot sẽ tìm và đi đến vị trí của các kẻ địch còn sống...");
    }
}

class UseGunListener implements Emitter.Listener {
    private final Hero hero;
    private Player currentTargetEnemy = null;

    public UseGunListener(Hero hero) {
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

            // Ưu tiên 1: Tìm và bắn địch trong tầm bắn
            if (hero.getInventory().getGun() != null) {
                if (findAndShootNearestEnemyInRange(gameMap, player)) {
                    System.out.println("Đã bắn địch trong tầm!");
                    return;
                }

                // Ưu tiên 2: Tìm và đuổi địch
                handleEnemyHunting(gameMap, player, nodesToAvoid);
            }
            else {

                handleItemCollecting(gameMap, player, nodesToAvoid);
            }

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================================================
    private boolean attackEnemyWithGun(Player player, Player targetEnemy, GameMap gameMap) throws IOException {
        // Kiểm tra có Gun không
        if (hero.getInventory().getGun() == null) {
            System.out.println("Không có Gun để bắn!");
            return false;
        }

        Weapon gun = hero.getInventory().getGun();

        // Lấy thông tin tầm bắn của Gun
        int[] gunRange = gun.getRange();
        int minRange = gunRange[0]; // Tầm bắn tối thiểu
        int maxRange = gunRange[1]; // Tầm bắn tối đa

        // Tính khoảng cách Manhattan giữa bot và địch
        int distance = PathUtils.distance(player, targetEnemy);

        // Kiểm tra địch có trong tầm bắn không
        if (distance < minRange || distance > maxRange) {
            System.out.println("Địch không trong tầm bắn! Khoảng cách: " + distance +
                    ", Tầm bắn: " + minRange + "-" + maxRange);
            return false;
        }

        // Xác định hướng bắn
        String shootDirection = getShootDirection(player, targetEnemy);
        if (shootDirection == null) {
            System.out.println("Không thể xác định hướng bắn!");
            return false;
        }

        // Kiểm tra đường bắn có bị chặn không
        if (isShootPathBlocked(player, targetEnemy, shootDirection, gameMap)) {
            System.out.println("Đường bắn bị chặn!");
            return false;
        }

        // Thực hiện bắn
        System.out.println("Bắn Gun về hướng " + shootDirection + " vào địch tại (" +
                targetEnemy.getX() + ", " + targetEnemy.getY() + ")");
        hero.shoot(shootDirection);
        return true;
    }

    private String getShootDirection(Player player, Player targetEnemy) {
        int playerX = player.getX();
        int playerY = player.getY();
        int enemyX = targetEnemy.getX();
        int enemyY = targetEnemy.getY();

        // Kiểm tra hướng ngang (trái/phải)
        if (playerY == enemyY) {
            if (enemyX < playerX) {
                return "l"; // Bắn trái
            } else if (enemyX > playerX) {
                return "r"; // Bắn phải
            }
        }

        // Kiểm tra hướng dọc (lên/xuống)
        if (playerX == enemyX) {
            if (enemyY > playerY) {
                return "u"; // Bắn lên
            } else if (enemyY < playerY) {
                return "d"; // Bắn xuống
            }
        }

        // Nếu không cùng hàng hoặc cột, không thể bắn trực tiếp
        return null;
    }

    private boolean isShootPathBlocked(Player player, Player targetEnemy, String direction, GameMap gameMap) {
        int playerX = player.getX();
        int playerY = player.getY();
        int enemyX = targetEnemy.getX();
        int enemyY = targetEnemy.getY();

        // Lấy danh sách vật cản không thể bắn xuyên qua
        List<Obstacle> blockingObstacles = gameMap.getObstaclesByTag("CAN_SHOOT_THROUGH");
        List<Obstacle> allObstacles = gameMap.getListObstacles();

        // Lọc ra những vật cản có thể chặn đường bắn
        List<Obstacle> shootBlockingObstacles = new ArrayList<>();
        for (Obstacle obstacle : allObstacles) {
            if (!blockingObstacles.contains(obstacle)) {
                shootBlockingObstacles.add(obstacle);
            }
        }

        // Kiểm tra từng ô trên đường bắn
        int currentX = playerX;
        int currentY = playerY;

        while (currentX != enemyX || currentY != enemyY) {
            // Di chuyển theo hướng bắn
            switch (direction) {
                case "l":
                    currentX--;
                    break;
                case "r":
                    currentX++;
                    break;
                case "u":
                    currentY++;
                    break;
                case "d":
                    currentY--;
                    break;
            }

            // Kiểm tra có vật cản chặn không
            for (Obstacle obstacle : shootBlockingObstacles) {
                if (obstacle.getX() == currentX && obstacle.getY() == currentY) {
                    return true; // Đường bắn bị chặn
                }
            }
        }

        return false; // Đường bắn thông thoáng
    }

    private boolean findAndShootNearestEnemyInRange(GameMap gameMap, Player player) throws IOException {
        // Kiểm tra có Gun không
        if (hero.getInventory().getGun() == null) {
            return false;
        }

        Weapon gun = hero.getInventory().getGun();
        int[] gunRange = gun.getRange();
        int minRange = gunRange[0];
        int maxRange = gunRange[1];

        // Lấy danh sách địch còn sống
        List<Player> enemies = gameMap.getOtherPlayerInfo();
        List<Player> livingEnemies = enemies.stream()
                .filter(enemy -> enemy.getHealth() > 0)
                .toList();

        Player targetEnemy = null;
        int minDistance = Integer.MAX_VALUE;

        // Tìm địch gần nhất trong tầm bắn
        for (Player enemy : livingEnemies) {
            int distance = PathUtils.distance(player, enemy);

            if (distance >= minRange && distance <= maxRange) {
                // Kiểm tra có thể bắn được không (cùng hàng hoặc cột)
                if (getShootDirection(player, enemy) != null) {
                    // Kiểm tra đường bắn không bị chặn
                    if (!isShootPathBlocked(player, enemy, getShootDirection(player, enemy), gameMap)) {
                        if (distance < minDistance) {
                            minDistance = distance;
                            targetEnemy = enemy;
                        }
                    }
                }
            }
        }

        // Bắn địch nếu tìm thấy
        if (targetEnemy != null) {
            return attackEnemyWithGun(player, targetEnemy, gameMap);
        }

        return false;
    }
    // ==================================================


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
                if (attackEnemyWithGun(player, currentTargetEnemy, gameMap)) {
                    System.out.println("Đã bắn địch khi ở cạnh!");
                } else {
                    System.out.println("Không thể bắn, có thể cần tấn công cận chiến.");
                }
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
            // ------------------------------------------------------------
            // Đã ở cạnh kẻ địch, thử bắn Gun
//            if (attackEnemyWithGun(player, currentTargetEnemy, gameMap)) {
//                System.out.println("Đã bắn địch khi ở cạnh! Khoảng cách: " + distanceToEnemy);
//            } else {
//                System.out.println("Không thể bắn, có thể cần tấn công cận chiến. Khoảng cách: " + distanceToEnemy);
//            }
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

    // Tìm GUN weapon gần nhất
    private Element findNearestItemOfNearestType(GameMap gameMap, Player player, Set<String> targetTypes) {
        List<Element> allWeapons = new ArrayList<>(gameMap.getListWeapons());
        Element nearestGunWeapon = null;
        double minDistance = Double.MAX_VALUE;

        // Chỉ tìm GUN weapons
        for (Element item : allWeapons) {
            if (item instanceof Weapon weapon) {

                // Chỉ xem xét GUN weapons
                if ("GUN".equals(weapon.getType().name())) {
                    // Kiểm tra có trong bo không
                    if (isItemInSafeZone(item, gameMap)) {
                        double distance = PathUtils.distance(player, item);
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestGunWeapon = item;
                        }
                    }
                }
            }
        }

        return nearestGunWeapon;
    }

    // Xác định các loại item còn thiếu
    private Set<String> getMissingItemTypes() {
        Set<String> missingTypes = new HashSet<>();

        // Kiểm tra gun (chỉ thiếu nếu chưa có)
        if (hero.getInventory().getGun() == null) {
            missingTypes.add("GUN");
        }

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