package ca.nio.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(BackendApplication.class).verify();
    }

}
