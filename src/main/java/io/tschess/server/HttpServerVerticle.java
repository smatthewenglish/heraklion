package io.tschess.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    private static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    private static final String CONFIG_QUEUE = "db.queue";

    private String dbQueue = "db.queue";

    @Override
    public void start(Future<Void> future) {

        dbQueue = config().getString(CONFIG_QUEUE, "db.queue");

        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.post().handler(BodyHandler.create());
        router.post("/game-create-instance").handler(this::gameCreateInstanceHandler);
        router.post("/user-create-instance").handler(this::userCreateInstanceHandler);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server
                .requestHandler(router)
                .listen(portNumber, asyncResult -> {
                    if (asyncResult.succeeded()) {
                        LOGGER.info("HTTP server running on port " + portNumber);
                        future.complete();
                    } else {
                        LOGGER.error("Could not start a HTTP server", asyncResult.cause());
                        future.fail(asyncResult.cause());
                    }
                });
    }

    private void handler(RoutingContext routingContext, String headerValue) {
        JsonObject data = new JsonObject(routingContext.getBodyAsString());

        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", headerValue);

        EventBus eventBus = vertx.eventBus();

        eventBus.send(dbQueue, data, options, asyncResult -> {
            if (asyncResult.succeeded()) {

                JsonObject reply = new JsonObject(asyncResult.result().body().toString());
                System.out.println("Received reply: " + reply.encodePrettily());

                routingContext.response()
                        .putHeader("content-type", "text/html")
                        .end(asyncResult.result().body().toString());
            } else {
                routingContext.fail(asyncResult.cause());
            }
        });
    }

    private void gameCreateInstanceHandler(RoutingContext context) {
        handler(context, "game-create-instance");
    }

    private void userCreateInstanceHandler(RoutingContext context) {
        handler(context, "user-create-instance");
    }
}
