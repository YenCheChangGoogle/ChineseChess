package YccStudio.Cames.ChineseChess.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChessGameService {
    
    private static final int ROWS = 10;
    private static final int COLS = 9;
    
    public record ValidationResult(boolean isValid, String reason) {}

    // RoomId -> BoardState
    private final Map<String, int[][]> roomBoards = new ConcurrentHashMap<>();
    // RoomId -> CurrentTurn (1: Red, -1: Black)
    private final Map<String, Integer> roomTurns = new ConcurrentHashMap<>();

    /**
     * Initialize a full Chinese Chess board.

     * Positive numbers: Red pieces (Bottom)
     * Negative numbers: Black pieces (Top)
     * 0: Empty
     */
    public int[][] initializeBoard() {
        int[][] board = new int[ROWS][COLS];

        // Red Side (Bottom - Positive)
        // Row 9: Rook, Horse, Elephant, Advisor, General, Advisor, Elephant, Horse, Rook
        board[9][0] = 1; board[9][8] = 1; // Rook
        board[9][1] = 2; board[9][7] = 2; // Horse
        board[9][2] = 3; board[9][6] = 3; // Elephant
        board[9][3] = 4; board[9][5] = 4; // Advisor
        board[9][4] = 5;                  // General
        
        // Row 7: Cannon (Columns 1 and 7)
        board[7][1] = 6; board[7][7] = 6; // Cannon
        
        // Row 6: Soldiers (Columns 0, 2, 4, 6, 8)
        for(int i=0; i<9; i+=2) board[6][i] = 7; // Soldiers

        // Black Side (Top - Negative)
        // Row 0: Rook, Horse, Elephant, Advisor, General, Advisor, Elephant, Horse, Rook
        board[0][0] = -1; board[0][8] = -1; // Rook
        board[0][1] = -2; board[0][7] = -2; // Horse
        board[0][2] = -3; board[0][6] = -3; // Elephant
        board[0][3] = -4; board[0][5] = -4; // Advisor
        board[0][4] = -5;                  // General
        
        // Row 2: Cannon (Columns 1 and 7)
        board[2][1] = -6; board[2][7] = -6; // Cannon
        
        // Row 3: Soldiers (Columns 0, 2, 4, 6, 8)
        for(int i=0; i<9; i+=2) board[3][i] = -7; // Soldiers

        return board;
    }

    public void createGame(String roomId) {
        roomBoards.put(roomId, initializeBoard());
        roomTurns.put(roomId, 1); // Red starts first
    }

    public void movePiece(String roomId, int startRow, int startCol, int endRow, int endCol) {
        int[][] board = getBoard(roomId);
        if (board != null) {
            board[endRow][endCol] = board[startRow][startCol];
            board[startRow][startCol] = 0;
        }
    }

    /**
     * Checks if the game is over.
     * Returns: Winner's color (1 for Red, -1 for Black), or 0 if game continues.
     */
    public int checkGameOver(String roomId) {
        int[][] board = getBoard(roomId);
        int currentTurn = getCurrentTurn(roomId);
        int opponentColor = -currentTurn;

        // 1. Check if General was captured (the piece at General's position is gone or replaced)
        // Since isValidMove allows capturing the general, we check if the current player's general exists.
        if (!generalExists(board, currentTurn)) {
            return opponentColor; // Opponent wins
        }

        // 2. Check for Checkmate
        // If the current player is in check and has no legal moves to escape, they are checkmated.
        if (isGeneralUnderAttack(roomId, currentTurn)) {
            if (!hasAnyLegalMove(roomId, currentTurn)) {
                return opponentColor; // Opponent wins
            }
        }

        return 0; // Game continues
    }

    private boolean generalExists(int[][] board, int color) {
        int targetGen = (color == 1) ? 5 : -5;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == targetGen) return true;
            }
        }
        return false;
    }

    private boolean hasAnyLegalMove(String roomId, int playerColor) {
        int[][] board = getBoard(roomId);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int piece = board[r][c];
                if (piece != 0 && ((playerColor == 1 && piece > 0) || (playerColor == -1 && piece < 0))) {
                    // Try all possible moves for this piece
                    for (int tr = 0; tr < ROWS; tr++) {
                        for (int tc = 0; tc < COLS; tc++) {
                            if (isValidMove(roomId, r, c, tr, tc, playerColor)) {
                                return true; // Found at least one legal move
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public int getCurrentTurn(String roomId) {
        return roomTurns.getOrDefault(roomId, 1);
    }

    public void switchTurn(String roomId) {
        Integer currentTurn = roomTurns.get(roomId);
        if (currentTurn != null) {
            roomTurns.put(roomId, -currentTurn);
        }
    }

    public int[][] getBoard(String roomId) {

        return roomBoards.getOrDefault(roomId, initializeBoard());
    }

    public boolean isValidMove(String roomId, int startRow, int startCol, int endRow, int endCol, int playerColor) {
        return validateMove(roomId, startRow, startCol, endRow, endCol, playerColor).isValid();
    }

    public String getMoveInvalidReason(String roomId, int startRow, int startCol, int endRow, int endCol, int playerColor) {
        return validateMove(roomId, startRow, startCol, endRow, endCol, playerColor).reason();
    }

    private ValidationResult validateMove(String roomId, int startRow, int startCol, int endRow, int endCol, int playerColor) {
        int[][] board = getBoard(roomId);
        if (board == null) return new ValidationResult(false, "伺服器錯誤，無法獲取棋盤");
        if (endRow < 0 || endRow >= ROWS || endCol < 0 || endCol >= COLS) return new ValidationResult(false, "目標位置超出棋盤範圍");

        int piece = board[startRow][startCol];
        if (piece == 0) return new ValidationResult(false, "選中的位置沒有棋子");
        if ((playerColor == 1 && piece < 0) || (playerColor == -1 && piece > 0)) return new ValidationResult(false, "不能移動對方的棋子");

        int targetPiece = board[endRow][endCol];
        if (targetPiece != 0 && ((playerColor == 1 && targetPiece > 0) || (playerColor == -1 && targetPiece < 0))) {
            return new ValidationResult(false, "不能吃掉自己的棋子");
        }

        int absPiece = Math.abs(piece);
        int dr = endRow - startRow;
        int dc = endCol - startCol;

        boolean moveLegal = false;
        String reason = "此走法不符合象棋規則";

        switch (absPiece) {
            case 1: // Rook
                if (dr != 0 && dc != 0) {
                    reason = "車只能直線移動";
                } else if (!isPathClear(board, startRow, startCol, endRow, endCol)) {
                    reason = "車的行進路徑被阻擋";
                } else {
                    moveLegal = true;
                }
                break;
            case 2: // Horse
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) {
                    reason = "馬的走法不正確 (需走『日』字)";
                } else {
                    int midR = (Math.abs(dr) == 2) ? startRow + (dr / 2) : startRow;
                    int midC = (Math.abs(dc) == 2) ? startCol + (dc / 2) : startCol;
                    if (board[midR][midC] != 0) {
                        reason = "馬被『蹩馬腿』，無法移動";
                    } else {
                        moveLegal = true;
                    }
                }
                break;
            case 3: // Elephant
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) {
                    reason = "相的走法不正確 (需走『田』字)";
                } else if ((playerColor == 1 && endRow < 5) || (playerColor == -1 && endRow > 4)) {
                    reason = "相不能過河";
                } else if (board[startRow + (dr / 2)][startCol + (dc / 2)] != 0) {
                    reason = "相被『蹩相腿』，無法移動";
                } else {
                    moveLegal = true;
                }
                break;
            case 4: // Advisor
                if (Math.abs(dr) != 1 || Math.abs(dc) != 1) {
                    reason = "仕的走法不正確 (需斜走一格)";
                } else if (endCol < 3 || endCol > 5 || (playerColor == 1 && endRow < 7) || (playerColor == -1 && endRow > 2)) {
                    reason = "仕不能走出宮";
                } else {
                    moveLegal = true;
                }
                break;
            case 5: // General
                if (Math.abs(dr) + Math.abs(dc) != 1) {
                    reason = "將帥只能直線移動一格";
                } else if (endCol < 3 || endCol > 5 || (playerColor == 1 && endRow < 7) || (playerColor == -1 && endRow > 2)) {
                    reason = "將帥不能走出宮";
                } else {
                    moveLegal = true;
                }
                break;
            case 6: // Cannon
                if (dr != 0 && dc != 0) {
                    reason = "炮只能直線移動";
                } else if (targetPiece == 0) {
                    if (!isPathClear(board, startRow, startCol, endRow, endCol)) {
                        reason = "炮在無目標時不能跳過棋子";
                    } else {
                        moveLegal = true;
                    }
                } else {
                    if (countPiecesInPath(board, startRow, startCol, endRow, endCol) != 1) {
                        reason = "炮在攻擊時必須且只能跳過一個棋子";
                    } else {
                        moveLegal = true;
                    }
                }
                break;
            case 7: // Soldier
                int dir = (playerColor == 1) ? -1 : 1;
                if (dr == dir && dc == 0) {
                    moveLegal = true;
                } else if (dr == 0 && Math.abs(dc) == 1) {
                    if (!((playerColor == 1 && startRow <= 4) || (playerColor == -1 && startRow >= 5))) {
                        reason = "卒/兵在過河前不能橫走";
                    } else {
                        moveLegal = true;
                    }
                } else {
                    reason = "卒/兵走法錯誤";
                }
                break;
            default:
                return new ValidationResult(false, "未知的棋子類型");
        }

        if (!moveLegal) return new ValidationResult(false, reason);

        if (willCauseFlyingGeneral(board, startRow, startCol, endRow, endCol)) {
            return new ValidationResult(false, "此走法會導致『飛將』(將帥不能直接對面)");
        }

        int[][] simulatedBoard = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) simulatedBoard[r] = board[r].clone();
        simulatedBoard[endRow][endCol] = board[startRow][startCol];
        simulatedBoard[startRow][startCol] = 0;

        if (isGeneralUnderAttack(simulatedBoard, playerColor)) {
            return new ValidationResult(false, "此走法會導致您的將帥暴露在攻擊之下，請重新選擇！");
        }

        return new ValidationResult(true, null);
    }

    private boolean willCauseFlyingGeneral(int[][] board, int sr, int sc, int er, int ec) {
        // Simulate move
        int[][] nextBoard = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) nextBoard[r] = board[r].clone();
        nextBoard[er][ec] = nextBoard[sr][sc];
        nextBoard[sr][sc] = 0;

        int redGenR = -1, redGenC = -1, blackGenR = -1, blackGenC = -1;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (nextBoard[r][c] == 5) { redGenR = r; redGenC = c; }
                if (nextBoard[r][c] == -5) { blackGenR = r; blackGenC = c; }
            }
        }

        if (redGenC == blackGenC) {
            int rMin = Math.min(redGenR, blackGenR);
            int rMax = Math.max(redGenR, blackGenR);
            boolean blocked = false;
            for (int r = rMin + 1; r < rMax; r++) {
                if (nextBoard[r][redGenC] != 0) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) return true; // Illegal: Flying General
        }
        return false;
    }

    public boolean isGeneralUnderAttack(String roomId, int generalColor) {
        return isGeneralUnderAttack(getBoard(roomId), generalColor);
    }

    public boolean isGeneralUnderAttack(int[][] board, int generalColor) {
        if (board == null) return false;

        int genR = -1, genC = -1;
        // Find general's position
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == (generalColor == 1 ? 5 : -5)) {
                    genR = r;
                    genC = c;
                    break;
                }
            }
        }

        if (genR == -1) return false;

        // Check all opponent pieces to see if any can attack this position
        int opponentColor = -generalColor;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int piece = board[r][c];
                if (piece != 0 && (opponentColor == 1 ? piece > 0 : piece < 0)) {
                    // Check if this piece can move to (genR, genC)
                    // Use a simplified check to avoid infinite recursion if isValidMove is called here
                    if (canPieceAttack(board, r, c, genR, genC, opponentColor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canPieceAttack(int[][] board, int startRow, int startCol, int endRow, int endCol, int playerColor) {
        if (endRow < 0 || endRow >= ROWS || endCol < 0 || endCol >= COLS) return false;

        int piece = board[startRow][startCol];
        if (piece == 0) return false;

        int targetPiece = board[endRow][endCol];
        if (targetPiece != 0 && ((playerColor == 1 && targetPiece > 0) || (playerColor == -1 && targetPiece < 0))) {
            return false;
        }

        int absPiece = Math.abs(piece);
        int dr = endRow - startRow;
        int dc = endCol - startCol;

        switch (absPiece) {
            case 1: // Rook
                if (dr != 0 && dc != 0) return false;
                return isPathClear(board, startRow, startCol, endRow, endCol);
            case 2: // Horse
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) return false;
                int midR = (Math.abs(dr) == 2) ? startRow + (dr / 2) : startRow;
                int midC = (Math.abs(dc) == 2) ? startCol + (dc / 2) : startCol;
                return (board[midR][midC] == 0);
            case 3: // Elephant
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                if ((playerColor == 1 && endRow < 5) || (playerColor == -1 && endRow > 4)) return false;
                return (board[startRow + (dr / 2)][startCol + (dc / 2)] == 0);
            case 4: // Advisor
                if (Math.abs(dr) != 1 || Math.abs(dc) != 1) return false;
                if (endCol < 3 || endCol > 5) return false;
                if ((playerColor == 1 && endRow < 7) || (playerColor == -1 && endRow > 2)) return false;
                return true;
            case 5: // General
                if (Math.abs(dr) + Math.abs(dc) != 1) return false;
                if (endCol < 3 || endCol > 5) return false;
                if ((playerColor == 1 && endRow < 7) || (playerColor == -1 && endRow > 2)) return false;
                return true;
            case 6: // Cannon
                if (dr != 0 && dc != 0) return false;
                if (targetPiece == 0) {
                    return isPathClear(board, startRow, startCol, endRow, endCol);
                } else {
                    return (countPiecesInPath(board, startRow, startCol, endRow, endCol) == 1);
                }
            case 7: // Soldier
                int dir = (playerColor == 1) ? -1 : 1;
                if (dr == dir && dc == 0) return true;
                if (dr == 0 && Math.abs(dc) == 1) {
                    if ((playerColor == 1 && startRow <= 4) || (playerColor == -1 && startRow >= 5)) return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean isPathClear(int[][] board, int r1, int c1, int r2, int c2) {
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

    private int countPiecesInPath(int[][] board, int r1, int c1, int r2, int c2) {
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
}
