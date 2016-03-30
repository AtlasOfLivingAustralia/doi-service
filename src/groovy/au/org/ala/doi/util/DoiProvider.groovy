package au.org.ala.doi.util

enum DoiProvider {

    ANDS

    static DoiProvider byName(String name) {
        values().find { it.name().equalsIgnoreCase(name) }
    }
}