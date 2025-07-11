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

public class ItemCollector {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = ""; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "ItemCollector"; // Tên bot
    private static final String SECRET_KEY = ""; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new ItemCollectorListener(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}

class ItemCollectorListener implements Emitter.Listener {
    private final Hero hero;

    public ItemCollectorListener(Hero hero) {
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
        
        // Kiểm tra gun
        if (hero.getInventory().getGun() == null) {
            missingTypes.add("GUN");
        }
        
        // Kiểm tra melee (chỉ thiếu nếu chưa có hoặc chỉ có HAND)
        Weapon meleeWeapon = hero.getInventory().getMelee();
        if (meleeWeapon == null || "HAND".equals(meleeWeapon.getId())) {
            missingTypes.add("MELEE");
        }
        
        // Kiểm tra throwable
        if (hero.getInventory().getThrowable() == null) {
            missingTypes.add("THROWABLE");
        }
        
        // Kiểm tra special
        if (hero.getInventory().getSpecial() == null) {
            missingTypes.add("SPECIAL");
        }
        
        // Kiểm tra armor
        if (hero.getInventory().getArmor() == null) {
            missingTypes.add("ARMOR");
        }
        
        // Kiểm tra helmet
        if (hero.getInventory().getHelmet() == null) {
            missingTypes.add("HELMET");
        }
        
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