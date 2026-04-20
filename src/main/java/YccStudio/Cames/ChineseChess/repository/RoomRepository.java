package YccStudio.Cames.ChineseChess.repository;

import YccStudio.Cames.ChineseChess.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    List<Room> findByStatus(String status);
}
