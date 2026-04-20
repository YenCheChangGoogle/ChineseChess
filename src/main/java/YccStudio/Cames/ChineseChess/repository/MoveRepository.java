package YccStudio.Cames.ChineseChess.repository;

import YccStudio.Cames.ChineseChess.model.Move;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MoveRepository extends JpaRepository<Move, Long> {
    List<Move> findByRoomIdOrderByCreatedAtAsc(String roomId);
}
