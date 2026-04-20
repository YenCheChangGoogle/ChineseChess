package YccStudio.Cames.ChineseChess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChineseChessApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChineseChessApplication.class, args);
        System.out.println("====================================================");
        System.out.println("🚀 線上中國象棋伺服器已成功啟動！");
        System.out.println("🌐 訪問網址: http://localhost:8088/ChineseChess");
        System.out.println("====================================================");
    }
}
