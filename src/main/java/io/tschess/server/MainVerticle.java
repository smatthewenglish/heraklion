package io.tschess.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> future) throws Exception {

        Future<String> databaseVerticleDeployment = Future.future();
        vertx.deployVerticle(new DatabaseVerticle(), databaseVerticleDeployment.completer());

        databaseVerticleDeployment.compose(id -> {
            Future<String> httpServerVerticleDeployment = Future.future();
            vertx.deployVerticle("io.tschess.server.HttpServerVerticle", new DeploymentOptions(), httpServerVerticleDeployment.completer());
            return httpServerVerticleDeployment;

        }).setHandler(asyncResult -> {
            if (asyncResult.succeeded()) {
                future.complete();
            } else {
                future.fail(asyncResult.cause());
            }
        });
    }
}
