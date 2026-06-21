# 中國象棋 AI 對戰技術規格書

本文件定義在 `ChineseChess` 專案中新增「人機對戰」功能的技術實作路徑。

## 第 1 章：AI 引擎設計

### 1.1 演算法
使用 **Minimax 演算法** 搭配 **Alpha-Beta Pruning ( $\alpha\text{-}\beta$ 剪枝)** 以減少搜索空間。
- **搜索深度 (Depth)**：
  - 簡單 (Easy)：$d=2$ (思考上限 10s)
  - 普通 (Normal)：$d=4$ (思考上限 20s)
  - 困難 (Hard)：$d=6$ (思考上限 30s)

### 1.2 評估函數 $\text{Eval}(s)$
總分 $\text{TotalScore} = \sum (\text{棋子價值} \times \text{位置價值})$

#### 1.2.1 棋子基本價值表 (Material Value)
| 棋子 | 價值 | 備註 |
| :--- | :--- | :--- |
| 車 (Rook) | 1200 | 最強進攻 |
| 炮 (Cannon) | 450 | 遠程打擊 |
| 馬 (Horse) | 400 | 靈活跳躍 |
| 兵/卒 (Pawn) | 15 / 30 | 未過河 / 已過河 |
| 相/象 (Elephant) | 20 | 防禦核心 |
| 仕/士 (Advisor) | 20 | 將帥護衛 |

#### 1.2.2 9×10 位置價值表 (Position Value)
每種棋子在不同格子的權重。以下為簡化示例（實作時將擴展至 10x9 陣列）。
- **車**：傾向於開闊線（中心線優先）。
- **馬**：傾向於中心位置（增加跳躍選擇）。
- **炮**：傾向於後方或對手陣前。
- **兵/卒**：越接近對手底線價值越高。

### 1.3 總分計算公式
$\text{Score} = (\text{MyMaterial} - \text{OpponentMaterial}) + (\text{MyPosValue} - \text{OpponentPosValue}) + \text{Bonus}$
- **Bonus**：
  - 將軍 (Check)：$+100$
  - 絕殺 (Checkmate)：$+\infty$

---

## 第 2 章：Room.java 變更

新增以下欄位以區分人機房間：
```java
private boolean isAI = false;
private String aiDifficulty = "normal"; // easy, normal, hard

// Getters and Setters
public boolean isAI() { return isAI; }
public void setAI(boolean AI) { isAI = AI; }
public String getAiDifficulty() { return aiDifficulty; }
public void setAiDifficulty(String aiDifficulty) { this.aiDifficulty = aiDifficulty; }
```

---

## 第 3 章：ChessAIService.java 設計

### 3.1 類別定義
`@Service` 類別，負責計算最佳走法。

### 3.2 核心方法簽章
```java
@Service
public class ChessAIService {
    // 主入口：獲取 AI 的最佳走法
    public Move getBestMove(String roomId, int aiColor, String difficulty) {
        int depth = getDepthByDifficulty(difficulty);
        int[][] board = chessGameService.getBoard(roomId);
        
        // 初始化 alpha-beta
        double bestValue = Double.NEGATIVE_INFINITY;
        Move bestMove = null;
        
        List<Move> legalMoves = getAllLegalMoves(board, aiColor);
        for (Move move : legalMoves) {
            simulateMove(board, move);
            double boardValue = minimax(board, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
            undoMove(board, move);
            
            if (boardValue > bestValue) {
                bestValue = boardValue;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private double minimax(int[][] board, int depth, double alpha, double beta, boolean isMaximizing) {
        if (depth == 0 || isGameOver(board)) {
            return evaluateBoard(board);
        }
        // ... Alpha-Beta 遞迴邏輯 ...
    }
}
```

---

## 第 4 章：RoomService.java 新增方法

### 4.1 建立 AI 房間
```java
public Room createAIRoom(String username, int playerColor, String difficulty) {
    Room room = createRoom(username);
    room.setAI(true);
    room.setAiDifficulty(difficulty);
    
    // 設定玩家顏色 (玩家選紅-1, AI則-1; 玩家選黑-1, AI則1)
    // 注意：現有 joinRoom 是先到先得，這裡需要強制覆蓋
    room.setPlayers(new ArrayList<>(Arrays.asList(username, "AI")));
    
    // 強制設定玩家顏色 de-facto: index 0 = Red, index 1 = Black
    // 如果玩家選擇執黑，則將 AI 放在 index 0
    if (playerColor == -1) {
        room.setPlayers(new ArrayList<>(Arrays.asList("AI", username)));
    }
    
    room.setStatus("PLAYING");
    return room;
}
```

