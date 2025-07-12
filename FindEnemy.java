import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FindEnemy {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = ""; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "EnemyHunter"; // Tên bot
    private static final String SECRET_KEY = ""; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new EnemyHunterListener(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
        
        System.out.println("Enemy Hunter Bot đã khởi động!");
        System.out.println("Bot sẽ tìm và đi đến vị trí của các kẻ địch còn sống...");
    }
}

class EnemyHunterListener implements Emitter.Listener {
    private final Hero hero;
    private Player currentTargetEnemy = null;

    public EnemyHunterListener(Hero hero) {
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

            // Tìm và đi đến kẻ địch gần nhất
            handleEnemyHunting(gameMap, player, nodesToAvoid);

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
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
