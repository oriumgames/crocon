package games.orium.util;

public enum Edition {
    JAVA("java"),
    BEDROCK("bedrock");

    private final String name;

    Edition(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Edition fromString(String str) {
        for (Edition e : values()) {
            if (e.name.equalsIgnoreCase(str)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown edition: " + str);
    }
}
