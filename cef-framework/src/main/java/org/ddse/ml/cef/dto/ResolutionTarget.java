package org.ddse.ml.cef.dto;

import java.util.List;
import java.util.Map;

public record ResolutionTarget(
        String description,
        String typeHint,
        Map<String, Object> properties) {
}
