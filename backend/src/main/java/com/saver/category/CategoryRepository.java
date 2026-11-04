package com.saver.category;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Categorías de sistema + las del usuario. {@code includeDeleted} para el historial. */
    @Query("""
            select c from Category c
            where (c.userId is null or c.userId = :userId)
              and (:includeDeleted = true or c.deletedAt is null)
            order by c.userId nulls first, lower(c.name)
            """)
    List<Category> findVisible(@Param("userId") UUID userId, @Param("includeDeleted") boolean includeDeleted);

    /** Una categoría que el usuario puede usar (de sistema o propia), aunque esté borrada. */
    @Query("select c from Category c where c.id = :id and (c.userId is null or c.userId = :userId)")
    Optional<Category> findAccessible(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByUserIdAndDeletedAtIsNullAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserIdAndDeletedAtIsNullAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);

    @Query("select c from Category c where c.id in :ids")
    List<Category> findAllByIdIn(@Param("ids") Collection<UUID> ids);
}
