package com.aireviewer.web;

import com.aireviewer.application.ReviewService;
import com.aireviewer.project.FileNode;
import com.aireviewer.project.ProjectImportException;
import com.aireviewer.web.dto.ErrorResponse;
import com.aireviewer.web.dto.ProjectImportRequest;
import com.aireviewer.web.dto.ProjectImportResponse;
import com.aireviewer.web.dto.ProjectTreeDto;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/** Thin adapter for POST /api/projects and POST /api/projects/upload. */
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
    /**
     * POST /api/projects/upload: multipart form, one {@code files} part per file, each part's
     * filename carrying the file's path relative to the chosen folder (e.g.
     * {@code my-project/src/Main.java}). Path safety and size limits are enforced by the service.
     */
    public void uploadProject(Context ctx) {
        List<UploadedFile> parts;
        try {
            parts = ctx.uploadedFiles("files");
        } catch (IllegalStateException e) {
            // Jetty refuses a request over the configured multipart size or part-count limits.
            ctx.status(413).json(new ErrorResponse("Upload too large: " + e.getMessage())); return;
        }
        if (parts.isEmpty()) {
            ctx.status(400).json(new ErrorResponse("No files were uploaded")); return;
        }
        List<ReviewService.UploadedFile> files = parts.stream()
                .map(part -> new ReviewService.UploadedFile(part.filename(), part::content))
                .toList();
        try {
            ReviewService.ImportedProject project = service.importUploadedProject(files);
            ctx.json(new ProjectImportResponse(project.projectId(), toDto(project.tree())));
        } catch (ProjectImportException e) {
            ctx.status(400).json(new ErrorResponse(e.getMessage()));
        }
    }
    private static ProjectTreeDto toDto(FileNode node) {
        return new ProjectTreeDto(node.name(), node.isDirectory() ? "directory" : "file",
                node.sizeInBytes(), node.children().stream().map(ProjectController::toDto).toList());
    }
}
