package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IdWrappersTest {

    private record IdType(String name, Function<UUID, ?> factory, Function<Object, UUID> reader) {
    }

    private static List<IdType> idTypes() {
        return List.of(
                new IdType("UserId", UserId::new, o -> ((UserId) o).value()),
                new IdType("SessionId", SessionId::new, o -> ((SessionId) o).value()),
                new IdType("TransactionId", TransactionId::new, o -> ((TransactionId) o).value()),
                new IdType("RuleId", RuleId::new, o -> ((RuleId) o).value()),
                new IdType("FiringId", FiringId::new, o -> ((FiringId) o).value()),
                new IdType("OutboxId", OutboxId::new, o -> ((OutboxId) o).value()),
                new IdType("RunId", RunId::new, o -> ((RunId) o).value()),
                new IdType("JobId", JobId::new, o -> ((JobId) o).value()),
                new IdType("VerificationId", VerificationId::new, o -> ((VerificationId) o).value()),
                new IdType("ResetId", ResetId::new, o -> ((ResetId) o).value()),
                new IdType("AuditEntryId", AuditEntryId::new, o -> ((AuditEntryId) o).value())
        );
    }

    static Stream<Arguments> idTypesArgs() {
        return idTypes().stream().map(t -> Arguments.of(t.name(), t));
    }

    @ParameterizedTest(name = "{0} rejects null UUID")
    @MethodSource("idTypesArgs")
    void constructor_null_throwsIllegalArgument(String name, IdType type) {
        assertThatThrownBy(() -> type.factory().apply(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} stores UUID verbatim")
    @MethodSource("idTypesArgs")
    void constructor_validUuid_storesValue(String name, IdType type) {
        UUID uuid = UUID.randomUUID();
        Object id = type.factory().apply(uuid);
        assertThat(type.reader().apply(id)).isEqualTo(uuid);
    }
}
