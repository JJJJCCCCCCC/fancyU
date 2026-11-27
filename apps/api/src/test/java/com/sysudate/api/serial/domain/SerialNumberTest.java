package com.sysudate.api.serial.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SerialNumberTest {
    private static final String VALID_SERIAL =
        "SYSU-550E8400-E29B-41D4-A716-446655440000";
    private static final Pattern SERIAL_PATTERN = Pattern.compile(
        "^SYSU-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");

    @Test
    void generatesUppercaseSysuSerialWithStandardUuidShape() {
        SerialNumber serial = SerialNumber.generate();

        assertThat(serial.value()).matches(SERIAL_PATTERN);
    }

    @Test
    void parsesTrimmedLowercaseSerialIntoCanonicalForm() {
        SerialNumber serial =
            SerialNumber.parse(" sysu-550e8400-e29b-41d4-a716-446655440000 ");

        assertThat(serial.value()).isEqualTo(VALID_SERIAL);
    }

    @Test
    void parseRejectsUnicodeCharacterThatUppercasesToAscii() {
        String raw = "\u017Fysu-550e8400-e29b-41d4-a716-446655440000";

        assertThatThrownBy(() -> SerialNumber.parse(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid serial number");
    }

    @Test
    void parseRejectsUnicodeLigatureThatExpandsToAscii() {
        String raw = "sysu-550e8400-e29b-41d4-a716-4466554400\uFB00";

        assertThatThrownBy(() -> SerialNumber.parse(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid serial number");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        " ",
        "SYSU-123",
        "SYSU-550E8400-E29B-41D4-A716-446655440000X",
        "SYSU-550E8400-E29B-41D4-A716-44665544000G"
    })
    void parseRejectsInvalidSerialWithoutEchoingInput(String raw) {
        assertThatThrownBy(() -> SerialNumber.parse(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid serial number");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "sysu-550e8400-e29b-41d4-a716-446655440000",
        " SYSU-550E8400-E29B-41D4-A716-446655440000 ",
        "SYSU-550E8400-E29B-41D4-A716-44665544000Z"
    })
    void constructorRejectsNonCanonicalSerialWithoutEchoingInput(String value) {
        assertThatThrownBy(() -> new SerialNumber(value))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid serial number");
    }
}
