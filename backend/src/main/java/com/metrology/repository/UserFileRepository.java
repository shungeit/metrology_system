package com.metrology.repository;

import com.metrology.entity.UserFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFileRepository extends JpaRepository<UserFile, Long> {

    List<UserFile> findByUserIdAndParentIdOrderByTypeAscNameAsc(String userId, Long parentId);

    List<UserFile> findByUserIdAndNameContainingIgnoreCaseOrderByTypeAscNameAsc(String userId, String name);

    List<UserFile> findByParentId(Long parentId);

    boolean existsByUserIdAndParentIdAndName(String userId, Long parentId, String name);
}
