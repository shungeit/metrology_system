package com.metrology.service;

import com.metrology.entity.UserFile;
import com.metrology.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserFileService {

    private final UserFileRepository repo;

    @Value("${upload.path:/app/uploads}")
    private String uploadPath;

    public List<UserFile> list(String userId, Long parentId) {
        return repo.findByUserIdAndParentIdOrderByTypeAscNameAsc(userId, parentId);
    }

    public List<UserFile> search(String userId, String q) {
        if (q == null || q.isBlank()) return List.of();
        return repo.findByUserIdAndNameContainingIgnoreCaseOrderByTypeAscNameAsc(userId, q);
    }

    public UserFile getFile(String userId, Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
    }

    public UserFile createFolder(String userId, Long parentId, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("文件夹名称不能为空");
        if (repo.existsByUserIdAndParentIdAndName(userId, parentId, name.trim())) {
            throw new IllegalArgumentException("同名文件夹已存在");
        }
        UserFile f = new UserFile();
        f.setUserId(userId);
        f.setParentId(parentId);
        f.setName(name.trim());
        f.setType("FOLDER");
        return repo.save(f);
    }

    public UserFile uploadFile(String userId, Long parentId, MultipartFile file) throws IOException {
        File dir = new File(uploadPath + "/user-files");
        if (!dir.exists()) dir.mkdirs();

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = original.contains(".") ? original.substring(original.lastIndexOf(".")) : "";
        String filename = UUID.randomUUID() + ext;
        File dest = new File(dir, filename);
        file.transferTo(dest);

        UserFile f = new UserFile();
        f.setUserId(userId);
        f.setParentId(parentId);
        f.setName(original);
        f.setType("FILE");
        f.setFilePath("/user-files/" + filename);
        f.setFileSize(file.getSize());
        f.setMimeType(file.getContentType());
        return repo.save(f);
    }

    public UserFile rename(String userId, Long id, String newName) {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("名称不能为空");
        UserFile f = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        f.setName(newName.trim());
        return repo.save(f);
    }

    public void delete(String userId, Long id) {
        UserFile f = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        if ("FOLDER".equals(f.getType())) {
            deleteChildrenRecursive(id);
        } else {
            if (f.getFilePath() != null) {
                new File(uploadPath + f.getFilePath()).delete();
            }
        }
        repo.deleteById(id);
    }

    private void deleteChildrenRecursive(Long parentId) {
        repo.findByParentId(parentId).forEach(child -> {
            if ("FOLDER".equals(child.getType())) {
                deleteChildrenRecursive(child.getId());
            } else if (child.getFilePath() != null) {
                new File(uploadPath + child.getFilePath()).delete();
            }
            repo.deleteById(child.getId());
        });
    }

    public byte[] downloadFile(Long id) throws IOException {
        UserFile f = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        if (!"FILE".equals(f.getType())) throw new IllegalArgumentException("不是文件");
        File file = new File(uploadPath + f.getFilePath());
        if (!file.exists()) throw new IllegalArgumentException("文件已被移除");
        return Files.readAllBytes(file.toPath());
    }

    public List<Map<String, Object>> getBreadcrumb(Long folderId) {
        List<Map<String, Object>> crumbs = new ArrayList<>();
        Long current = folderId;
        while (current != null) {
            Optional<UserFile> opt = repo.findById(current);
            if (opt.isEmpty()) break;
            UserFile f = opt.get();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", f.getId());
            c.put("name", f.getName());
            crumbs.add(0, c);
            current = f.getParentId();
        }
        return crumbs;
    }
}
