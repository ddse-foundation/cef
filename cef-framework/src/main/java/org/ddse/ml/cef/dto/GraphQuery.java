package org.ddse.ml.cef.dto;

import java.util.List;

public record GraphQuery(
        List<ResolutionTarget> targets,
        TraversalHint traversal) {
}
