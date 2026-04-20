package YccStudio.Cames.ChineseChess.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @Column(length = 8)
    private String roomId;

    private String owner;
    private String status; // WAITING, PLAYING, FINISHED

    @ElementCollection
    @CollectionTable(name = "room_players", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "username")
    private List<String> players = new ArrayList<>();

    public Room() {}

    public Room(String roomId, String owner, String status, List<String> players) {
        this.roomId = roomId;
        this.owner = owner;
        this.status = status;
        this.players = players;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getPlayers() { return players; }
    public void setPlayers(List<String> players) { this.players = players; }
}
