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

public class ChestBreaker {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "126022"; // Nhập Game ID vào đây
    private static final String PLAYER_NAME = "ChestBreaker"; // Tên bot
    private static final String SECRET_KEY = "sk-qYZm8FeOSAiZD1yCs-l7Yw:c21__W0fmgR8HVFTHVlAtaW-cEF7o9Ml5xgs1mHYCjMTpMthlG1tvtSucmUyqjyQu3Bg_gUGOY-dYlD-1cAJuA"; // Nhập Secret Key vào đây

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new ChestBreakerListener(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}

class ChestBreakerListener implements Emitter.Listener {
    private final Hero hero;
    private Obstacle currentTargetChest = null;

    public ChestBreakerListener(Hero hero) {
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

            // Tìm rương gần nhất để đập
            handleChestBreaking(gameMap, player, nodesToAvoid);

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng trong call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

    // Kiểm tra rương có nằm trong bo không
    private boolean isChestInSafeZone(Obstacle chest, Player player) {
        GameMap gameMap = hero.getGameMap();
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        return PathUtils.checkInsideSafeArea(chest, safeZone, mapSize);
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