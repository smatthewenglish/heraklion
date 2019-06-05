package io.tschess.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameTableTest {
    static final String JDBC_DRIVER = "org.postgresql.Driver";
    static final String DB_URL = "jdbc:postgresql://localhost:5432/";

    static Connection connection = null;
    static Statement statement = null;

    @org.junit.Test
    public void main() throws Exception {
        System.out.println("Hello, World");

        // Register JDBC driver
        Class.forName(JDBC_DRIVER);

        // Open a connection
        System.out.println("connect");
        connection = DriverManager.getConnection(DB_URL);

        System.out.println("commenceGame");
        statement = connection.createStatement();

        String identifier = "1313";
        //--------
        String usernameWhite = "w_0";
        String configurationWhite = "[{\"row_0\": [{\"column_0\": \"WhiteRook\"},{\"column_1\": \"WhiteKnight\"},{\"column_2\": \"WhiteBishop\"},{\"column_3\": \"WhiteQueen\"},{\"column_4\": \"WhiteKing\"},{\"column_5\": \"WhiteBishop\"},{\"column_6\": \"WhiteKnight\"},{\"column_7\": \"WhiteRook\"}]},{\"row_1\": [{\"column_0\": \"WhitePawn\"},{\"column_1\": \"WhitePawn\"},{\"column_2\": \"WhitePawn\"},{\"column_3\": \"WhitePawn\"},{\"column_4\": \"WhitePawn\"},{\"column_5\": \"WhitePawn\"},{\"column_6\": \"WhitePawn\"},{\"column_7\":\"WhiteQueen\"}]}]";
        //--------
        String usernameBlack = "b_0";
        String configurationBlack = "[{\"row_0\": [{\"column_0\": \"BlackRook\"},{\"column_1\": \"BlackKnight\"},{\"column_2\": \"BlackBishop\"},{\"column_3\": \"BlackQueen\"},{\"column_4\": \"BlackKing\"},{\"column_5\": \"BlackBishop\"},{\"column_6\": \"BlackKnight\"},{\"column_7\": \"BlackRook\"}]},{\"row_1\": [{\"column_0\": \"BlackPawn\"},{\"column_1\": \"BlackPawn\"},{\"column_2\": \"BlackPawn\"},{\"column_3\": \"BlackPawn\"},{\"column_4\": \"BlackPawn\"},{\"column_5\": \"BlackPawn\"},{\"column_6\": \"BlackPawn\"},{\"column_7\":\"BlackQueen\"}]}]";
        //--------
        String usernameTurn = "white";
        String inviterId = "ir_0";
        String inviteeId = "ie_0";
        //--------
        // String gamestate = "[{\"row_0\": [{\"column_0\": \"BlackRook\"},{\"column_1\": \"BlackKnight\"},{\"column_2\": \"BlackBishop\"},{\"column_3\": \"BlackQueen\"},{\"column_4\": \"BlackKing\"},{\"column_5\": \"BlackBishop\"},{\"column_6\": \"BlackKnight\"},{\"column_7\": \"BlackRook\"}]},{\"row_1\": [{\"column_0\": \"BlackPawn\"},{\"column_1\": \"BlackPawn\"},{\"column_2\": \"BlackPawn\"},{\"column_3\": \"BlackPawn\"},{\"column_4\": \"BlackPawn\"},{\"column_5\": \"BlackPawn\"},{\"column_6\": \"BlackPawn\"},{\"column_7\":\"BlackQueen\"}]},{\"row_2\":[{},{},{},{},{},{},{},{}]},{\"row_3\":[{},{},{},{},{},{},{},{}]},{\"row_4\":[{},{},{},{},{},{},{},{}]},{\"row_5\": [{},{},{},{},{},{},{},{}]},{\"row_6\": [{\"column_0\": \"WhiteRook\"},{\"column_1\": \"WhiteKnight\"},{\"column_2\": \"WhiteBishop\"},{\"column_3\": \"WhiteQueen\"},{\"column_4\": \"WhiteKing\"},{\"column_5\": \"WhiteBishop\"},{\"column_6\": \"WhiteKnight\"},{\"column_7\": \"WhiteRook\"}]},{\"row_7\": [{\"column_0\": \"WhitePawn\"},{\"column_1\": \"WhitePawn\"},{\"column_2\": \"WhitePawn\"},{\"column_3\": \"WhitePawn\"},{\"column_4\": \"WhitePawn\"},{\"column_5\": \"WhitePawn\"},{\"column_6\": \"WhitePawn\"},{\"column_7\":\"WhiteQueen\"}]}]";
        // String gameStatus = "PROPOSED";
        // String winner = "";

        String sqlInsert = "INSERT INTO game_table VALUES ('"
                                                         + identifier + "', '"
                                                         + usernameWhite + "', '"
                                                         + configurationWhite + "', '"
                                                         + usernameBlack + "', '"
                                                         + configurationBlack + "', '"
                                                         + usernameTurn + "', '"
                                                         + inviterId + "', '"
                                                         + inviteeId + "')";




//        String sqlInsert = "INSERT INTO game_table VALUES ('"
//                                                         + identifier + "', '"
//                                                         + usernameWhite + "', '"
//                                                         + configurationWhite + "', '"
//                                                         + usernameBlack + "', '"
//                                                         + configurationBlack + "', '"
//                                                         + usernameTurn + "', '"
//                                                         + inviterId + "', '"
//                                                         + inviteeId + "', '"
//                                                         + gameStatus + "')";

        statement.executeUpdate(sqlInsert);
    }

}


