package io.casehub.neocortex.memory.experience;

public final class SubThoughtAttributeKeys {
    public static final String COUNT = "sub-thought-count";

    public static String type(int index)      { return "sub-thought-" + index + "-type"; }
    public static String text(int index)      { return "sub-thought-" + index + "-text"; }
    public static String entity(int index)    { return "sub-thought-" + index + "-entity"; }
    public static String graduated(int index) { return "sub-thought-" + index + "-graduated"; }

    public static String confidence(int index) {return "sub-thought-" + index + "-confidence";}


    private SubThoughtAttributeKeys() {}
}
