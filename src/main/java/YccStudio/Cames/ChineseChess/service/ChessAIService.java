package YccStudio.Cames.ChineseChess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * ChessAIService - 中國象棋 AI 核心服務
 * 
 * 本服務實作了一個基於 Minimax 演算法與 Alpha-Beta 剪枝的 AI 引擎。
 * AI 會模擬未來多步的走法，並透過評估函數計算出目前最優的走棋位置。
 */
@Service
public class ChessAIService {

    @Autowired
    private ChessGameService chessGameService;

    /**
     * 走法紀錄 (Move record)
     * 起點座標 (startRow, startCol) 與 終點座標 (endRow, endCol)
     */
    public record Move(int startRow, int startCol, int endRow, int endCol) {}

    /**
     * 棋子基礎價值表 (Material Values)
     * 數值越高代表該棋子在盤面上的權重越大。
     */
    private static final Map<Integer, Integer> PIECE_VALUES = Map.of(
        1, 1200,  // 車 (Rook): 強力遠程進攻
        2, 400,   // 馬 (Horse): 靈活跳躍
        3, 20,    // 相 (Elephant): 防禦核心
        4, 20,    // 仕 (Advisor): 將帥守衛
        5, 20000, // 將/帥 (General): 遊戲核心，失去即失敗
        6, 450,   // 炮 (Cannon): 特殊跳躍攻擊
        7, 15     // 兵/卒 (Soldier): 基礎單位
    );

    // 位置價值表：針對特定棋子在特定位置的權重加成
    private final int[][] generalPosValue = new int[10][9]; // 將帥位置權重
    private final int[][] rookPosValue = new int[10][9];    // 車位置權重

    public ChessAIService() {
        // 初始化位置權重
        for(int i=0; i<10; i++) {
            for(int j=0; j<9; j++) {
                // 車在邊線或特定路徑上更有威脅
                rookPosValue[i][j] = (j == 0 || j == 8) ? 10 : 5;
                // 將帥在中心位置 (3-5列) 較為穩健
                generalPosValue[i][j] = (j >= 3 && j <= 5) ? 10 : 2;
            }
        }
    }

    /**
     * 獲取 AI 的最佳走法
     * 
     * @param roomId     房間 ID
     * @param aiColor    AI 扮演的顏色 (1 為紅方, -1 為黑方)
     * @param difficulty 難度設定 (easy, normal, hard)
     * @return 最佳走法 Move，若無合法走法則回傳 null
     */
    public Move getBestMove(String roomId, int aiColor, String difficulty) {
        // 1. 根據難度決定搜尋深度 (Depth)
        int depth = getDepthByDifficulty(difficulty);
        // 2. 獲取當前棋盤狀態
        int[][] board = chessGameService.getBoard(roomId);
        
        // 3. 找出所有 AI 目前可以走的所有合法步驟
        List<Move> legalMoves = getAllLegalMoves(board, aiColor);
        if (legalMoves.isEmpty()) return null;

        Move bestMove = null;
        // 初始化最佳分值：紅方尋找最大值，黑方尋找最小值
        double bestValue = (aiColor == 1) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        // 遍歷所有可能的走法，模擬每一步後的盤面分值
        for (Move move : legalMoves) {
            int[][] nextBoard = cloneBoard(board); // 複製棋盤避免污染
            applyMove(nextBoard, move);            // 模擬走子
            
            // 使用 Minimax 遞迴計算該走法在未來 depth 步後的最終預期分值
            // 如果 AI 是紅方 (Maximizing)，則下一層遞迴是黑方 (Minimizing)
            double boardValue = minimax(nextBoard, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, aiColor == 1 ? false : true);
            
            if (aiColor == 1) { // AI 為紅方 $\rightarrow$ 追求最高分
                if (boardValue > bestValue) {
                    bestValue = boardValue;
                    bestMove = move;
                }
            } else { // AI 為黑方 $\rightarrow$ 追求最低分 (負值最深)
                if (boardValue < bestValue) {
                    bestValue = boardValue;
                    bestMove = move;
                }
            }
        }
        return bestMove;
    }

