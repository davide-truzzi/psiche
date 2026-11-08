package it.infog.psiche.common;

import org.junit.jupiter.api.Test;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeFilesTest {

    @Test
    void aStepAnotherProgramHoldsUpIsTriedAgainUntilItWorks() throws Exception {
        AtomicInteger tries = new AtomicInteger();

        String result = SafeFiles.patiently(() -> switch (tries.incrementAndGet()) {
            case 1 -> throw new AccessDeniedException("settings.properties");
            case 2 -> throw new FileSystemException("settings.properties", null, "used by another process");
            default -> "saved";
        });

        assertThat(result).isEqualTo("saved");
        assertThat(tries).hasValue(3);
    }

    @Test
    void aStepThatKeepsBeingHeldUpGivesUpWithItsLastError() {
        AtomicInteger tries = new AtomicInteger();

        assertThatThrownBy(() -> SafeFiles.patiently(() -> {
            throw new AccessDeniedException("try " + tries.incrementAndGet());
        })).isInstanceOf(AccessDeniedException.class).hasMessage("try 5");
    }

    @Test
    void anyOtherFailureIsNotTriedAgain() {
        AtomicInteger tries = new AtomicInteger();

        assertThatThrownBy(() -> SafeFiles.patiently(() -> {
            tries.incrementAndGet();
            throw new NoSuchFileException("settings.properties");
        })).isInstanceOf(NoSuchFileException.class);
        assertThat(tries).hasValue(1);
    }
}
