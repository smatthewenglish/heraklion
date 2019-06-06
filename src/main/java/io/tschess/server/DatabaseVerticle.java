package io.tschess.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class DatabaseVerticle extends AbstractVerticle {

    private static final String CONFIG_QUEUE = "db.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);

    private enum SqlQuery {
        GAME_CREATE_TABLE,
        GAME_ACCEPT_CHALLENGE,

        USER_CREATE_TABLE,
        USER_LOGIN,
        USER_ALL
    }

    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {

        InputStream queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");

        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        sqlQueries.put(SqlQuery.GAME_CREATE_TABLE, queriesProps.getProperty("game-create-table"));
        sqlQueries.put(SqlQuery.GAME_ACCEPT_CHALLENGE, queriesProps.getProperty("game-accept-challenge"));

        sqlQueries.put(SqlQuery.USER_CREATE_TABLE, queriesProps.getProperty("user-create-table"));
        sqlQueries.put(SqlQuery.USER_LOGIN, queriesProps.getProperty("user-login"));
        sqlQueries.put(SqlQuery.USER_ALL, queriesProps.getProperty("user-all"));
    }

    private SQLClient dbClient;

    @Override
    public void start(Future<Void> future) throws Exception {
        loadSqlQueries(); // NOTE: This method call uses blocking APIs, but data is small...

        dbClient = PostgreSQLClient.createShared(vertx, new JsonObject()
                .put("username", "s.matthew.english")
                .put("password", "")
                .put("database", "s.matthew.english")
                .put("url", "jdbc:postgresql://localhost:5432")
                .put("driver_class", "org.postgresql.Driver")
                .put("max_pool_size", 30));

        dbClient.getConnection(connectionResult -> {
            if (connectionResult.succeeded()) {
                SQLConnection connection = connectionResult.result();
                connection.execute(sqlQueries.get(SqlQuery.USER_CREATE_TABLE), userCreateTableResult -> {

                    if (userCreateTableResult.succeeded()) {
                        connection.execute(sqlQueries.get(SqlQuery.GAME_CREATE_TABLE), gameCreateTableResult -> {
                            connection.close();

                            if (gameCreateTableResult.succeeded()) {
                                EventBus eventBus = vertx.eventBus();
                                MessageConsumer<JsonObject> consumer = eventBus.consumer(config().getString(CONFIG_QUEUE, "db.queue"));
                                consumer.handler(message -> {
                                    System.out.println("Received message: " + message.body());
                                    onMessage(message);
                                });
                                future.complete();
                            } else {
                                LOGGER.error("Database preparation error", gameCreateTableResult.cause());
                                future.fail(gameCreateTableResult.cause());
                            }
                        });
                    } else {
                        LOGGER.error("Database preparation error", userCreateTableResult.cause());
                        future.fail(userCreateTableResult.cause());
                    }
                });
            } else {
                LOGGER.error("Could not open a database connection", connectionResult.cause());
                future.fail(connectionResult.cause());
            }
        });
    }

    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private void onMessage(Message<JsonObject> message) {
        if (!message.headers().contains("action")) {
            LOGGER.error("No action header specified for message with headers {} and body {}",
                    message.headers(), message.body().encodePrettily());
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }

        String action = message.headers().get("action");

        switch (action) {
            case "user-create-instance":
                userCreateInstance(message);
                break;
            case "user-login":
                userLogin(message);
                break;
            case "user-all":
                userAll(message);
                break;
            //* * *//
            case "game-create-instance":
                gameCreateInstance(message);
                break;
            case "game-accept-challenge":
                gameAcceptChallenge(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }

    private void gameAcceptChallenge(Message<JsonObject> message) {
        String identifier = message.body().getString("identifier");
        String configurationWhite = message.body().getJsonArray("configuration_white").toString();

        String sql = "UPDATE game_table SET game_status = 'ONGOING', configuration_white = '"
                + configurationWhite + "' WHERE identifier = '"
                + identifier
                + "'";

        dbClient.update(sql, asyncResult -> {
            dbClient.close();
            if (asyncResult.failed()) {
                reportQueryError(message, asyncResult.cause());
            } else {
                JsonObject response = new JsonObject();
                response.put("response", "gamestate-update");
                response.put("result", "success");
                message.reply(response);
            }
        });
    }

    private void userAll(Message<JsonObject> message) {

        dbClient.query(sqlQueries.get(SqlQuery.USER_ALL), res -> {
            if (res.succeeded()) {
                List<String> pages = res.result()
                        .getResults()
                        .stream()
                        .map(json -> json.getString(0))
                        .sorted()
                        .collect(Collectors.toList());
                message.reply(new JsonObject()
                        .put("response", "user-all")
                        .put("result", new JsonArray(pages)));
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void userLogin(Message<JsonObject> message) {

        System.out.println("----------");

        JsonArray username = new JsonArray().add(message.body().getString("username"));
        String password = message.body().getString("password");

        dbClient.queryWithParams(
                sqlQueries.get(SqlQuery.USER_LOGIN),
                username,
                validatePasswordResult -> {
                    JsonObject response = new JsonObject();
                    response.put("response", "user-login");
                    if (validatePasswordResult.succeeded()) {
                        ResultSet resultSet = validatePasswordResult.result();
                        if (resultSet.getNumRows() == 0) {
                            response.put("result", "username");
                        } else {
                            String retrievedPassword = resultSet.getResults().get(0).getString(0);
                            if (retrievedPassword.equals(password)) {
                                response.put("result", "success");
                            } else {
                                response.put("result", "password");
                            }
                        }
                        message.reply(response);
                    } else {
                        reportQueryError(message, validatePasswordResult.cause());
                    }
                });
        dbClient.close();
    }

    private void userCreateInstance(Message<JsonObject> message) {

        System.out.println("----------");

        //--------
        String identifier = message.body().getString("identifier");
        System.out.println("identifier" + identifier);

        String username = message.body().getString("username");
        System.out.println("username" + username);

        String password = message.body().getString("password");
        System.out.println("password" + password);

        String sql = "INSERT INTO user_table VALUES ('"
                + identifier + "', '"
                + username + "', '"
                + password + "')";

        dbClient.update(sql, asyncResult -> {
            dbClient.close();
            if (asyncResult.failed()) {
                reportQueryError(message, asyncResult.cause());
            } else {
                JsonObject response = new JsonObject();
                response.put("response", "user-create-instance");
                response.put("result", "success");
                message.reply(response);
            }
        });
    }

    private void gameCreateInstance(Message<JsonObject> message) {

        System.out.println("----------");

        //--------
        String identifier = message.body().getString("identifier");
        System.out.println("identifier" + identifier);

        String usernameWhite = message.body().getString("username_white");
        System.out.println("username_white" + usernameWhite);

        //String configurationWhite = message.body().getJsonArray("configuration_white").toString();
        String configurationWhite = "DEFAULT";
        //--------
        String usernameBlack = message.body().getString("username_black");
        String configurationBlack = message.body().getJsonArray("configuration_black").toString();
        //--------
        String usernameTurn = message.body().getString("username_turn");
        System.out.println("username_turn" + usernameTurn);

        String inviterId = message.body().getString("inviter_id");
        System.out.println("inviter_id" + inviterId);

        String inviteeId = message.body().getString("invitee_id");
        System.out.println("invitee_id" + inviteeId);

        String sql = "INSERT INTO game_table VALUES ('"
                + identifier + "', '"
                + usernameWhite + "', "
                + configurationWhite + ", '"
                + usernameBlack + "', '"
                + configurationBlack + "', '"
                + usernameTurn + "', '"
                + inviterId + "', '"
                + inviteeId + "')";

        dbClient.update(sql, asyncResult -> {
            dbClient.close();
            if (asyncResult.failed()) {
                reportQueryError(message, asyncResult.cause());
            } else {
                JsonObject response = new JsonObject();
                response.put("response", "game-create-instance");
                response.put("result", "success");
                message.reply(response);
            }
        });
    }

    private void reportQueryError(Message<JsonObject> message, Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }
}