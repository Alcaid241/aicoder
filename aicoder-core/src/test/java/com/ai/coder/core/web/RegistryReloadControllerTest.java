package com.ai.coder.core.web;

import com.ai.coder.core.registry.AbstractDynamicModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistryReloadControllerTest {

    @Test
    void reload_delegates_to_registry() {
        AbstractDynamicModelRegistry registry = mock(AbstractDynamicModelRegistry.class);
        RegistryReloadController controller = new RegistryReloadController(registry);

        ResponseEntity<Map<String, String>> resp = controller.reload();

        verify(registry).reload();
        assertEquals(200, resp.getStatusCode().value());
    }
}
