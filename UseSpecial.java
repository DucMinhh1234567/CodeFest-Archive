import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.support_items.SupportItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

public class UseSpecial {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "145230"; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "UseSpecial"; // Tên bot
    private static final String SECRET_KEY = "sk-H__B6olPTeelwSs3R46pmQ:fHUi994TZvUp1hE6v4J-6tL3oRiTyKirLStD1yjP2jW717K3lk3qNujJIFwEGK8rguQS5sCVPFykXuHtdmD3Tg"; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new UseSpecialListener(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}

class UseSpecialListener implements Emitter.Listener {
    private final Hero hero;
    private Player currentTargetEnemy = null;

    public UseSpecialListener(Hero hero) {
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

            // Tìm item chưa có gần nhất để nhặt
            handleItemCollecting(gameMap, player, nodesToAvoid);

            // Chỉ đi săn kẻ thù khi có vũ khí special
            if (hero.getInventory().getSpecial() != null) {
                handleEnemyHunting(gameMap, player, nodesToAvoid);
            }

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

        // // Kiểm tra melee (chỉ thiếu nếu chưa có hoặc chỉ có HAND)
        // Weapon meleeWeapon = hero.getInventory().getMelee();
        
        // if (meleeWeapon == null || "HAND".equals(meleeWeapon.getId())) {
        //     missingTypes.add("MELEE");
        // }
        
        // // Kiểm tra throwable
        // if (hero.getInventory().getThrowable() == null) {
        //     missingTypes.add("THROWABLE");
        // }
        
        // Kiểm tra special
        if (hero.getInventory().getSpecial() == null) {
            missingTypes.add("SPECIAL");
        }
        
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
                // Ở đây có thể thêm logic tấn công nếu muốn
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

        //------------------------------------------
        // Luôn kiểm tra tấn công xa trước
        tryUseSpecialWeaponOnEnemy(player, currentTargetEnemy);
        //------------------------------------------

        if (distanceToEnemy <= 1) {
            // Đã ở cạnh kẻ địch
            System.out.println("Đã đến cạnh kẻ địch! Khoảng cách: " + distanceToEnemy);
            String direction = getDirectionToEnemy(player, currentTargetEnemy);
            if (direction != null) {
                hero.attack(direction); // Thực hiện tấn công cận chiến
            }
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

    // Global Functions
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

    //==================================================================================================

    private void tryUseSpecialWeaponOnEnemy(Player player, Player enemy) throws IOException {
        Weapon special = hero.getInventory().getSpecial();
        if (special == null) return;

        String specialId = special.getId();
        int range = 0;
        switch (specialId) {
            case "ROPE":
                range = 6;
                break;
            case "BELL":
                range = 7;
                break;
            case "SAHUR_BAT":
                range = 5;
                break;
            default:
                return;
        }

        int distance = PathUtils.distance(player, enemy);
        if (distance <= range) {
            String direction = getDirectionToEnemy(player, enemy);
            System.out.println("Địch ở vị trí max range của " + specialId + ", sử dụng specials hướng: " + direction);
            hero.useSpecial(direction);
        }
    }

    // Hàm xác định hướng từ player đến enemy (giả sử chỉ di chuyển theo 4 hướng cơ bản)
    private String getDirectionToEnemy(Player player, Player enemy) {
        int dx = enemy.getX() - player.getX();
        int dy = enemy.getY() - player.getY();

        if (dx == 0 && dy > 0) return "u";      // enemy phía dưới
        if (dx == 0 && dy < 0) return "d";      // enemy phía trên
        if (dy == 0 && dx > 0) return "r";      // enemy bên phải
        if (dy == 0 && dx < 0) return "l";      // enemy bên trái
        // Nếu không cùng hàng/cột thì không trả về hướng
        return null;
    }
    //===================================================================================================
}