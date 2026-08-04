package io.github.rafaeljc.argus.portfolio.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.Collection;
import java.util.Set;

// Read-only facade for peer modules that need the distinct set of tickers held across a group
// of users, deduped in a single round trip regardless of holder count.
public interface HeldTickers {

    Set<Ticker> findForUserIds(Collection<UserId> userIds);
}
