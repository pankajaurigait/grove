package com.samagra.grove.contracts;

public class ComponentManager {
    public static IGroveLoggingComponent iGroveLoggingComponent;
    public static void registerGroveLoggingComponent(IGroveLoggingComponent groveLoggingComponentImpl) {
        iGroveLoggingComponent = groveLoggingComponentImpl;
    }

}