    /**
     * 難度轉深度映射
     */
    private int getDepthByDifficulty(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy" -> 2;   // 搜尋 2 層，反應較慢，易被擊敗
            case "hard" -> 4;   // 搜尋 4 層，能預見較遠的陷阱
            default -> 3;       // 普通難度
        };
    }

    /**
     * Minimax 遞迴核心 (含 Alpha-Beta 剪枝)
     * 
     * @param board       當前模擬棋盤
     * @param depth       剩餘遞迴深度
     * @param alpha       最佳保底值 (Lower bound)
     * @param beta        最差限制值 (Upper bound)
     * @param isMaximizing 當前輪到紅方 (True) 還是黑方 (False)
     * @return 該狀態下的棋盤評分
     */
    private double minimax(int[][] board, int depth, double alpha, double beta, boolean isMaximizing) {
        // 基底情況：到達最大深度或遊戲結束 $\rightarrow$ 進行靜態評估
        if (depth == 0) {
            return evaluateBoard(board);
        }

        int turnColor = isMaximizing ? 1 : -1;
        List<Move> moves = getAllLegalMoves(board, turnColor);
        
        // 如果一方無路可走，直接評分並加權
        if (moves.isEmpty()) return evaluateBoard(board) * 10;

        if (isMaximizing) {
            // 紅方輪到：嘗試最大化分值 (Maximize)
            double maxEval = Double.NEGATIVE_INFINITY;
            for (Move move : moves) {
                int[][] nextBoard = cloneBoard(board);
                applyMove(nextBoard, move);
                double eval = minimax(nextBoard, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval); // 更新 Alpha
                if (beta <= alpha) break;      // ✂️ Alpha-Beta 剪枝：此分支不可能比已知路徑更好
            }
            return maxEval;
        } else {
            // 黑方輪到：嘗試最小化分值 (Minimize)
            double minEval = Double.POSITIVE_INFINITY;
            for (Move move : moves) {
                int[][] nextBoard = cloneBoard(board);
                applyMove(nextBoard, move);
                double eval = minimax(nextBoard, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);   // 更新 Beta
                if (beta <= alpha) break;       // ✂️ Alpha-Beta 剪枝
            }
            return minEval;
        }
    }

    /**
     * 評估函數 (Evaluation Function)
     * 根據棋盤上所有棋子的價值與位置計算總分。
     * 紅方得分 $\rightarrow$ 正值增加；黑方得分 $\rightarrow$ 負值增加。
     */
    private double evaluateBoard(int[][] board) {
        double totalScore = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                
                int absPiece = Math.abs(piece);
                int value = PIECE_VALUES.getOrDefault(absPiece, 0);
                
                // 兵卒過河特殊加分：越過河界後戰鬥力大幅提升
                if (absPiece == 7) {
                    if ((piece > 0 && r <= 4) || (piece < 0 && r >= 5)) {
                        value = 30;
                    }
                }

                // 基礎位置加分：根據棋子類別增加位置權重
                if (absPiece == 1) value += rookPosValue[r][c];
                if (absPiece == 5) value += generalPosValue[r][c];

                if (piece > 0) totalScore += value; // 紅方價值增加總分
                else totalScore -= value;           // 黑方價值減少總分 (使總分變負)
            }
        }
        return totalScore;
    }

    /**
     * 遍歷棋盤，尋找所有該顏色可執行的合法走法
     */
    private List<Move> getAllLegalMoves(int[][] board, int playerColor) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                // 檢查是否為目前 AI 的棋子
                if (piece != 0 && ((playerColor == 1 && piece > 0) || (playerColor == -1 && piece < 0))) {
                    for (int tr = 0; tr < 10; tr++) {
                        for (int tc = 0; tc < 9; tc++) {
                            if (isValidMoveSimulated(board, r, c, tr, tc, playerColor)) {
                                moves.add(new Move(r, c, tr, tc));
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    /**
     * 模擬走法驗證 (Simulated Validation)
     * 為了提高 AI 搜尋速度，此方法不依賴 roomId 或 DB，直接操作 int[][] 棋盤。
     */
    private boolean isValidMoveSimulated(int[][] board, int sr, int sc, int er, int ec, int color) {
        if (er < 0 || er >= 10 || ec < 0 || ec >= 9) return false;
        int piece = board[sr][sc];
        if (piece == 0) return false;
        if ((color == 1 && piece < 0) || (color == -1 && piece > 0)) return false;
        
        int target = board[er][ec];
        // 不能吃自己的棋子
        if (target != 0 && ((color == 1 && target > 0) || (color == -1 && target < 0))) return false;

        int absPiece = Math.abs(piece);
        int dr = er - sr;
        int dc = ec - sc;

        boolean legal = false;
        switch (absPiece) {
            case 1: // 車 (Rook): 直線走, 路徑必須清空
                if (dr != 0 && dc != 0) return false;
                legal = isPathClearSim(board, sr, sc, er, ec);
                break;
            case 2: // 馬 (Horse): 日字走, 且不能被蹩馬腿
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) return false;
                int midR = (Math.abs(dr) == 2) ? sr + (dr / 2) : sr;
                int midC = (Math.abs(dc) == 2) ? sc + (dc / 2) : sc;
                legal = (board[midR][midC] == 0);
                break;
            case 3: // 相 (Elephant): 飛字走, 不可過河, 且不能被塞象眼
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                if ((color == 1 && er < 5) || (color == -1 && er > 4)) return false;
                legal = (board[sr + (dr / 2)][sc + (dc / 2)] == 0);
                break;
            case 4: // 仕 (Advisor): 斜線走, 僅限宮內
                if (Math.abs(dr) != 1 || Math.abs(dc) != 1) return false;
                if (ec < 3 || ec > 5 || (color == 1 && er < 7) || (color == -1 && er > 2)) return false;
                legal = true;
                break;
            case 5: // 將/帥 (General): 直線走, 僅限宮內
                if (Math.abs(dr) + Math.abs(dc) != 1) return false;
                if (ec < 3 || ec > 5 || (color == 1 && er < 7) || (color == -1 && er > 2)) return false;
                legal = true;
                break;
            case 6: // 炮 (Cannon): 直線走, 吃子需經過且僅經過 1 個棋子
                if (dr != 0 && dc != 0) return false;
                if (target == 0) {
                    legal = isPathClearSim(board, sr, sc, er, ec);
                } else {
                    legal = (countPiecesSim(board, sr, sc, er, ec) == 1);
                }
                break;
            case 7: // 兵/卒 (Soldier): 直前走, 過河後可左右
                int dir = (color == 1) ? -1 : 1;
                if (dr == dir && dc == 0) legal = true;
                else if (dr == 0 && Math.abs(dc) == 1) {
                    if ((color == 1 && sr <= 4) || (color == -1 && sr >= 5)) legal = true;
                }
                break;
        }
        return legal;
    }

    /** 檢查兩點之間路徑是否完全清空 */
    private boolean isPathClearSim(int[][] board, int r1, int c1, int r2, int c2) {
        int dr = Integer.compare(r2, r1);
        int dc = Integer.compare(c2, c1);
        int r = r1 + dr;
        int c = c1 + dc;
        while (r != r2 || c != c2) {
            if (board[r][c] != 0) return false;
            r += dr;
            c += dc;
        }
        return true;
    }

    /** 計算兩點之間路徑上有多少個棋子 (用於炮的判定) */
    private int countPiecesSim(int[][] board, int r1, int c1, int r2, int c2) {
        int count = 0;
        int dr = Integer.compare(r2, r1);
        int dc = Integer.compare(c2, c1);
        int r = r1 + dr;
        int c = c1 + dc;
        while (r != r2 || c != c2) {
            if (board[r][c] != 0) count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    /** 模擬更新棋盤狀態 */
    private void applyMove(int[][] board, Move move) {
        board[move.endRow()][move.endCol()] = board[move.startRow()][move.startCol()];
        board[move.startRow()][move.startCol()] = 0;
    }

    /** 複製棋盤陣列 */
    private int[][] cloneBoard(int[][] board) {
        int[][] copy = new int[board.length][];
        for (int i = 0; i < board.length; i++) {
            copy[i] = board[i].clone();
        }
        return copy;
    }
}
