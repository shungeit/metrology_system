package com.metrology.controller;

import com.metrology.dto.LoginResponse;
import com.metrology.dto.RegisterRequest;
import com.metrology.entity.User;
import com.metrology.entity.UserPermission;
import com.metrology.repository.UserPermissionRepository;
import com.metrology.repository.UserRepository;
import com.metrology.service.AuthService;
import com.metrology.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserRepository userRepository;
    private final UserPermissionRepository permissionRepository;
    private final PermissionService permissionService;
    private final AuthService authService;

    /** 管理员创建新用户 */
    @PostMapping
    public ResponseEntity<?> createUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        if (!permissionService.hasPermission(userDetails.getUsername(), PermissionService.USER_MANAGE)) {
            return ResponseEntity.status(403).body(Map.of("message", "权限不足"));
        }
        try {
            RegisterRequest req = new RegisterRequest();
            req.setUsername((String) body.get("username"));
            req.setPassword((String) body.get("password"));
            LoginResponse res = authService.register(req);

            // Set role and permissions if provided
            String role = (String) body.get("role");
            String department = (String) body.get("department");
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) body.get("permissions");
            User newUser = userRepository.findByUsername(res.getUsername()).orElseThrow();
            if (role != null && !role.isEmpty()) {
                newUser.setRole(role);
            }
            if (department != null) {
                newUser.setDepartment(department.isBlank() ? null : department);
            }
            userRepository.save(newUser);
            if (permissions != null && !"ADMIN".equals(role)) {
                for (String p : permissions) {
                    permissionRepository.save(new UserPermission(null, newUser.getId(), p));
                }
            }

            return ResponseEntity.ok(Map.of("message", "用户创建成功", "username", res.getUsername()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal UserDetails userDetails) {
        if (!permissionService.hasPermission(userDetails.getUsername(), PermissionService.USER_MANAGE)) {
            return ResponseEntity.status(403).body(Map.of("message", "权限不足"));
        }
        List<Map<String, Object>> result = userRepository.findAll().stream().map(u -> {
            List<String> perms = permissionRepository.findByUserId(u.getId())
                    .stream().map(UserPermission::getPermission).collect(Collectors.toList());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole() != null ? u.getRole() : "USER");
            m.put("department", u.getDepartment() != null ? u.getDepartment() : "");
            m.put("permissions", perms);
            m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/role-permissions")
    public ResponseEntity<?> updateRolePermissions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (!permissionService.hasPermission(userDetails.getUsername(), PermissionService.USER_MANAGE)) {
            return ResponseEntity.status(403).body(Map.of("message", "权限不足"));
        }
        User user = userRepository.findById(id).orElseThrow();
        String role = (String) body.get("role");
        String department = (String) body.get("department");
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) body.get("permissions");

        if (role != null) {
            user.setRole(role);
        }
        if (body.containsKey("department")) {
            user.setDepartment(department != null && !department.isBlank() ? department : null);
        }
        userRepository.save(user);
        if (permissions != null) {
            permissionRepository.deleteByUserId(id);
            for (String p : permissions) {
                UserPermission up = new UserPermission(null, id, p);
                permissionRepository.save(up);
            }
        }
        return ResponseEntity.ok(Map.of("message", "更新成功"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        if (!permissionService.hasPermission(userDetails.getUsername(), PermissionService.USER_MANAGE)) {
            return ResponseEntity.status(403).body(Map.of("message", "权限不足"));
        }
        User current = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();
        if (current.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能删除当前登录账号"));
        }
        permissionRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
