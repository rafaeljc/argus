package io.github.rafaeljc.argus.marketdata.application;

public record SymbolSyncResult(int upserted, int delisted, int total) {
}
