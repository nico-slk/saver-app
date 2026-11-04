package com.saver.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {}

    public record CategoryResponse(
            UUID id, String systemKey, String name, String color, String icon, boolean system, boolean deleted) {

        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getSystemKey(), c.getName(), c.getColor(), c.getIcon(),
                    c.isSystem(), c.isDeleted());
        }
    }

    public record CreateCategoryRequest(
            @NotBlank(message = "validation.required") @Size(max = 60, message = "validation.too_long") String name,
            @NotBlank(message = "validation.required") @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "category.color_invalid") String color,
            @Size(max = 30) String icon) {}

    public record UpdateCategoryRequest(
            @Size(min = 1, max = 60, message = "validation.too_long") String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "category.color_invalid") String color,
            @Size(max = 30) String icon) {}
}
