package com.metrology.controller;

import com.metrology.entity.UserFile;
import com.metrology.service.PermissionService;
import com.metrology.service.UserFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class UserFileController {

    private final UserFileService service;
    private final PermissionService permissionService;

    private ResponseEntity<?> checkFileAccess(String username) {
        if (!permissionService.hasPermission(username, PermissionService.FILE_ACCESS)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "无权访问文件模块"));
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @AuthenticationPrincipal UserDetails u,
            @RequestParam(required = false) Long parentId) {
        ResponseEntity<?> check = checkFileAccess(u.getUsername());
        if (check != null) return check;
        return ResponseEntity.ok(service.list(u.getUsername(), parentId));
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @AuthenticationPrincipal UserDetails u,
            @RequestParam String q) {
        ResponseEntity<?> check = checkFileAccess(u.getUsername());
        if (check != null) return check;
        return ResponseEntity.ok(service.search(u.getUsername(), q));
    }

    @GetMapping("/breadcrumb")
    public ResponseEntity<?> breadcrumb(
            @AuthenticationPrincipal UserDetails u,
            @RequestParam Long folderId) {
        ResponseEntity<?> check = checkFileAccess(u.getUsername());
        if (check != null) return check;
        return ResponseEntity.ok(service.getBreadcrumb(folderId));
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            @AuthenticationPrincipal UserDetails u,
            @RequestBody Map<String, Object> body) {
        ResponseEntity<?> check = checkFileAccess(u.getUsername());
        if (check != null) return check;
        try {
            String name = (String) body.get("name");
            Long parentId = body.get("parentId") != null
                    ? ((Number) body.get("parentId")).longValue() : null;
            return ResponseEntity.ok(service.createFolder(u.getUsername(), parentId, name));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @AuthenticationPrincipal UserDetails u,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long parentId) throws IOException {
        ResponseEntity<?> check = checkFileAccess(u.getUsername());
        if (check != null) return check;
        return ResponseEntity.ok(service.uploadFile(u.getUsername(), parentId, file));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal UserDetails u,
            @PathVariable Long id) throws IOException {
        if (!permissionService.hasPermission(u.getUsername(), PermissionService.FILE_ACCESS)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UserFile f = service.getFile(u.getUsername(), id);
        byte[] data = service.downloadFile(id);
        String encoded = URLEncoder.encode(f.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        f.getMimeType() != null ? f.getMimeType() : "application/octet-stream"))
                .body(data);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @AuthenticationPrincipal UserDetails u,
            @PathVariable Long id) {
        ResponseEntity<?> check = checkFileAccess(u.getUsername());
        if (check != null) return check;
        try {
            service.delete(u.getUsername(), id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<?> rename(
            @AuthenticationPrincipal UserDetails u,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        ResponseEntity<?> check = checkFileAccess(u.getUsername());
        if (check != null) return check;
        try {
            return ResponseEntity.ok(service.rename(u.getUsername(), id, body.get("name")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
