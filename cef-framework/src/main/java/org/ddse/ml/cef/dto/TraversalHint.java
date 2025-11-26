package org.ddse.ml.cef.dto;

import org.ddse.ml.cef.domain.Direction;
import java.util.List;

public record TraversalHint(
        int maxDepth,
        List<String> relationTypes,
        Direction direction) {
}
