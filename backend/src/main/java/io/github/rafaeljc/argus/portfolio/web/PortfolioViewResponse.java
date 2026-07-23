package io.github.rafaeljc.argus.portfolio.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.portfolio.application.PortfolioView;
import io.github.rafaeljc.argus.portfolio.application.Position;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioViewResponse(
        @JsonProperty("as_of_date") LocalDate asOfDate,
        @JsonProperty("total_value") String totalValue,
        @JsonProperty("total_value_pending") boolean totalValuePending,
        List<PositionResponse> positions) {

    public static PortfolioViewResponse from(PortfolioView view) {
        return new PortfolioViewResponse(
                view.asOfDate(),
                view.totalValue().value().toPlainString(),
                view.totalValuePending(),
                view.positions().stream().map(PositionResponse::from).toList());
    }

    public record PositionResponse(
            String ticker,
            String quantity,
            @JsonProperty("last_close_price") String lastClosePrice,
            @JsonProperty("last_close_date") LocalDate lastCloseDate,
            @JsonProperty("position_value") String positionValue,
            @JsonProperty("percent_of_portfolio") BigDecimal percentOfPortfolio,
            @JsonProperty("price_pending") boolean pricePending,
            @JsonProperty("price_stale") boolean priceStale,
            @JsonProperty("stale_since") LocalDate staleSince) {

        static PositionResponse from(Position position) {
            return new PositionResponse(
                    position.ticker().value(),
                    position.quantity().value().toPlainString(),
                    position.lastClosePrice() != null ? position.lastClosePrice().toPlainString() : null,
                    position.lastCloseDate(),
                    position.positionValue() != null ? position.positionValue().value().toPlainString() : null,
                    position.percentOfPortfolio(),
                    position.pricePending(),
                    position.priceStale(),
                    position.staleSince());
        }
    }
}
