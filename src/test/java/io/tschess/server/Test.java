package io.tschess.server;

import java.util.concurrent.CompletableFuture;

import static junit.framework.Assert.assertEquals;

public class Test {

    @org.junit.Test
    public void test() {
        System.out.println("-----");

        ConcreteApple concreteApple = new ConcreteApple();

        concreteApple.callOnMe();

    }

    @org.junit.Test
    public void merkem0() throws Exception {
        final CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> doSomethingAndReturnA());

        future.get();
    }

    @org.junit.Test
    public void merkem1() throws Exception {
        final CompletableFuture<Integer> future = CompletableFuture
                .supplyAsync(() -> doSomethingAndReturnA())
                .thenApply(a -> convertToB(a));

        future.get();
    }

    private static int convertToB(final String a) {
        System.out.println("convertToB: " + Thread.currentThread().getName());
        return Integer.parseInt(a);
    }

    private static String doSomethingAndReturnA() {
        System.out.println("doSomethingAndReturnA: " + Thread.currentThread().getName());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "1";
    }

    @org.junit.Test
    public void addition_allYouNeed() {

        assertEquals(666, 665 + 1);

        B b = new B();

        if(!b.go()) {
            System.out.println("A");
        } else {
            System.out.println("B");
        }
    }

    public static abstract class A {

        public boolean go() {

            return true;
        }
    }

    public static class B extends A {

        public boolean go() {
            super.go();

            return false;
        }
    }



}
