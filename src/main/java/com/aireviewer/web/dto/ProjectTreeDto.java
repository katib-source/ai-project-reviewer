package com.aireviewer.web.dto;
import java.util.List;
public record ProjectTreeDto(String name, String type, long sizeInBytes, List<ProjectTreeDto> children) {
    public ProjectTreeDto { children = children == null ? List.of() : List.copyOf(children); }
}
