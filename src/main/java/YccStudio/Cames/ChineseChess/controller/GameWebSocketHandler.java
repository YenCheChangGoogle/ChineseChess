package YccStudio.Cames.ChineseChess.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import YccStudio.Cames.ChineseChess.model.Room;
import YccStudio.Cames.ChineseChess.service.ChessGameService;
import YccStudio.Cames.ChineseChess.service.RoomService;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ChessGameService chessGameService;
    private final RoomService roomService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

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
        
        // Add session to room tracking
        roomSessions.computeIfAbsent(roomId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
        
        int color = roomService.getPlayerColor(username);
        int currentTurn = chessGameService.getCurrentTurn(roomId);
        int[][] board = chessGameService.getBoard(roomId);
        
        // Notify user of their role and current room status
        Map<String, Object> response = new HashMap<>();
        response.put("type", "JOIN_SUCCESS");
        response.put("color", color); 
        response.put("roomId", roomId);
        response.put("currentTurn", currentTurn);
        response.put("board", board); // Include board immediately on join
        
        // Tell the joining player if the game is already playing
        if ("PLAYING".equals(roomService.getRoom(roomId).getStatus())) {
            response.put("gameState", "PLAYING");
        } else {
            response.put("gameState", "WAITING");
        }
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        broadcastToRoom(roomId, "{\"type\":\"PLAYER_JOINED\", \"user\":\"" + username + "\"}");
        
        // Explicitly trigger game start if both are present
        if ("PLAYING".equals(roomService.getRoom(roomId).getStatus())) {
            broadcastToRoom(roomId, "{\"type\":\"GAME_STARTED\"}");
        }
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
            
            // Broadcast the move and the new board state to everyone in the room
            Map<String, Object> moveData = new HashMap<>();
            moveData.put("type", "MOVE_MADE");
            moveData.put("startRow", startRow);
            moveData.put("startCol", startCol);
            moveData.put("endRow", endRow);
            moveData.put("endCol", endCol);
            moveData.put("user", username);
            moveData.put("board", chessGameService.getBoard(roomId));
            
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
                
                // Reset board for the next game
                resetAndSyncGame(roomId);
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
        
        // Reset board for the next game
        resetAndSyncGame(roomId);
    }

    private void broadcastToRoom(String roomId, String message) {
        Set<WebSocketSession> roomSess = roomSessions.get(roomId);
        if (roomSess != null) {
            synchronized (roomSess) {
                roomSess.forEach(s -> {
                    try {
                        if (s.isOpen()) {
                            s.sendMessage(new TextMessage(message));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String user = sessionToUser.remove(session.getId());
        sessions.remove(session.getId());
        
        if (user != null) {
            String roomId = roomService.getRoomIdByUsername(user);
            if (roomId != null) {
                // Check if they were in a game before removing them from room tracking
                boolean wasPlaying = "PLAYING".equals(roomService.getRoom(roomId).getStatus());
                
                // Remove from room tracking
                Set<WebSocketSession> roomSess = roomSessions.get(roomId);
                if (roomSess != null) {
                    roomSess.remove(session);
                    if (roomSess.isEmpty()) {
                        roomSessions.remove(roomId);
                    }
                }

                // Handle game interruption
                String interruptedRoomId = roomService.leaveRoom(user);
                if (interruptedRoomId != null) {
                    handleGameInterruption(interruptedRoomId, user);
                }
            }
        }
    }

    private void handleGameInterruption(String roomId, String loserUsername) {
        Room room = roomService.getRoom(roomId);
        if (room == null || room.getPlayers() == null || room.getPlayers().isEmpty()) return;

        // Find the winner (the player who is NOT the one who left)
        String winnerUsername = null;
        for (String player : room.getPlayers()) {
            if (!player.equals(loserUsername)) {
                winnerUsername = player;
                break;
            }
        }

        if (winnerUsername == null) return; // No one left to win

        int winnerColor = roomService.getPlayerColor(winnerUsername);

        broadcastToRoom(roomId, "{\"type\":\"GAME_OVER\", \"winner\":" + winnerColor + ", \"reason\":\"對方擅自離開房間，您獲得勝利！\", \"loser\":\"" + loserUsername + "\"}");
        
        // Reset board for the next game
        resetAndSyncGame(roomId);
    }

    private void resetAndSyncGame(String roomId) {
        // Reset board and turn in service
        chessGameService.createGame(roomId);
        
        // Sync new board and turn to all players in the room
        int[][] board = chessGameService.getBoard(roomId);
        int currentTurn = chessGameService.getCurrentTurn(roomId);
        
        Map<String, Object> syncData = new HashMap<>();
        syncData.put("type", "BOARD_SYNC");
        syncData.put("board", board);
        syncData.put("currentTurn", currentTurn);
        
        try {
            broadcastToRoom(roomId, objectMapper.writeValueAsString(syncData));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
