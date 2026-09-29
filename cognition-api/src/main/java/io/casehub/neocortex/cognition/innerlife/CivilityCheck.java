package io.casehub.neocortex.cognition.innerlife;

public sealed interface CivilityCheck {
    record Permitted() implements CivilityCheck {}
    record Denied(String reason) implements CivilityCheck {}
}
