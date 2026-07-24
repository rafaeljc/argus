package io.github.rafaeljc.argus.alerts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DirectionTest {

    @Test
    void valueOf_up_matchesDbCheckConstraintLiteral() {
        assertThat(Direction.valueOf("UP")).isEqualTo(Direction.UP);
        assertThat(Direction.UP.name()).isEqualTo("UP");
    }

    @Test
    void valueOf_down_matchesDbCheckConstraintLiteral() {
        assertThat(Direction.valueOf("DOWN")).isEqualTo(Direction.DOWN);
        assertThat(Direction.DOWN.name()).isEqualTo("DOWN");
    }
}
