# Tschess Server

Everything you need to know to be a core contributor to Tschess.

----

`update games set turn = 'test', accepted = 'true' where identifier = '2cd57300-49ef-b69a-b1d2-ef1a00235e27';`

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Database

You need to create a file, named like so: `src/main/conf/my-application-conf.json`, with the following characteristics:

```
{
  "_comment": "define these variables according to your local postgresql configuration",
  "username" : "YOUR_USERNAME_HERE",
  "database" : "YOUR_DATABASE_NAME_HERE"
}
```

**NOTE:** This example is for a database configuration with no password, if you have a password
for your local JDBC you can define that here, if you *do* decide to do that, be sure to
set the corresponding code in the file `DatabaseVerticle.java`, in the lines described 
by the operation that includes the following snippet: `dbClient = PostgreSQLClient.createShared(vertx, new JsonObject()`.

### Run Command

Run the server with the following command: 

```
./gradlew clean && ./gradlew fatJar && java -jar ./build/libs/server-all-1.0-SNAPSHOT.jar
```

PostgreSQL can be run locally like so: 

```
psql -h localhost
```


### Postgresql Configuration

Important folder: 

```
/etc/postgresql/10/main
```

blah...

```
pg_hba.conf
```

currently on aws, looks like so:

```
# Database administrative login by Unix domain socket
local   all             all                                     trust

# TYPE  DATABASE        USER            ADDRESS                 METHOD

# "local" is for Unix domain socket connections only
local   all             all                                     trust
# IPv4 local connections:
host    all             all             127.0.0.1/32            trust
# IPv6 local connections:
host    all             all             ::1/128                 trust
# Allow replication connections from localhost, by a user with the
# replication privilege.
local   replication     all                                     trust
host    replication     all             127.0.0.1/32            trust
host    replication     all             ::1/128                 trust
```

---
to disable passwords...

```
sudo -u postgres psql -w
psql (10.6 (Ubuntu 10.6-0ubuntu0.18.04.1))
Type "help" for help.
```

---

```
$ sudo -u postgres psql -w

# ALTER USER postgres WITH PASSWORD '';
```
```
postgres=# ALTER USER postgres WITH PASSWORD '';
NOTICE:  empty string is not a valid password, clearing password
ALTER ROLE
postgres=# ALTER USER ubuntu WITH PASSWORD '';
NOTICE:  empty string is not a valid password, clearing password
ALTER ROLE
```

---

```
HelloWorld
```


```
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class HelloWorld {
    static final String JDBC_DRIVER = "org.postgresql.Driver";
    static final String DB_URL = "jdbc:postgresql://localhost:5432/";

    static Connection connection = null;
    static Statement statement = null;

    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World");

        // Register JDBC driver
        Class.forName(JDBC_DRIVER);

        // Open a connection
        System.out.println("connect");
        connection = DriverManager.getConnection(DB_URL);

        System.out.println("commenceGame");
        statement = connection.createStatement();
        String sqlInsert = "INSERT INTO games VALUES ('a503abbe-98bc-fd1b-1449-02f0a76dcbed', 'prc', 'cgn', DEFAULT, DEFAULT, DEFAULT)";
        statement.executeUpdate(sqlInsert);
    }
}
```


ran it like so:

```
javac -cp .:postgresql-42.2.5.jar HelloWorld.java
java -cp .:postgresql-42.2.5.jar HelloWorld
```

ref.

```
DELETE FROM games WHERE identifier = 'a503abbe-98bc-fd1b-1449-02f0a76dcbed';
```