### 4.2 判斷是否為 AI 房間
```java
public boolean isAIRoom(String roomId) {
    Room room = rooms.get(roomId);
    return room != null && room.isAI();
}
```

---

## 第 5 章：AdminController.java 新增 API

新增端點用於 AI 房間創建：
```java
@PostMapping("/rooms/create-ai")
public ResponseEntity<?> createAIRoom(
        @RequestParam String username, 
        @RequestParam int color, 
        @RequestParam String difficulty) {
    try {
        Room room = roomService.createAIRoom(username, color, difficulty);
        return ResponseEntity.ok(room);
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("message", "AI 房間建立失敗"));
    }
}
```

---

## 第 6 章：GameWebSocketHandler.java 變更

在 `handleMove` 方法中，當玩家走完後：

```java
// 1. 執行玩家走子
chessGameService.movePiece(roomId, startRow, startCol, endRow, endCol);
chessGameService.switchTurn(roomId);

// 2. 檢查是否為 AI 房間 且 輪到 AI 走子
if (roomService.isAIRoom(roomId)) {
    int currentTurn = chessGameService.getCurrentTurn(roomId);
    
    // 模擬 AI 思考延遲
    scheduler.schedule(() -> {
        // 呼叫 AI 引擎
        Move aiMove = chessAIService.getBestMove(roomId, currentTurn, room.getAiDifficulty());
        
        if (aiMove != null) {
            chessGameService.movePiece(roomId, aiMove.startRow(), aiMove.startCol(), aiMove.endRow(), aiMove.endCol());
            chessGameService.switchTurn(roomId);
            
            // 廣播 AI 走子
            broadcastMoveToRoom(roomId, aiMove, "AI");
        }
    }, ThreadLocalRandom.current().nextInt(500, 1001), TimeUnit.MILLISECONDS);
}
```

---

## 第 7 章：前端 lobby.html 變更

### 7.1 HTML 新增挑戰區塊
```html
<div class="ai-challenge-section">
    <h3>🤖 挑戰 AI</h3>
    <div class="ai-controls">
        <select id="ai-color">
            <option value="1">執紅 (先手)</option>
            <option value="-1">執黑 (後手)</option>
        </select>
        <select id="ai-difficulty">
            <option value="easy">簡單</option>
            <option value="normal" selected>普通</option>
            <option value="hard">困難</option>
        </select>
        <button class="btn-ai" onclick="challengeAI()">開始挑戰</button>
    </div>
</div>
```

### 7.2 JS 呼叫邏輯
```javascript
async function challengeAI() {
    const username = localStorage.getItem('username');
    const color = document.getElementById('ai-color').value;
    const difficulty = document.getElementById('ai-difficulty').value;
    
    const resp = await fetch(`/api/rooms/create-ai?username=${username}&color=${color}&difficulty=${difficulty}`, { method: 'POST' });
    if (resp.ok) {
        const room = await resp.json();
        window.location.href = `/ChineseChess/game?roomId=${room.roomId}&isAI=true`;
    }
}
```

---

## 第 8 章：實作檢查清單

1. [ ] `model/Room.java` $\rightarrow$ 新增 `isAI`, `aiDifficulty` 欄位
2. [ ] `service/ChessAIService.java` $\rightarrow$ 實作 Minimax + Alpha-Beta + 評估函數
3. [ ] `service/RoomService.java` $\rightarrow$ 實作 `createAIRoom`, `isAIRoom`
4. [ ] `controller/AdminController.java` $\rightarrow$ 新增 `/api/rooms/create-ai`
5. [ ] `controller/GameWebSocketHandler.java` $\rightarrow$ 整合 AI 走子觸發邏輯與隨機延遲
6. [ ] `templates/lobby.html` $\rightarrow$ 新增 AI 挑戰介面
7. [ ] `templates/game.html` $\rightarrow$ 處理 `isAI` 參數，修改對手顯示文字
8. [ ] 測試簡單 $\rightarrow$ 普通 $\rightarrow$ 困難 難度的走子差異
9. [ ] 測試執紅/執黑 顏色分配是否正確
