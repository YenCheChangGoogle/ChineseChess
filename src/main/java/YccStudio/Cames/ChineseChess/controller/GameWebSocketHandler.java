package YccStudio.Cames.ChineseChess.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import YccStudio.Cames.ChineseChess.service.ChessGameService;
import YccStudio.Cames.ChineseChess.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ChessGameService chessGameService;
    private final RoomService roomService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    public GameWebSocketHandler(ChessGameService chessGameService, RoomService roomService) {
        this.chessGameService = chessGameService;
        this.roomService = roomService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            
            if ("JOIN".equals(type)) {
                handleJoin(session, data);
            } else if ("MOVE".equals(type)) {
                handleMove(session, data);
            } else if ("SYNC".equals(type)) {
                handleSync(session, data);
            } else if ("RESIGN".equals(type)) {
                handleResign(session);
            }
        } catch (Exception e) {
            session.sendMessage(new TextMessage("系統錯誤: " + e.getMessage()));
        }
    }

    private void handleJoin(WebSocketSession session, Map<String, Object> data) throws IOException {
        String roomId = (String) data.get("roomId");
        String username = (String) data.get("username");
        
        sessionToUser.put(session.getId(), username);
        roomService.joinRoom(roomId, username);
        chessGameService.createGame(roomId); // Initialize board for the room
        
        int color = roomService.getPlayerColor(username);
        int currentTurn = chessGameService.getCurrentTurn(roomId);
        
        // Notify user of their role
        Map<String, Object> response = new HashMap<>();
        response.put("type", "JOIN_SUCCESS");
        response.put("color", color); 
        response.put("roomId", roomId);
        response.put("currentTurn", currentTurn);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        broadcastToRoom(roomId, "{\"type\":\"PLAYER_JOINED\", \"user\":\"" + username + "\"}");
    }

    private void handleMove(WebSocketSession session, Map<String, Object> data) throws IOException {
        String username = sessionToUser.get(session.getId());
        if (username == null) {
            session.sendMessage(new TextMessage("身分驗證失敗，請重新登入。"));
            return;
        }

        String roomId = roomService.getRoomIdByUsername(username);
        if (roomId == null) {
            session.sendMessage(new TextMessage("您目前不在任何房間中。"));
            return;
        }

        int startRow = (int) data.get("startRow");
        int startCol = (int) data.get("startCol");
        int endRow = (int) data.get("endRow");
        int endCol = (int) data.get("endCol");
        int color = roomService.getPlayerColor(username);
        int currentTurn = chessGameService.getCurrentTurn(roomId);

        // Check if it's the player's turn
        if (color != currentTurn) {
            session.sendMessage(new TextMessage("{\"type\":\"MOVE_INVALID\", \"message\":\"現在是對方操作階段，您需等待對方完成操作後方可進行操作\"}"));
            return;
        }

        if (chessGameService.isValidMove(roomId, startRow, startCol, endRow, endCol, color)) {
            chessGameService.movePiece(roomId, startRow, startCol, endRow, endCol);
            chessGameService.switchTurn(roomId);
            
            // Broadcast the move to everyone in the room
            Map<String, Object> moveData = new HashMap<>();
            moveData.put("type", "MOVE_MADE");
            moveData.put("startRow", startRow);
            moveData.put("startCol", startCol);
            moveData.put("endRow", endRow);
            moveData.put("endCol", endCol);
            moveData.put("user", username);
            
            broadcastToRoom(roomId, objectMapper.writeValueAsString(moveData));

            // Broadcast the turn change
            Map<String, Object> turnData = new HashMap<>();
            turnData.put("type", "TURN_CHANGED");
            turnData.put("nextTurn", chessGameService.getCurrentTurn(roomId));
            broadcastToRoom(roomId, objectMapper.writeValueAsString(turnData));

            // Check for Win/Loss (Captured General or Checkmate)
            int winner = chessGameService.checkGameOver(roomId);
            if (winner != 0) {
                Map<String, Object> gameOverData = new HashMap<>();
                gameOverData.put("type", "GAME_OVER");
                gameOverData.put("winner", winner);
                gameOverData.put("reason", (winner == 1) ? "紅方獲勝 (將軍被吃或將死)" : "黑方獲勝 (將軍被吃或將死)");
                broadcastToRoom(roomId, objectMapper.writeValueAsString(gameOverData));
                return; // End move handling, game is over
            }

            // Check if the current player (who just received the turn) is in check
            currentTurn = chessGameService.getCurrentTurn(roomId);
            if (chessGameService.isGeneralUnderAttack(roomId, currentTurn)) {
                Map<String, Object> checkData = new HashMap<>();
                checkData.put("type", "CHECK");
                broadcastToRoom(roomId, objectMapper.writeValueAsString(checkData));
            }
        } else {
            String reason = chessGameService.getMoveInvalidReason(roomId, startRow, startCol, endRow, endCol, color);
            session.sendMessage(new TextMessage("{\"type\":\"MOVE_INVALID\", \"message\":\"" + (reason != null ? reason : "此走法不符合象棋規則") + "\"}"));
        }
    }

    private void handleSync(WebSocketSession session, Map<String, Object> data) throws IOException {
        String username = sessionToUser.get(session.getId());
        if (username == null) return;

        String roomId = roomService.getRoomIdByUsername(username);
        if (roomId == null) return;

        int[][] board = chessGameService.getBoard(roomId);
        Map<String, Object> response = new HashMap<>();
        response.put("type", "BOARD_SYNC");
        response.put("board", board);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleResign(WebSocketSession session) throws IOException {
        String username = sessionToUser.get(session.getId());
        if (username == null) return;

        String roomId = roomService.getRoomIdByUsername(username);
        if (roomId == null) return;

        int loserColor = roomService.getPlayerColor(username);
        int winnerColor = -loserColor;

        // Broadcast game over event
        Map<String, Object> gameOverData = new HashMap<>();
        gameOverData.put("type", "GAME_OVER");
        gameOverData.put("winner", winnerColor);
        gameOverData.put("reason", "resignation");
        gameOverData.put("loser", username);

        broadcastToRoom(roomId, objectMapper.writeValueAsString(gameOverData));
    }

    private void broadcastToRoom(String roomId, String message) {
        sessions.values().stream()
                .filter(s -> {
                    String user = sessionToUser.get(s.getId());
                    return user != null && roomId.equals(roomService.getRoomIdByUsername(user));
                })
                .forEach(s -> {
                    try {
                        s.sendMessage(new TextMessage(message));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String user = sessionToUser.remove(session.getId());
        sessions.remove(session.getId());
    }
}
