package io.tschess.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class UserTableTest {
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

        //--------
        String identifier = "1313";
        System.out.println("identifier" + identifier);

        String username = "s_m_e";
        System.out.println("username" + username);

        String password = "passworld";
        System.out.println("password" + password);

        String sqlInsert = "INSERT INTO user_table VALUES ('"
                + identifier + "', '"
                + username + "', '"
                + password + "')";

        statement.executeUpdate(sqlInsert);
    }

    /*

    INSERT INTO user_table VALUES ('999', 'juice', 'wrld');


curl --header "Content-Type: application/json" --request POST --data '{"identifier":"8","username":"xx","password":"xcx"}' http://localhost:8080/user-create-instance


     */
}


