package io.github.rafaeljc.argus.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationTest {

    @Test
    void valueOf_buy_matchesDbCheckConstraintLiteral() {
        assertThat(Operation.valueOf("BUY")).isEqualTo(Operation.BUY);
        assertThat(Operation.BUY.name()).isEqualTo("BUY");
    }

    @Test
    void valueOf_sell_matchesDbCheckConstraintLiteral() {
        assertThat(Operation.valueOf("SELL")).isEqualTo(Operation.SELL);
        assertThat(Operation.SELL.name()).isEqualTo("SELL");
    }
}
