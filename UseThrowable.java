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

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

public class throwabl1 {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "199086"; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "throwabl1"; // Tên bot
    private static final String SECRET_KEY = "sk-H__B6olPTeelwSs3R46pmQ:fHUi994TZvUp1hE6v4J-6tL3oRiTyKirLStD1yjP2jW717K3lk3qNujJIFwEGK8rguQS5sCVPFykXuHtdmD3Tg"; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new SmartBotListener(hero);
        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}

// =================== SMART BOT GỘP 2 CHỨC NĂNG + NÉM THROWABLE ===================
class SmartBotListener implements Emitter.Listener {
    private final Hero hero;
    private Player currentTargetEnemy = null;
    private Element currentTargetItem = null;

    public SmartBotListener(Hero hero) {
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

            List<Node> nodesToAvoid = getNodesToAvoid(gameMap);
//=====================================================================
            // 1. Nếu đã có throwable: chỉ săn địch và ném
            if (hero.getInventory().getThrowable() != null) {
                System.out.println("ĐÃ CÓ THROWABLE - SĂN ĐỊCH VÀ NÉM!");
                boolean didThrow = handleThrowableAttack(gameMap, player);
                if (didThrow) return; // Ném được thì dừng tại đây
                handleEnemyHunting(gameMap, player, nodesToAvoid); // Di chuyển tới địch nếu chưa ném được
                return;
            }
//========================================================CÁI NÀY CHỈ DÀNH CHO BOT CHUYÊN ĐI NÉM BOM
            // 2. Nếu chưa có throwable: chỉ tập trung nhặt throwable
            System.out.println("CHƯA CÓ THROWABLE - ĐI NHẶT ĐỒ!");
            handleItemCollecting(gameMap, player, nodesToAvoid);

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private boolean handleItemCollecting(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        Element nearestItem = findNearestMissingItem(gameMap, player);
        if (nearestItem == null) {
            return false;
        }
        currentTargetItem = nearestItem;
        System.out.println("Tìm thấy item chưa có gần nhất tại (" + nearestItem.getX() + ", " + nearestItem.getY() + ")");
        String pathToItem = PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestItem, true);
        if (pathToItem != null) {
            if (pathToItem.isEmpty()) {
                hero.pickupItem();
            } else {
                System.out.println("Di chuyển đến item: " + pathToItem);
                hero.move(pathToItem);
            }
            return true;
        } else {
            System.out.println("Không tìm thấy đường đi đến item!");
            currentTargetItem = null;
            return false;
        }
    }

    // Tìm item còn thiếu gần nhất
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

        Map<String, Double> typeMinDistances = new HashMap<>();
        Map<String, Element> typeNearestItems = new HashMap<>();

        for (String type : targetTypes) {
            typeMinDistances.put(type, Double.MAX_VALUE);
            typeNearestItems.put(type, null);
        }

