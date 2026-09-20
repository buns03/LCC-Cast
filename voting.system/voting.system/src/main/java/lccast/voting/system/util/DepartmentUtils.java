package lccast.voting.system.util;

import lccast.voting.system.model.Department;

public final class DepartmentUtils {

    private DepartmentUtils() {}

    public static String extractProgramCode(Department department) {
        if (department.getCode() != null && !department.getCode().isBlank()) {
            return department.getCode().trim().toUpperCase();
        }

        String title = department.getTitle();
        if (title == null || title.isBlank()) return null;

        String code = title.split("-", 2)[0].trim();
        return code.isBlank() ? null : code.toUpperCase();
    }
}