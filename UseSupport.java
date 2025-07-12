import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
// ================================
// Thần được xóa cả tốt lẫn bất lợi

public class UseSupport {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "126022"; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "UseSupportItem"; // Tên bot
    private static final String SECRET_KEY = "sk-qYZm8FeOSAiZD1yCs-l7Yw:c21__W0fmgR8HVFTHVlAtaW-cEF7o9Ml5xgs1mHYCjMTpMthlG1tvtSucmUyqjyQu3Bg_gUGOY-dYlD-1cAJuA"; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new UseSupportItem(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}

class UseSupportItem implements Emitter.Listener {
    private final Hero hero;
    private Obstacle currentTargetChest = null;
    // Thêm biến lưu trạng thái kẻ địch để dùng cho logic la bàn
    private Player currentTargetEnemy = null;
    //===============================

    public UseSupportItem(Hero hero) {
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

            // GỌI HÀM SỬ DỤNG SUPPORT ITEM THÔNG MINH
            smartUseSupportItems(gameMap, player);
            //===============================

            // Lấy danh sách các node cần tránh
            List<Node> nodesToAvoid = getNodesToAvoid(gameMap);

            // Tìm rương gần nhất để đập
            handleChestBreaking(gameMap, player, nodesToAvoid);

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================== HÀM SỬ DỤNG SUPPORT ITEM THÔNG MINH ==================
    /**
     * Hàm này sẽ kiểm tra và sử dụng các support item một cách khôn khéo theo yêu cầu:
     * - Đồ hồi máu: chọn item hồi máu sao cho HP gần max nhất mà không lãng phí.
     * - La bàn: dùng khi có địch trong phạm vi 6x6.
     * - Thần dược: dùng khi bị hiệu ứng bất lợi.
     * - Tiên dược sự sống: dùng khi sắp chết.
     * - Gậy thần: dùng khi cần tàng hình để chạy trốn hoặc áp sát.
     */
    private void smartUseSupportItems(GameMap gameMap, Player player) throws IOException {
        List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
        Float currentHp = player.getHealth();
        int maxHp = 100;

        // 1. ĐỒ HỒI MÁU: chọn item sao cho HP gần max nhất
        String[] hpItemIds = {"GOD_LEAF", "SPIRIT_TEAR", "MERMAID_TAIL", "PHOENIX_FEATHERS", "UNICORN_BLOOD"};
        int[] hpValues = {10, 15, 20, 40, 80}; // mapping theo thứ tự trên
        int minAbs = Integer.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < supportItems.size(); i++) {
            Element item = supportItems.get(i);
            String id = item.getId();
            for (int j = 0; j < hpItemIds.length; j++) {
                if (id.equals(hpItemIds[j])) {
                    int abs = (int) Math.abs(currentHp + hpValues[j] - maxHp);
                    if (abs < minAbs && currentHp < maxHp) {
                        minAbs = abs;
                        bestIdx = i;
                    }
                }
            }
        }
        if (bestIdx != -1) {
            // Sử dụng item hồi máu tối ưu
            System.out.println("[SUPPORT] Sử dụng item hồi máu: " + supportItems.get(bestIdx).getId());
            hero.useItem(supportItems.get(bestIdx).getId());
            return; // Ưu tiên hồi máu trước
        }

        // 2. LA BÀN: dùng khi có địch trong phạm vi 6x6
        for (Element item : supportItems) {
            if (item.getId().equals("COMPASS")) {
                List<Player> enemies = gameMap.getOtherPlayerInfo();
                for (Player enemy : enemies) {
                    if (enemy.getHealth() > 0) {
                        int dx = Math.abs(enemy.getX() - player.getX());
                        int dy = Math.abs(enemy.getY() - player.getY());
                        if (dx <= 3 && dy <= 3) { // 6x6 centered on player
                            System.out.println("[SUPPORT] Sử dụng La bàn khi có địch gần!");
                            hero.useItem("COMPASS");
                            return;
                        }
                    }
                }
            }
        }

        // 3. THẦN DƯỢC: dùng khi bị hiệu ứng bất lợi
        for (Element item : supportItems) {
            if (item.getId().equals("ELIXIR")) {
                // SỬA: Chỉ kích hoạt nếu danh sách hiệu ứng xấu khác rỗng và có >= 1 phần tử
                if (hero.getEffects() != null && !hero.getEffects().isEmpty()) {
                    System.out.println("[SUPPORT] Sử dụng Thần dược để giải hiệu ứng!");
                    hero.useItem("ELIXIR");
                    return;
                }
            }
        }

        // 4. GẬY THẦN: dùng khi cần tàng hình (ví dụ bị truy đuổi)
        for (Element item : supportItems) {
            if (item.getId().equals("MAGIC")) {
                // Nếu có nhiều địch gần hoặc HP thấp thì dùng để chạy trốn
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
    //===============================

    private void handleChestBreaking(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        // Lấy danh sách tất cả các rương có thể phá hủy
        List<Obstacle> destructibleChests = gameMap.getObstaclesByTag("DESTRUCTIBLE");

        if (destructibleChests.isEmpty()) {
            System.out.println("Không còn rương nào để đập!");
            return;
        }

        // Nếu đang đập một rương cụ thể và rương đó vẫn còn
        if (currentTargetChest != null && isChestStillExists(gameMap, currentTargetChest)) {
            handleCurrentChestAttack(player);
            return;
        }

        // Tìm rương gần nhất
        Obstacle nearestChest = findNearestChest(destructibleChests, player);
        if (nearestChest == null) {
            System.out.println("Không tìm thấy rương nào!");
            return;
        }

        // Đặt rương mới làm mục tiêu
        currentTargetChest = nearestChest;

        System.out.println("Tìm thấy rương mới tại vị trí (" + nearestChest.getX() + ", " + nearestChest.getY() + ")");

        // Di chuyển đến rương
        String pathToChest = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestChest, true);

        if (pathToChest != null) {
            if (pathToChest.isEmpty()) {
                // Đã ở cạnh rương, bắt đầu đập
                handleCurrentChestAttack(player);
            } else {
                // Di chuyển đến rương
                System.out.println("Di chuyển đến rương: " + pathToChest);
                hero.move(pathToChest);
            }
        } else {
            System.out.println("Không tìm thấy đường đi đến rương!");
            currentTargetChest = null; // Reset target
        }
    }

    private void handleCurrentChestAttack(Player player) throws IOException {
        if (currentTargetChest == null) return;

        // Kiểm tra xem có đang ở cạnh rương không
        int distanceToChest = PathUtils.distance(player, currentTargetChest);

        if (distanceToChest <= 1) {
            // Đang ở cạnh rương, bắt đầu đập
            String attackDirection = getChestAttackDirection(player, currentTargetChest);

            if (attackDirection != null) {
                System.out.println("Đập rương tại (" + currentTargetChest.getX() + ", " + currentTargetChest.getY() +
                        ") - HP còn lại: " + currentTargetChest.getCurrentHp() + "/" + currentTargetChest.getHp());

                hero.attack(attackDirection);
            }
        } else {
            // Không ở cạnh rương, reset target
            System.out.println("Không ở cạnh rương, tìm rương khác...");
            currentTargetChest = null;
        }
    }

    private String getChestAttackDirection(Player player, Obstacle chest) {
        int playerX = player.getX();
        int playerY = player.getY();
        int chestX = chest.getX();
        int chestY = chest.getY();

        // Xác định hướng đánh dựa trên vị trí tương đối
        if (chestX == playerX - 1 && chestY == playerY) return "l"; // Rương ở bên trái
        if (chestX == playerX + 1 && chestY == playerY) return "r"; // Rương ở bên phải
        if (chestX == playerX && chestY == playerY + 1) return "u"; // Rương ở trên
        if (chestX == playerX && chestY == playerY - 1) return "d"; // Rương ở dưới

        return null; // Không ở cạnh rương
    }

    private Obstacle findNearestChest(List<Obstacle> chests, Player player) {
        Obstacle nearestChest = null;
        double minDistance = Double.MAX_VALUE;

        for (Obstacle chest : chests) {
            // Chỉ xem xét những rương còn máu và còn trong bo
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
        // Kiểm tra xem rương có còn tồn tại và còn máu không
        Element element = gameMap.getElementByIndex(chest.getX(), chest.getY());
        if (element != null && element.getType() == chest.getType()) {
            if (element instanceof Obstacle currentChest) {
                return currentChest.getCurrentHp() > 0;
            }
        }
        return false;
    }

    // Kiểm tra rương có nằm trong bo không
    private boolean isChestInSafeZone(Obstacle chest, Player player) {
        GameMap gameMap = hero.getGameMap();
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(chest, safeZone, mapSize);
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

        if (distanceToEnemy <= 1) {
            // Đã ở cạnh kẻ địch
            System.out.println("Đã đến cạnh kẻ địch! Khoảng cách: " + distanceToEnemy);
            // Có thể thêm logic tấn công ở đây
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

        // Support items - chỉ thêm nếu chưa đủ 4 slot
        if (hero.getInventory().getListSupportItem().size() < 4) {
            missingTypes.add("SUPPORT");
        }

        return missingTypes;
    }

    // Kiểm tra item có nằm trong bo không
    private boolean isItemInSafeZone(Element item, GameMap gameMap) {
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(item, safeZone, mapSize);
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
}