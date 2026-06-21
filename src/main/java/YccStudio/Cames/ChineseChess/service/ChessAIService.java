package YccStudio.Cames.ChineseChess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChessAIService {

    @Autowired
    private ChessGameService chessGameService;

    public record Move(int startRow, int startCol, int endRow, int endCol) {}

    // 棋子基礎價值
    private static final Map<Integer, Integer> PIECE_VALUES = Map.of(
        1, 1200, // 車
        2, 400,  // 馬
        3, 20,   // 相
        4, 20,   // 仕
        5, 20000, // 將/帥
        6, 450,  // 炮
        7, 15    // 兵/卒 (基礎)
    );

    // 簡單的位置價值表 (示例：將帥在中心較穩, 車在邊線較強)
    // 實際 AI 應有詳細 10x9 權重表, 此處實作基礎權重
    private final int[][] generalPosValue = new int[10][9];
    private final int[][] rookPosValue = new int[10][9];

    public ChessAIService() {
        // 初始化位置價值 (簡單賦值)
        for(int i=0; i<10; i++) {
            for(int j=0; j<9; j++) {
                rookPosValue[i][j] = (j == 0 || j == 8) ? 10 : 5;
                generalPosValue[i][j] = (j >= 3 && j <= 5) ? 10 : 2;
            }
        }
    }

    /**
     * 獲取 AI 的最佳走法
     */
    public Move getBestMove(String roomId, int aiColor, String difficulty) {
        int depth = getDepthByDifficulty(difficulty);
        int[][] board = chessGameService.getBoard(roomId);
        
        List<Move> legalMoves = getAllLegalMoves(board, aiColor);
        if (legalMoves.isEmpty()) return null;

        Move bestMove = null;
        double bestValue = (aiColor == 1) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        for (Move move : legalMoves) {
            int[][] nextBoard = cloneBoard(board);
            applyMove(nextBoard, move);
            
            double boardValue = minimax(nextBoard, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, aiColor == 1 ? false : true);
            
            if (aiColor == 1) {
                if (boardValue > bestValue) {
                    bestValue = boardValue;
                    bestMove = move;
                }
            } else {
                if (boardValue < bestValue) {
                    bestValue = boardValue;
                    bestMove = move;
                }
            }
        }
        return bestMove;
    }

    private int getDepthByDifficulty(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy" -> 2;
            case "hard" -> 4; // Depth 6 對於 Java 實時計算可能太慢, 設為 4-5 較穩
            default -> 3;
        };
    }

    private double minimax(int[][] board, int depth, double alpha, double beta, boolean isMaximizing) {
        if (depth == 0) {
            return evaluateBoard(board);
        }

        // 這裡簡化處理：AI 總是嘗試對抗另一個顏色
        int turnColor = isMaximizing ? 1 : -1;
        List<Move> moves = getAllLegalMoves(board, turnColor);
        
        if (moves.isEmpty()) return evaluateBoard(board) * 10;

        if (isMaximizing) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (Move move : moves) {
                int[][] nextBoard = cloneBoard(board);
                applyMove(nextBoard, move);
                double eval = minimax(nextBoard, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (Move move : moves) {
                int[][] nextBoard = cloneBoard(board);
                applyMove(nextBoard, move);
                double eval = minimax(nextBoard, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private double evaluateBoard(int[][] board) {
        double totalScore = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                
                int absPiece = Math.abs(piece);
                int value = PIECE_VALUES.getOrDefault(absPiece, 0);
                
                // 兵卒過河加分
                if (absPiece == 7) {
                    if ((piece > 0 && r <= 4) || (piece < 0 && r >= 5)) {
                        value = 30;
                    }
                }

                // 基礎位置加分
                if (absPiece == 1) value += rookPosValue[r][c];
                if (absPiece == 5) value += generalPosValue[r][c];

                if (piece > 0) totalScore += value; // 紅色加分
                else totalScore -= value;           // 黑色減分
            }
        }
        return totalScore;
    }

    private List<Move> getAllLegalMoves(int[][] board, int playerColor) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
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

    private boolean isValidMoveSimulated(int[][] board, int sr, int sc, int er, int ec, int color) {
        // 這裡-- 由於 ChessGameService 的 isValidMove 依賴於 roomId 獲取 board,
        // AI 引擎需要一個能直接接收 board 陣列的驗證方法。
        // 為保持簡潔, 此處實作與 ChessGameService 邏輯一致的模擬驗證
        if (er < 0 || er >= 10 || ec < 0 || ec >= 9) return false;
        int piece = board[sr][sc];
        if (piece == 0) return false;
        if ((color == 1 && piece < 0) || (color == -1 && piece > 0)) return false;
        
        int target = board[er][ec];
        if (target != 0 && ((color == 1 && target > 0) || (color == -1 && target < 0))) return false;

        int absPiece = Math.abs(piece);
        int dr = er - sr;
        int dc = ec - sc;

        boolean legal = false;
        switch (absPiece) {
            case 1: // Rook
                if (dr != 0 && dc != 0) return false;
                legal = isPathClearSim(board, sr, sc, er, ec);
                break;
            case 2: // Horse
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) return false;
                int midR = (Math.abs(dr) == 2) ? sr + (dr / 2) : sr;
                int midC = (Math.abs(dc) == 2) ? sc + (dc / 2) : sc;
                legal = (board[midR][midC] == 0);
                break;
            case 3: // Elephant
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                if ((color == 1 && er < 5) || (color == -1 && er > 4)) return false;
                legal = (board[sr + (dr / 2)][sc + (dc / 2)] == 0);
                break;
            case 4: // Advisor
                if (Math.abs(dr) != 1 || Math.abs(dc) != 1) return false;
                if (ec < 3 || ec > 5 || (color == 1 && er < 7) || (color == -1 && er > 2)) return false;
                legal = true;
                break;
            case 5: // General
                if (Math.abs(dr) + Math.abs(dc) != 1) return false;
                if (ec < 3 || ec > 5 || (color == 1 && er < 7) || (color == -1 && er > 2)) return false;
                legal = true;
                break;
            case 6: // Cannon
                if (dr != 0 && dc != 0) return false;
                if (target == 0) {
                    legal = isPathClearSim(board, sr, sc, er, ec);
                } else {
                    legal = (countPiecesSim(board, sr, sc, er, ec) == 1);
                }
                break;
            case 7: // Soldier
                int dir = (color == 1) ? -1 : 1;
                if (dr == dir && dc == 0) legal = true;
                else if (dr == 0 && Math.abs(dc) == 1) {
                    if ((color == 1 && sr <= 4) || (color == -1 && sr >= 5)) legal = true;
                }
                break;
        }
        return legal;
    }

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

    private void applyMove(int[][] board, Move move) {
        board[move.endRow()][move.endCol()] = board[move.startRow()][move.startCol()];
        board[move.startRow()][move.startCol()] = 0;
    }

    private int[][] cloneBoard(int[][] board) {
        int[][] copy = new int[board.length][];
        for (int i = 0; i < board.length; i++) {
            copy[i] = board[i].clone();
        }
        return copy;
    }
}