        for (Element item : allItems) {
            String typeName = item.getClass().getSimpleName();
            String itemType = null;
            if (typeName.equals("Weapon")) {
                Weapon w = (Weapon) item;
                itemType = w.getType().name();
            } else if (typeName.equals("Armor")) {
                Armor a = (Armor) item;
                itemType = a.getType().name();
            } else if (typeName.equals("SupportItem")) {
                itemType = "SUPPORT";
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


    // ================== Hàm Global =========================
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
        return gameMap.getObstaclesByTag("DESTRUCTIBLE")
                .stream()
                .filter(obs -> {
                    String id = obs.getId();
                    return "CHEST".equals(id) || "DRAGON_EGG".equals(id);
                })
                .collect(Collectors.toList());
    }

    //====================================================================
    private static final Map<String, int[]> THROWABLE_RANGE = new HashMap<>() {{
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

    private static final Map<String, int[]> THROWABLE_EXPLODE_RANGE = new HashMap<>() {{
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

        for (Player enemy : enemies) {
            int ex = enemy.getX(), ey = enemy.getY();

            // Duyệt tất cả điểm ném hợp lệ
            for (int dx = -maxThrow; dx <= maxThrow; dx++) {
                for (int dy = -maxThrow; dy <= maxThrow; dy++) {
                    int dist = Math.abs(dx) + Math.abs(dy);
                    // Chỉ ném trên hàng/cột
                    if (!((dx == 0 || dy == 0) && dist >= minThrow && dist <= maxThrow)) continue;
                    int tx = px + dx, ty = py + dy;

                    // Nếu enemy nằm trong vùng nổ centered tại (tx, ty)
                    if (Math.abs(ex - tx) <= halfWidth && Math.abs(ey - ty) <= halfHeight) {
                        String dir = getDirectionFromDelta(dx, dy);
                        if (dir != null) {
                            hero.throwItem(dir);
                            System.out.println("💣 Throwing " + throwable.getId() + " to " + dir
                                    + " (will explode at " + tx + "," + ty + ")"
                                    + " | Target enemy at " + ex + "," + ey
                                    + " | ExplodeRange: " + Arrays.toString(explodeRange));
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isInThrowRange(Player hero, Player enemy, Weapon throwable) {
        int[] throwRange = getThrowableRange(throwable);          // {min, max} range ném
        int[] explodeRange = getThrowableExplodeRange(throwable); // {height, width}
        String id = throwable.getId();

        int px = hero.getX(), py = hero.getY();
        int ex = enemy.getX(), ey = enemy.getY();
        int minThrow = throwRange[0];
        int maxThrow = throwRange[1];
        int halfHeight = explodeRange[0] / 2;
        int halfWidth  = explodeRange[1] / 2;

        switch (id) {
            case "BANANA": {
                // Chuối: Duyệt tất cả điểm ném hợp lệ trong phạm vi, nếu vùng nổ chứa enemy thì trả về true
                for (int dx = -maxThrow; dx <= maxThrow; dx++) {
                    for (int dy = -maxThrow; dy <= maxThrow; dy++) {
                        int dist = Math.abs(dx) + Math.abs(dy);
                        // Ném hợp lệ (game chỉ cho ném theo hàng/cột):
                        if (!((dx == 0 || dy == 0) && dist >= minThrow && dist <= maxThrow)) continue;
                        int tx = px + dx, ty = py + dy;
                        // Vùng nổ centered tại (tx, ty)
                        if (Math.abs(ex - tx) <= halfWidth && Math.abs(ey - ty) <= halfHeight)
                            return true;
                    }
                }
                return false;
            }
            case "SMOKE": {
                // Bom khói: tương tự, nhưng vùng nổ lớn hơn
                for (int dx = -maxThrow; dx <= maxThrow; dx++) {
                    for (int dy = -maxThrow; dy <= maxThrow; dy++) {
                        int dist = Math.abs(dx) + Math.abs(dy);
                        if (!((dx == 0 || dy == 0) && dist >= minThrow && dist <= maxThrow)) continue;
                        int tx = px + dx, ty = py + dy;
                        if (Math.abs(ex - tx) <= halfWidth && Math.abs(ey - ty) <= halfHeight)
                            return true;
                    }
                }
                return false;
            }
            case "METEORITE_FRAGMENT":
            case "CRYSTAL":
            case "SEED": {
                // Các loại này: tương tự, dùng đúng vùng nổ từng loại
                for (int dx = -maxThrow; dx <= maxThrow; dx++) {
                    for (int dy = -maxThrow; dy <= maxThrow; dy++) {
                        int dist = Math.abs(dx) + Math.abs(dy);
                        if (!((dx == 0 || dy == 0) && dist >= minThrow && dist <= maxThrow)) continue;
                        int tx = px + dx, ty = py + dy;
                        if (Math.abs(ex - tx) <= halfWidth && Math.abs(ey - ty) <= halfHeight)
                            return true;
                    }
                }
                return false;
            }
            // Nếu có loại khác, bổ sung ở đây
            default: {
                // Xử lý mặc định như trên
                for (int dx = -maxThrow; dx <= maxThrow; dx++) {
                    for (int dy = -maxThrow; dy <= maxThrow; dy++) {
                        int dist = Math.abs(dx) + Math.abs(dy);
                        if (!((dx == 0 || dy == 0) && dist >= minThrow && dist <= maxThrow)) continue;
                        int tx = px + dx, ty = py + dy;
                        if (Math.abs(ex - tx) <= halfWidth && Math.abs(ey - ty) <= halfHeight)
                            return true;
                    }
                }
                return false;
            }
        }
    }


    // Xác định hướng ném từ chênh lệch dx, dy
    private String getDirectionFromDelta(int dx, int dy) {
        if (dx == 0 && dy > 0) return "u";
        if (dx == 0 && dy < 0) return "d";
        if (dy == 0 && dx > 0) return "r";
        if (dy == 0 && dx < 0) return "l";
        return null;
    }
}


