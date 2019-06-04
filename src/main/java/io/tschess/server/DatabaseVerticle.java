package io.tschess.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class DatabaseVerticle extends AbstractVerticle {

    private static final String CONFIG_QUEUE = "db.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);

    private enum SqlQuery {
        GAME_CREATE_TABLE
    }

    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {

        InputStream queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");

        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        sqlQueries.put(SqlQuery.GAME_CREATE_TABLE, queriesProps.getProperty("game-create-table"));
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
                connection.execute(sqlQueries.get(SqlQuery.GAME_CREATE_TABLE), createUsersTableResult -> {

                    if (createUsersTableResult.succeeded()) {
                        connection.close();

                        EventBus eventBus = vertx.eventBus();
                        MessageConsumer<JsonObject> consumer = eventBus.consumer(config().getString(CONFIG_QUEUE, "db.queue"));

                        consumer.handler(message -> {
                            System.out.println("Received message: " + message.body());
                            onMessage(message);
                        });
                        future.complete();
                    } else {
                        LOGGER.error("Database preparation error", createUsersTableResult.cause());
                        future.fail(createUsersTableResult.cause());
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
            case "game-create-instance":
                gameCreateInstance(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }

    private void gameCreateInstance(Message<JsonObject> message) {

        //--------
        String identifier = message.body().getString("identifier");
        String usernameWhite = message.body().getString("username_white");
        String configurationWhite = message.body().getJsonArray("configuration_white").toString();
        //--------
        String usernameBlack = message.body().getString("username_black");
        String configurationBlack = message.body().getJsonArray("configuration_black").toString();
        //--------
        String usernameTurn = message.body().getString("username_turn");
        String inviterId = message.body().getString("inviter_id");
        String inviteeId = message.body().getString("invitee_id");
        //--------
        String gamestate = message.body().getJsonArray("gamestate").toString();
        String gameStatus = "DEFAULT";
        String winner = "DEFAULT";

        String sql = "INSERT INTO game_table VALUES ('" + identifier + "', '"
                + usernameWhite + "', '"
                + configurationWhite + "', '"
                + usernameBlack + "', '"
                + configurationBlack + "', '"
                + gameStatus + "')";

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