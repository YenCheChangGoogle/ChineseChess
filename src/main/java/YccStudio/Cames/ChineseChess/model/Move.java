package YccStudio.Cames.ChineseChess.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "moves")
public class Move {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 8)
    private String roomId;
    private String username;
    private int startRow;
    private int startCol;
    private int endRow;
    private int endCol;
    private LocalDateTime createdAt;

    public Move() {}

    public Move(Long id, String roomId, String username, int startRow, int startCol, int endRow, int endCol, LocalDateTime createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.username = username;
        this.startRow = startRow;
        this.startCol = startCol;
        this.endRow = endRow;
        this.endCol = endCol;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getStartRow() { return startRow; }
    public void setStartRow(int startRow) { this.startRow = startRow; }

    public int getStartCol() { return startCol; }
    public void setStartCol(int startCol) { this.startCol = startCol; }

    public int getEndRow() { return endRow; }
    public void setEndRow(int endRow) { this.endRow = endRow; }

    public int getEndCol() { return endCol; }
    public void setEndCol(int endCol) { this.endCol = endCol; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
