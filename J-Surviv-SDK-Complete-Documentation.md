# J-Surviv SDK - Complete Documentation

<---[Home](https://github.com/fu-js/j-surviv-SDK/wiki/Codefest-2024-‐-SDK)

## Table of Contents
1. [Getting Started](#getting-started)
2. [Hero Class](#hero-class)
3. [Inventory Class](#inventory-class)
4. [PathUtils Class](#pathutils-class)
5. [Entity Classes](#entity-classes)
6. [GameMap Class](#gamemap-class)

---

## Getting Started

Tài liệu này mô tả các bước cài đặt game & chạy SDK để kết nối.

### Bước 1: Set up phần mềm
- Download Intellij Community: [Download Intellij](https://www.jetbrains.com/idea/download/?section=windows)

![Screenshot 2024-08-23 112420](https://github.com/user-attachments/assets/c8d12d4e-854b-41ad-bea3-a067b98ada6d)

- Download file Codefest.jar từ bản release version mới nhất (là một file java chứa các thư viện có sẵn để hỗ trợ cho người chơi code) : [Download Jar](https://github.com/fu-js/j-surviv-SDK/releases)

### Bước 2: Tạo Project
- Mở Intellij vừa dược tải về, chọn New Project

![image](https://github.com/user-attachments/assets/af37d58c-d1b6-4138-a36b-d091d4d0e021)
- Đặt tên cho Project và bấm nút **Create**, lưu ý hãy chọn phiên bản JDK từ 20 trở lên

![image](https://github.com/user-attachments/assets/396d064d-f362-456e-9358-5f7b4a42e2a5)
- Khi này bạn sẽ được giao diện của Project 

![image](https://github.com/user-attachments/assets/5338b094-c5b4-4d92-abb3-fd42019c87d2)

### Bước 3: Import file jar
- Ở góc trên bên trái màn hình chọn **Menu** sau đó chọn mục **Project Structure**

![Screenshot 2024-08-23 115123](https://github.com/user-attachments/assets/33e13e0f-5548-4ce8-95ad-0f4c81ebcd7f)
- Nếu không thấy bạn có thể chọn **Setting** ở góc trên phải màn hình và chọn **Project Structure**

![Screenshot 2024-08-23 120831](https://github.com/user-attachments/assets/ef8447e9-3c47-4842-9b4d-5a122f73a96c)
- Cửa sổ mới sẽ mở ra, sau đó bạn chọn mục **Modules** -> **Libraries**

![image](https://github.com/user-attachments/assets/6c4fc265-df1d-4daa-908a-8aa5b33e8444)
- Chọn dấu **+** và chọn **Java**

![image](https://github.com/user-attachments/assets/c2e1f2a1-ef19-4b08-a519-6dd5a3e480f4)
- Chọn file **CodeFest.jar** bạn vừa tải về trong folder Downloads sau đó chọn **OK**

![image](https://github.com/user-attachments/assets/99ba7701-e3bb-4b18-80b4-4795b0e141dc)
- Sau đó bạn sẽ thêm được file jar vào trong project, bạn chọn **Apply** sau đó **OK**

![image](https://github.com/user-attachments/assets/2c468936-2a40-4d3f-8eda-5022befc8ec6)
- Khi này bạn đã thêm file jar thành công

![image](https://github.com/user-attachments/assets/566519c3-1b23-45a3-9789-0cdb590cccb3)

### Bước 4: Connect server
- Copy đoạn code sau paste vào file Main.java trong Project của bạn

```java
import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;

import java.io.IOException;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "";
    private static final String PLAYER_NAME = "";
    private static final String SECRET_KEY = "*secret-key được BTC cung cấp*";


    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);
                
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}
```

- Sau đó mở game lên, giao diện của game sẽ như này

![image](https://github.com/user-attachments/assets/85efc145-b836-4a1a-8997-a575222ba771)

- Lúc này trên màn hình sẽ có game ID, lúc này là 189926
Sau đó bạn sẽ nhập game id vào code trong file main của bạn

```
private static final String GAME_ID = "189926"; 
```

- Bộ phận kĩ thuật sẽ cung cấp cho các đội chơi SECRET_KEY cho từng con bot, bạn nhập vào phần tương ứng trong code

```
    private static final String SECRET_KEY = "key_duoc_cap";
```

- Sau đó bạn run Project

![image](https://github.com/user-attachments/assets/886b0158-780a-4317-a019-3dd3bcb7eb7f)

- Project sẽ log ra connected to server, lúc này bạn đã thành công connect to server

- Lúc này trong game sẽ hiện lên người chơi với tên tương ứng trong code

![image](https://github.com/user-attachments/assets/d578a27b-0746-4ff4-9a0f-23da7f335162)

### Bước 5: Chạy thử bot

- Để chắc chắn con bot của bạn có thể thực hiện hành động trong game, mình sẽ thử cho nó đi lấy súng
- Sử dụng if-else để nó sẽ là mỗi condition(step) thực hiện một action
- Lúc đấy toàn bộ code của bạn sẽ như này

```java
import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "";
    private static final String PLAYER_NAME = "";
    private static final String SECRET_KEY = "*secret-key được BTC cung cấp*";


    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new MapUpdateListener(hero);

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}

class MapUpdateListener implements Emitter.Listener {
    private final Hero hero;

    public MapUpdateListener(Hero hero) {
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
                System.out.println("Player is dead or data is not available.");
                return;
            }

            List<Node> nodesToAvoid = getNodesToAvoid(gameMap);

            if (hero.getInventory().getGun() == null) {
                handleSearchForGun(gameMap, player, nodesToAvoid);
            }


        } catch (Exception e) {
            System.err.println("Critical error in call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSearchForGun(GameMap gameMap, Player player, List<Node> nodesToAvoid) throws IOException {
        System.out.println("No gun found. Searching for a gun.");
        String pathToGun = findPathToGun(gameMap, nodesToAvoid, player);

        if (pathToGun != null) {
            if (pathToGun.isEmpty()) {
                hero.pickupItem();
            } else {
                hero.move(pathToGun);
            }
        }
    }

    private List<Node> getNodesToAvoid(GameMap gameMap) {
        List<Node> nodes = new ArrayList<>(gameMap.getListIndestructibles());

        nodes.removeAll(gameMap.getObstaclesByTag("CAN_GO_THROUGH"));
        nodes.addAll(gameMap.getOtherPlayerInfo());
        return nodes;
    }

    private String findPathToGun(GameMap gameMap, List<Node> nodesToAvoid, Player player) {
        Weapon nearestGun = getNearestGun(gameMap, player);
        if (nearestGun == null) return null;
        return PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestGun, false);
    }

    private Weapon getNearestGun(GameMap gameMap, Player player) {
        List<Weapon> guns = gameMap.getAllGun();
        Weapon nearestGun = null;
        double minDistance = Double.MAX_VALUE;

        for (Weapon gun : guns) {
            double distance = PathUtils.distance(player, gun);
            if (distance < minDistance) {
                minDistance = distance;
                nearestGun = gun;
            }
        }
        return nearestGun;
    }

}
```

- Do mình vừa sửa code nên mình phải chạy lại code
- Để nhanh hơn thì mình tắt đi bật lại game, sau đó thao tác lại bước 4
- Do cần phải có nhiều hơn 1 người mới vào chơi được nên bạn chọn **Add player** sau đó chọn **Play**

- Như vậy là mình đã hoàn thành xong các hướng dẫn cần thiết để có thể chơi game

---

## Hero Class

- **Package**: `jsclub.codefest.sdk.model`

- **Mô tả**: Class chứa các method sử dụng để tạo người chơi cũng như kết nối với Server thi đấu.

### **1. getGameMap**
```java
GameMap getGameMap()
```
- **Mô tả**: Trả về tất cả thông tin của game.
- **Tham số**: Không có.
- **Trả về**: `gameMap` - Thông tin của game.

### **2. getInventory**
```java
Inventory getInventory()
```
- **Mô tả**: Lấy thông tin về danh sách vũ khí của người dùng
- **Tham số**: Không có
- **Trả về**:
  - `Inventory`: Danh sách vũ khí của người dùng

### **3. move**
```java
void move(String move)
```
- **Mô tả**: Hàm giúp người dùng di chuyển
- **Tham số**: `move`: Một dãy các chỉ dẫn `"lrud..."` (left - right - up - down) giúp người dùng di chuyển
- **Trả về**: Không có

### **4. shoot**
```java
void shoot(String direction)
```
- **Mô tả**: Hàm giúp người dùng vũ khí tầm xa (gun)
- **Tham số**: `direction`: Hướng sẽ bắn (`"l"`/`"r"`/`"u"`/`"d"`)
- **Trả về**: Không có

### **5. attack**
```java
void attack(String direction)
```
- **Mô tả**: Hàm giúp người dùng tấn công cận chiến (melee)
- **Tham số**: `direction`: Hướng đánh (`"l"`/`"r"`/`"u"`/`"d"`)
- **Trả về**: Không có

### **6. throwItem**
```java
void throwItem(String direction)
```
- **Mô tả**: Hàm giúp người chơi ném vũ khí dạng ném (throwable)
- **Tham số**: 
    - `direction`: Hướng ném vũ khí (`"l"`/`"r"`/`"u"`/`"d"`)
- **Trả về**: Không có

### **7. useSpecial**
```java
void useSpecial(String direction)
```
- **Mô tả**: Hàm giúp người chơi dùng vũ khí dạng đặc biệt (special)
- **Tham số**: `direction`: Hướng dùng vũ khí (`"l"`/`"r"`/`"u"`/`"d"`)
- **Trả về**: Không có

### **8. pickupItem**
```java
void pickupItem()
```
- **Mô tả**: Hàm giúp người chơi nhặt đồ (nếu vị trí đồ trùng vị trí người chơi)
- **Tham số**: Không có
- **Trả về**: Không có

### **9. useItem**
```java
void useItem(String itemId)
```
- **Mô tả**: Hàm giúp người chơi sử dụng vật phẩm trị thương (healingItem)
- **Tham số**: `itemId`: id của item
- **Trả về**: Không có

### **10. revokeItem**
```java
void revokeItem(String itemId)
```
- **Mô tả**: Hàm giúp người chơi bỏ đồ ra khỏi túi
- **Tham số**: `itemId`: id của item
- **Trả về**: Không có

### **11. getEffects**
```java
List<Effect> getEffects()
```
- **Mô tả**: Lấy thông tin về danh sách hiệu ứng áp dụng lên người dùng
- **Tham số**: Không có
- **Trả về**:
  - `List<Effect>`: Danh sách hiệu ứng của người dùng

---

## Inventory Class

- **Package**: `jsclub.codefest.sdk.model`

- **Mô tả**: Class chứa các method sử dụng để người chơi lấy, cập nhật thông tin của map.

### **1. Constructor**
```java
Inventory()
```
- **Mô tả**: Inventory(): Khởi tạo kho đồ với vũ khí cận chiến mặc định là `HAND`.
- **Tham số**: không có
- **Trả về**: không có

```java
Inventory(List<ItemData> items)
```
- **Mô tả**: Inventory():  Khởi tạo kho đồ từ danh sách vật phẩm ban đầu. Tự động phân loại và thêm các vật phẩm tương ứng.
- **Tham số**: `items` – danh sách các vật phẩm kiểu `ItemData`
- **Trả về**: không có

### **2. getGun**
```java
Weapon getGun()
```
- **Mô tả**: Lấy thông tin vũ khí súng hiện tại của người chơi.
- **Tham số**: không có
- **Trả về**: không có

### **3. getMelee**
```java
Weapon getMelee()
```
- **Mô tả**: Lấy thông tin vũ khí cận chiến hiện tại.
- **Tham số**: không có
- **Trả về**: không có

### **4. getThrowable**
```java
Weapon getThrowable()
```
- **Mô tả**: Lấy thông tin vũ khí dạng ném `throwable` trong kho đồ.
- **Tham số**: không có
- **Trả về**: không có

### **5. getSpecial**
```java
Weapon getSpecial()
```
- **Mô tả**: Lấy thông tin vũ khí dạng đặc biệt `special` trong kho đồ.
- **Tham số**: không có
- **Trả về**: không có

### **6. getListSupportItem**
```java
List<SupportItem> getListSupportItem()
```
- **Mô tả**: Lấy danh sách thông tin vật phẩm trị thương `healingItem` trong kho đồ.
- **Tham số**: không có
- **Trả về**: không có

### **7. getHelmet**
```java
Armor getHelmet()
```
- **Mô tả**: Lấy thông tin trang bị `helmet` trong kho đồ.
- **Tham số**: không có
- **Trả về**: không có

### **8. getArmor**
```java
Armor getArmor()
```
- **Mô tả**: Lấy thông tin trang bị `armor` trong kho đồ.
- **Tham số**: không có
- **Trả về**: không có

---

## PathUtils Class

- **Package**: `jsclub.codefest.sdk.algorithm`

- **Mô tả**: Class chứa các method hỗ trợ người chơi.

### **1. getShortestPath**
```java
String getShortestPath(GameMap gameMap, List<Node> restrictedNodes, Node current, Node target, boolean skipDarkArea)
```
- **Mô tả**: Hàm trả về 1 dãy chỉ dẫn là đường đi ngắn nhất để đi từ node `current` đến node `target`
- **Tham số**: 
    + `gameMap`: Thông tin về game mà người dùng cần truyền vào
    + `restrictedNodes`: Danh sách những node mà người dùng cần truyền vào để giúp bot né. (Bình xăng, trap...)
    + `current`: Vị trí hiện tại của người dùng
    + `target`: Vị trí của người dùng muốn đến
    + `skipDarkArea`: Nếu truyền vào true, bot sẽ không đi ra khỏi bo, và ngược lại.
- **Trả về**: 
    + `String`: Một dãy các chỉ dẫn liên tiếp để đi từ vị trí `current` đến vị trí `target`
    + Nếu người dùng hiện tại ở vị trí (x, y), sẽ có 4 hướng:
         + `l`: bot sẽ rẽ trái `(x - 1, y)`
         + `r`: bot sẽ rẽ phải `(x + 1, y)`
         + `u`: bot sẽ đi lên trên `(x, y + 1)`
         + `d`: bot sẽ đi xuống dưới `(x, y - 1)`

### **2. checkInsideSafeArea**
```java
boolean checkInsideSafeArea(Node x, int safeZone, int mapSize)
```
- **Mô tả**: Hàm kiểm tra xem 1 vị trí node x có ở trong vùng an toàn hay không
- **Tham số**:
    + `x`: vị trí mà bạn muốn kiểm tra
    + `safeZone`: Kích thước của vùng an toàn.
    + `mapSize`: Kích thước của bản đồ
- **Trả về**:
    + `true`: Nếu node `x` ở trong vùng an toàn.
    + `false`: Nếu node `x` ở ngoài vùng an toàn.

### **3. distance**
```java
int distance(Node x, Node y)
```
- **Mô tả**: Hàm tính khoảng cách Manhattan giữa 2 node trên bản đồ, dùng để xác định tương đối về khoảng cách phải di chuyển(distance = |x1-x2| + |y1-y2|)
- **Tham số**:
    + `x`: vị trí node đầu tiên
    + `y`: vị trí node thứ hai.
- **Trả về**:
    + `int`: Khoảng cách Manhattan giữa 2 node x và y.

---

## Entity Classes

### **Inventory**
**Mô tả**: Kho đồ của người chơi, chứa thông tin các vũ khí như súng, cận chiến, vũ khí ném, vũ khí đặc biệt; thông tin trang bị và vật phẩm trị thương.

#### **1. getGun**
```java
Weapon getGun()
```
- **Mô tả**: Lấy thông tin vũ khí tầm xa trong kho đồ.

#### **2. getMelee**
```java
Weapon getMelee()
```
- **Mô tả**: Lấy thông tin vũ khí cận chiến trong kho đồ.

#### **3. getThrowable**
```java
Weapon getThrowable()
```
- **Mô tả**: Lấy thông tin vũ khí ném trong kho đồ.

#### **4. getSpecial**
```java
Weapon getSpecial()
```
- **Mô tả**: Lấy thông tin vũ khí đặc biệt trong kho đồ.

#### **5. getListSupportItem**
```java
List<SupportItem> getListSupportItem()
```
- **Mô tả**: Lấy danh sách thông tin vật phẩm hỗ trợ trong kho đồ.

#### **6. getArmor**
```java
Armor getArmor()
```
- **Mô tả**: Lấy danh sách thông tin giáp trong kho đồ.

#### **7. getHelmet**
```java
Armor getHelmet()
```
- **Mô tả**: Lấy danh sách thông tin mũ trong kho đồ.

### **Element**
**Mô tả**: Bao gồm các thành phần trong trò chơi, bao gồm `player`, `npc`, `weapon`, `obstacle`,...

#### **1. getId**
```java
String getId()
```
- **Mô tả**: Lấy id của thành phần.

#### **2. getType**
```java
ElementType getType()
```
- **Mô tả**: Lấy phân loại của thành phần.

#### **3. getX / getY**
```java
int getX()
int getY()
```
- **Mô tả**: Lấy vị trí x, y của thành phần.

### **Weapon**
**Mô tả**: Thông tin vũ khí, có 4 loại vũ khí là `gun`, `melee`, `throwable`, `special`

#### **1. getPickupPoints**
```java
int getPickupPoints()
```
- **Mô tả**: Lấy ra điểm số nhận được khi nhặt vũ khí.

#### **2. getHitPoints**
```java
int getHitPoins()
```
- **Mô tả**: Lấy ra số điểm nhận được khi vũ khí đánh trúng 1 mục tiêu.

#### **3. getCooldown**
```java
double getCooldown()
```
- **Mô tả**: Lấy ra thời gian hồi giữa mỗi lần sử dụng.

#### **4. getDamage**
```java
int getDamage()
```
- **Mô tả**: Lấy ra sát thương của vũ khí lên người chơi khác.

#### **5. getRange**
```java
int[] getRange()
```
- **Mô tả**: Lấy ra một mảng 2 phần tử mô tả phạm vi sử dụng vũ khí. Có dạng {a, b}.
- **Ví dụ**: range {3, 1}.
![image](https://github.com/user-attachments/assets/8297cd2d-e0cb-4523-81f8-c6056957b1e5)

#### **6. getExplodeRange**
```java
int getExplodeRange()
```
- **Mô tả**: Lấy ra phạm vi nổ của vũ khí (chỉ áp dụng vũ khí ném).

#### **7. getSpeed**
```java
int getSpeed()
```
- **Mô tả**: Lấy ra tốc độ đạn(cell/s). (áp dụng với súng, vũ khí ném, vũ khí đặc biệt)

#### **8. getUseCount**
```java
int getUseCount()
```
- **Mô tả**: Lấy ra số lần sử dụng tối đa của vũ khí.

### **Bullet**
#### **1. getDamage**
```java
float getDamage()
```
- **Mô tả**: Lấy ra sát thương của đạn.

#### **2. getSpeed**
```java
int getSpeed()
```
- **Mô tả**: Lấy ra tốc độ đạn bắn (cell/step).

#### **3. getDestinationX**
```java
int getDestinationX()
```
- **Mô tả**: Lấy ra tọa độ x ước tính mà đạn sẽ biến mất.

#### **4. getDestinationY**
```java
int getDestinationY()
```
- **Mô tả**: Lấy ra tọa độ y ước tính mà đạn sẽ biến mất.

### **Player**
**Mô tả**: Thông tin người chơi.

#### **1. getID**
```java
String getID()
```
- **Mô tả**: Lấy ra ID của người chơi.

#### **2. getScore**
```java
int getScore()
```
- **Mô tả**: Lấy ra số điểm hiện tại của người chơi.

#### **3. getHealth**
```java
Float getHealth()
```
- **Mô tả**: Lấy ra số máu hiện tại của người chơi.

### **Enemy**
#### **1. getDamage**
```java
int getDamage()
```
- **Mô tả**: Lấy ra sát thương của kẻ thù.

### **Ally**
#### **1. getHealingHP**
```java
int getHealingHP()
```
- **Mô tả**: Lấy ra lượng HP hồi phục cho Hero của đồng minh.

### **Armor**
#### **1. getHealthPoint**
```java
double getHealthPoint()
```
- **Mô tả**: Lấy ra số HP của trang bị.

#### **2. getDamageReduce**
```java
int getDamageReduce()
```
- **Mô tả**: Lấy ra phần trăm giảm sát thương của trang bị.

### **SupportItem**
#### **1. getHealingHP**
```java
int getHealingHP()
```
- **Mô tả**: Lấy ra số máu được hồi trong 1 khoảng thời gian.

#### **2. getUsageTime**
```java
double getUsageTime()
```
- **Mô tả**: Lấy ra thời gian cần để dùng vật phẩm.

#### **3. getPoint**
```java
int getPoint()
```
- **Mô tả**: Lấy ra số điểm nhận được khi sử dụng vật phẩm.

### **Obstacle**
#### **1. getHp**
```java
int getHp()
```
- **Mô tả**: Lấy ra máu ban đầu của vật cản.

#### **2. getCurrentHp**
```java
int getCurrentHp()
```
- **Mô tả**: Lấy ra máu hiện tại của vật cản.

#### **3. getTags**
```java
List<ObstacleTag> getTags()
```
- **Mô tả**: Lấy ra danh sách các thẻ thuộc tính của Obstacle(chi tiết các thẻ thuộc tính xem ở hàm `getObstaclesByTag` trong gameMap).

### **Effect**
#### **1. getDuration**
```java
int getDuration()
```
- **Mô tả**: Lấy ra thời gian hiệu lực của Effect.

#### **2. getAffectedAt**
```java
int getAffectedAt()
```
- **Mô tả**: Lấy ra step mà effect được áp dụng.

#### **3. getEstimatedEndAt**
```java
int getEstimatedEndAt()
```
- **Mô tả**: Lấy ra step mà effect được xóa bỏ.

---

## GameMap Class

- **Package**: `jsclub.codefest.sdk.model`

- **Mô tả**: Class chứa các method sử dụng để người chơi lấy, cập nhật thông tin của map.

### **1. getElementByIndex**
```java
Element getElementByIndex(int x, int y)
```
- **Mô tả**: Lấy Element ở vị trí x, y được truyền vào.
- **Tham số**:
  - `x`: vị trí x
  - `y`: vị trí y
- **Trả về**: 
  - `Element`: Element

### **2. getMapSize**
```java
int getMapSize()
```
- **Mô tả**: Lấy độ rộng của map
- **Tham số**: Không có
- **Trả về**: 
  - `int`: độ rộng của map

### **3. getSafeZone**
```java
int getSafeZone()
```
- **Mô tả**: Lấy bán kính vùng an toàn (tính từ tâm của map)
- **Tham số**: Không có
- **Trả về**: 
  - `int`: bán kính vùng an toàn của map

### **4. getListWeapons**
```java
List<Weapon> getListWeapons()
```
- **Mô tả**: Lấy thông tin tất cả vũ khí trên bản đồ.
- **Tham số**: Không có
- **Trả về**: 
  - `List<Weapon>`: danh sách vũ khí

### **5. getAllGun / getAllMelee / getAllThrowable/ getAllSpecial**
```java
List<Weapon> getAllGun()
List<Weapon> getAllMelee()
List<Weapon> getAllThrowable()
List<Weapon> getAllSpecial()
```
- **Mô tả**: Lấy thông tin tất cả vũ khí loại `Gun` / `Melee` / `Throwable` / `Special` tương ứng trên bản đồ.
- **Tham số**: Không có
- **Trả về**: 
  - `List<Weapon>`: danh sách vũ khí loại `Gun` / `Melee` / `Throwable` / `Special` tương ứng

### **6. getListEnemies**
```java
List<Enemy> getListEnemies()
```
- **Mô tả**: Lấy thông tin tất cả `Enemy`(kẻ thù gây sát thương) trên bản đồ 
- **Tham số**: Không có
- **Trả về**: 
  - `List<Enemy>`: danh sách kẻ thù

### **7. getListAllies**
```java
List<Ally> getListAllies()
```
- **Mô tả**: Lấy thông tin tất cả `Ally`(NPC đồng minh) trên bản đồ 
- **Tham số**: Không có
- **Trả về**: 
  - `List<Ally>`: danh sách đồng minh

### **8. getListObstacles**
```java
List<Obstacle> getListObstacles()
```
- **Mô tả**: Lấy thông tin tất cả vật cản trên bản đồ.
- **Tham số**: Không có
- **Trả về**: 
  - `List<Obstacle>`: danh sách obstacle

### **9. getListIndestructibles**
```java
List<Obstacle> getListIndestructibles()
```
- **Mô tả**: Lấy thông tin tất cả vật cản theo loại `Indestructible` trên bản đồ
- **Tham số**: Không có
- **Trả về**: 
  - `List<Obstacle>`: danh sách obstacle

### **10. getObstaclesByTag**
```java
List<Obstacle> getObstaclesByTag(String tag)
```
- **Mô tả**: Lấy thông tin về tất cả Obstacle trên bản đồ theo mỗi loại thuộc tính của nó.
- **Tham số**: `tag`:  Thẻ thuộc tính: 
  - `"DESTRUCTIBLE"`
  - `"TRAP"`
  - `"CAN_GO_THROUGH"`
  - `"CAN_SHOOT_THROUGH"`
  - `"PULLABLE_ROPE"`
  - `"HERO_HIT_BY_BAT_WILL_BE_STUNNED"`.
- **Trả về**: 
  - `List<Obstacle>`: danh sách obstacle theo thuộc tính tương ứng.

### **11. getListSupportItems**
```java
List<SupportItem> getListSupportItems()
```
- **Mô tả**: Lấy thông tin tất cả vật phẩm hỗ trợ trên bản đồ.
- **Tham số**: Không có
- **Trả về**: 
  - `List<SupportItem>`: danh sách vật phẩm hỗ trợ.

### **12. getListArmors**
```java
List<Armor> getListArmors()
```
- **Mô tả**: Lấy thông tin tất cả trang bị trên bản đồ (cả `armor` và `helmet`).
- **Tham số**: Không có
- **Trả về**: 
  - `List<Armor>`: danh sách trang bị

### **13. getListBullets**
```java
List<Bullet> getListBullets()
```
- **Mô tả**: Lấy thông tin tất cả đạn trên bản đồ.
- **Tham số**: Không có
- **Trả về**: 
  - `List<Bullet>`: danh sách đạn 

### **14. getOtherPlayerInfo**
```java
List<Player> getOtherPlayerInfo()
```
- **Mô tả**: Lấy thông tin tất cả các chơi khác(trừ mình) trên bản đồ.
- **Tham số**: Không có
- **Trả về**: 
  - `List<Player>`: danh sách người chơi 

### **15. getCurrentPlayer**
```java
Player getCurrentPlayer()
```
- **Mô tả**: Lấy thông tin của người chơi mình điều khiển.
- **Tham số**: Không có
- **Trả về**: 
  - `Player`: thông tin người chơi 

### **16. getStepNumber**
```java
int getStepNumber()
```
- **Mô tả**: Lấy ra step hiện tại của trò chơi(tính từ khi bắt đầu game).

---

*Tài liệu này được tổng hợp từ tất cả các file Markdown trong dự án J-Surviv SDK.* 