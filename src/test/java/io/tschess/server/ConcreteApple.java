package io.tschess.server;

public class ConcreteApple extends AbstractApple {
    String variable = "D";

    public void callOnMe() {
        System.out.println("B");

        super.callOnMe();

        System.out.println("C");
    }
}
