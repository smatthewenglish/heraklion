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
import java.util.ArrayList;
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
            case "game-cancel-outbound-invitation":
                gameCancelOutboundInvitation(message);
                break;
            case "game-create-instance":
                gameCreateInstance(message);
                break;
            case "game-accept-challenge":
                gameAcceptChallenge(message);
                break;
            case "game-all":
                gameAll(message);
                break;
            case "game-update-gamestate":
                gameUpdateGamestate(message);
                break;
            case "user-update-saved-configuration":
                userUpdateConfiguration(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }

    private void userUpdateConfiguration(Message<JsonObject> message){
        String username = message.body().getString("username");
        JsonArray savedConfiguration = message.body().getJsonArray("saved_configuration");

        String sql = "UPDATE user_table SET saved_configuration = '"
                + savedConfiguration + "' WHERE username =  '"
                + username + "'";

        dbClient.update(sql, asyncResult -> {
            dbClient.close();
            if (asyncResult.failed()) {
                reportQueryError(message, asyncResult.cause());
            } else {
                JsonObject response = new JsonObject();
                response.put("response", "user-update-saved-configuration");
                response.put("result", "success");
                message.reply(response);
            }
        });
    }

    private void gameCancelOutboundInvitation(Message<JsonObject> message) {
        String identifier = message.body().getString("identifier");

        String sql = "DELETE FROM game_table WHERE identifier = '" + identifier + "'";

        dbClient.update(sql, asyncResult -> {
            dbClient.close();
            if (asyncResult.failed()) {
                reportQueryError(message, asyncResult.cause());
            } else {
                JsonObject response = new JsonObject();
                response.put("response", "game-cancel-outbound-invitation");
                response.put("result", "success");
                message.reply(response);
            }
        });

    }

    private void gameAll(Message<JsonObject> message) {

        System.out.println("----------");

        String username = message.body().getString("username");

        String sql0 = "SELECT identifier, username_black, configuration_inviter, username_turn, inviter_id, invitee_id, game_status, gamestate, winner FROM game_table WHERE username_white = '" + username + "'";

        dbClient.query(sql0, res0 -> {
            if (res0.succeeded()) {
                List<JsonArray> pages0 = res0.result().getResults();

                String sql1 = "SELECT identifier, username_white, configuration_inviter, username_turn, inviter_id, invitee_id, game_status, gamestate, winner FROM game_table WHERE username_black = '" + username + "'";

                dbClient.query(sql1, res1 -> {
                    if (res1.succeeded()) {
                        List<JsonArray> pages1 = res1.result().getResults();


                        System.out.println("pages0.size(): " + pages0.size());
                        System.out.println("pages1.size(): " + pages1.size());


                        JsonObject response = new JsonObject();
                        response.put("response", "game-all")
                                .put("result", "success");

                        JsonArray gameAllList = new JsonArray();


                        for (int i = 0; i < pages0.size(); i++) {

                            System.out.println("pages0.get(i).size(): " + pages0.get(i).size());

                            JsonObject game = new JsonObject();
                            game.put("identifier", pages0.get(i).getValue(0));
                            game.put("username_white", username);
                            game.put("username_black", pages0.get(i).getValue(1));
                            game.put("configuration_inviter", pages0.get(i).getValue(2));
                            game.put("username_turn", pages0.get(i).getValue(3));
                            game.put("inviter_id", pages0.get(i).getValue(4));
                            game.put("invitee_id", pages0.get(i).getValue(5));
                            game.put("game_status", pages0.get(i).getValue(6));
                            game.put("gamestate", pages0.get(i).getValue(7));
                            game.put("winner", pages0.get(i).getValue(8));

                            gameAllList.add(game);
                        }

                        for (int i = 0; i < pages1.size(); i++) {


                            System.out.println("pages1.get(i).size(): " + pages1.get(i).size());

                            JsonObject game = new JsonObject();
                            game.put("identifier", pages1.get(i).getValue(0));
                            game.put("username_white", pages1.get(i).getValue(1));
                            game.put("username_black", username);
                            game.put("configuration_inviter", pages1.get(i).getValue(2));
                            game.put("username_turn", pages1.get(i).getValue(3));
                            game.put("inviter_id", pages1.get(i).getValue(4));
                            game.put("invitee_id", pages1.get(i).getValue(5));
                            game.put("game_status", pages1.get(i).getValue(6));
                            game.put("gamestate", pages1.get(i).getValue(7));
                            game.put("winner", pages1.get(i).getValue(8));

                            gameAllList.add(game);
                        }


                        response.put("game-all", gameAllList);
                        message.reply(response);

                    } else {
                        reportQueryError(message, res0.cause());
                    }
                });
            } else {
                reportQueryError(message, res0.cause());
            }
        });
    }

    private void gameUpdateGamestate(Message<JsonObject> message) {
        String identifier = message.body().getString("identifier");
        String usernameTurn = message.body().getString("username_turn");
        String gamestate = message.body().getJsonArray("gamestate").toString();

        String sql = "UPDATE game_table SET gamestate = '"
                + gamestate + "', username_turn =  '" + usernameTurn +
                "' WHERE identifier = '"
                + identifier
                + "'";

        dbClient.update(sql, asyncResult -> {
            dbClient.close();
            if (asyncResult.failed()) {
                reportQueryError(message, asyncResult.cause());
            } else {
                JsonObject response = new JsonObject();
                response.put("response", "game-update-gamestate");
                response.put("result", "success");
                message.reply(response);
            }
        });

    }

    private void gameAcceptChallenge(Message<JsonObject> message) {
        String identifier = message.body().getString("identifier");

        String sql0 = "UPDATE game_table SET game_status = 'ONGOING' WHERE identifier = '"
                + identifier
                + "'";

        dbClient.update(sql0, asyncResult0 -> {
            dbClient.close();
            if (asyncResult0.failed()) {
                reportQueryError(message, asyncResult0.cause());
            } else {

                String sql1 = "SELECT " +
                        "identifier, " +
                        "username_white, " +
                        "username_black, " +
                        "configuration_inviter, " +
                        "username_turn, " +
                        "inviter_id, " +
                        "invitee_id, " +
                        "game_status, " +
                        "gamestate, " +
                        "winner " +
                        "FROM game_table " +
                        "WHERE identifier = '" + identifier + "'";

                dbClient.query(sql1, asyncResult1 -> {
                    if (asyncResult1.succeeded()) {

                        List<JsonArray> pages = asyncResult1.result().getResults();

                        JsonObject game = new JsonObject();
                        game.put("response", "game-accept-challenge");
                        game.put("result", "success");
                        game.put("identifier", pages.get(0).getValue(0));
                        game.put("username_white", pages.get(0).getValue(1));
                        game.put("username_black", pages.get(0).getValue(2));
                        game.put("configuration_inviter", pages.get(0).getValue(3));
                        game.put("username_turn", pages.get(0).getValue(4));
                        game.put("inviter_id", pages.get(0).getValue(5));
                        game.put("invitee_id", pages.get(0).getValue(6));
                        game.put("game_status", pages.get(0).getValue(7));
                        game.put("gamestate", pages.get(0).getValue(8));
                        game.put("winner", pages.get(0).getValue(9));

//                        List<String> pages = asyncResult1.result()
//                                .getResults()
//                                .stream()
//                                .map(json -> json.getString(0))
//                                .sorted()
//                                .collect(Collectors.toList());
//                        message.reply(new JsonObject()
//                                .put("response", "user-all")
//                                .put("result", "success")
//                                .put("user-all", new JsonArray(pages)));
                    } else {
                        reportQueryError(message, res.cause());
                    }
                });

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
                        .put("result", "success")
                        .put("user-all", new JsonArray(pages)));
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

                                String sql = "SELECT " +
                                        "identifier, " +
                                        "avatar_url, " +
                                        "elo_score, " +
                                        "saved_configuration " +
                                        "FROM user_table " +
                                        "WHERE username = '" + message.body().getString("username") + "'";

                                dbClient.query(sql, asyncResult -> {
                                    dbClient.close();
                                    if (asyncResult.failed()) {

                                        System.out.println("FUCK");

                                        reportQueryError(message, asyncResult.cause());
                                    } else {

                                        System.out.println("999");

                                        List<JsonArray> pages = asyncResult.result().getResults();

                                        response.put("identifier", pages.get(0).getValue(0));
                                        System.out.println("identifier " + pages.get(0).getValue(0));

                                        response.put("username", message.body().getString("username"));
                                        System.out.println("username " + message.body().getString("username"));

                                        response.put("avatar_url", pages.get(0).getValue(1));
                                        System.out.println("avatar_url " + pages.get(0).getValue(1));

                                        response.put("elo_score", pages.get(0).getValue(2));
                                        System.out.println("avatar_url " + pages.get(0).getValue(2));

                                        response.put("saved_configuration", pages.get(0).getValue(3));
                                        System.out.println("avatar_url " + pages.get(0).getValue(3));

                                        message.reply(response);
                                    }
                                });

                                //^^^^^^

                            } else {
                                response.put("result", "password");
                            }
                        }
                    } else {
                        reportQueryError(message, validatePasswordResult.cause());
                    }
                });
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
        //--------
        String usernameBlack = message.body().getString("username_black");
        String configurationInviter = message.body().getJsonArray("configuration_inviter").toString();
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
                + usernameBlack + "', '"
                + configurationInviter + "', '"
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