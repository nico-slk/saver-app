package com.saver.category;

import com.saver.category.CategoryDtos.CreateCategoryRequest;
import com.saver.category.CategoryDtos.UpdateCategoryRequest;
import com.saver.common.ApiException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final Clock clock;

    public CategoryService(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Category> list(UUID userId, boolean includeDeleted) {
        return categories.findVisible(userId, includeDeleted);
    }

    @Transactional
    public Category create(UUID userId, CreateCategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByUserIdAndDeletedAtIsNullAndNameIgnoreCase(userId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "category.name_taken");
        }
        return categories.save(new Category(userId, name, req.color().toLowerCase(), req.icon(), clock.instant()));
    }

    @Transactional
    public Category update(UUID userId, UUID id, UpdateCategoryRequest req) {
        Category c = ownedActive(userId, id);
        if (req.name() != null) {
            String name = req.name().trim();
            if (categories.existsByUserIdAndDeletedAtIsNullAndNameIgnoreCaseAndIdNot(userId, name, id)) {
                throw new ApiException(HttpStatus.CONFLICT, "category.name_taken");
            }
            c.setName(name);
        }
        if (req.color() != null) c.setColor(req.color().toLowerCase());
        if (req.icon() != null) c.setIcon(req.icon());
        return c;
    }

    /** Borrado lógico: deja de ofrecerse para gastos nuevos, el historial la conserva. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        ownedActive(userId, id).markDeleted(clock.instant());
    }

    /** Valida que la categoría se pueda asignar a un gasto nuevo o editado. */
    @Transactional(readOnly = true)
    public Category requireUsable(UUID userId, UUID id) {
        Category c = categories.findAccessible(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "category.not_found"));
        if (c.isDeleted()) throw new ApiException(HttpStatus.BAD_REQUEST, "category.deleted");
        return c;
    }

    private Category ownedActive(UUID userId, UUID id) {
        Category c = categories.findAccessible(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "category.not_found"));
        if (c.isSystem()) throw new ApiException(HttpStatus.FORBIDDEN, "category.system_readonly");
        if (c.isDeleted()) throw new ApiException(HttpStatus.NOT_FOUND, "category.not_found");
        return c;
    }
}
