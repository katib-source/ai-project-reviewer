package com.aireviewer.web;

import com.aireviewer.application.ReviewService;
import com.aireviewer.project.FileNode;
import com.aireviewer.project.ProjectImportException;
import com.aireviewer.web.dto.ErrorResponse;
import com.aireviewer.web.dto.ProjectImportRequest;
import com.aireviewer.web.dto.ProjectImportResponse;
import com.aireviewer.web.dto.ProjectTreeDto;
import io.javalin.http.Context;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Thin adapter for POST /api/projects. */
public final class ProjectController {
    private final ReviewService service;
    public ProjectController(ReviewService service) { this.service = service; }
    public void importProject(Context ctx) {
        ProjectImportRequest request = ctx.bodyAsClass(ProjectImportRequest.class);
        if (request.path() == null || request.path().isBlank()) {
            ctx.status(400).json(new ErrorResponse("path is required")); return;
        }
        try {
            ReviewService.ImportedProject project = service.importProject(Path.of(request.path()));
            ctx.json(new ProjectImportResponse(project.projectId(), toDto(project.tree())));
        } catch (ProjectImportException | InvalidPathException e) {
            ctx.status(400).json(new ErrorResponse(e.getMessage()));
        }
    }
    private static ProjectTreeDto toDto(FileNode node) {
        return new ProjectTreeDto(node.name(), node.isDirectory() ? "directory" : "file",
                node.sizeInBytes(), node.children().stream().map(ProjectController::toDto).toList());
    }
}
