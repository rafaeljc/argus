package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CollectionEnvelopeTest {

    private static final CollectionEnvelope.Meta META = new CollectionEnvelope.Meta(0, 1, 50, 0);
    private static final CollectionEnvelope.Links LINKS =
            new CollectionEnvelope.Links("/api/v1/items?page=1", null, null, "/api/v1/items?page=1");

    @Test
    void constructor_validArgs_assignsAllFields() {
        List<String> data = List.of("a", "b");

        CollectionEnvelope<String> envelope = new CollectionEnvelope<>(data, META, LINKS);

        assertThat(envelope.data()).containsExactly("a", "b");
        assertThat(envelope.meta()).isEqualTo(META);
        assertThat(envelope.links()).isEqualTo(LINKS);
    }

    static Stream<Arguments> nullComponents() {
        return Stream.of(
                Arguments.of(null, META, LINKS),
                Arguments.of(List.of(), null, LINKS),
                Arguments.of(List.of(), META, null));
    }

    @ParameterizedTest
    @MethodSource("nullComponents")
    void constructor_nullComponent_throwsIllegalArgument(
            List<String> data, CollectionEnvelope.Meta meta, CollectionEnvelope.Links links) {
        assertThatThrownBy(() -> new CollectionEnvelope<>(data, meta, links))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_mutableDataSource_defensivelyCopies() {
        List<String> mutable = new ArrayList<>();
        mutable.add("a");

        CollectionEnvelope<String> envelope = new CollectionEnvelope<>(mutable, META, LINKS);
        mutable.clear();

        assertThat(envelope.data()).containsExactly("a");
    }

    @Test
    void data_returnsUnmodifiableList() {
        CollectionEnvelope<String> envelope = new CollectionEnvelope<>(List.of("a"), META, LINKS);

        assertThatThrownBy(() -> envelope.data().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void meta_constructor_assignsAllFields() {
        CollectionEnvelope.Meta meta = new CollectionEnvelope.Meta(120, 2, 50, 3);

        assertThat(meta.total()).isEqualTo(120);
        assertThat(meta.page()).isEqualTo(2);
        assertThat(meta.perPage()).isEqualTo(50);
        assertThat(meta.totalPages()).isEqualTo(3);
    }

    @Test
    void links_constructor_nullSelf_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CollectionEnvelope.Links(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self");
    }

    @Test
    void links_constructor_optionalNextPrevLast_allowed() {
        CollectionEnvelope.Links links = new CollectionEnvelope.Links("/self", null, null, null);

        assertThat(links.self()).isEqualTo("/self");
        assertThat(links.next()).isNull();
        assertThat(links.prev()).isNull();
        assertThat(links.last()).isNull();
    }
}
