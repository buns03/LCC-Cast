package lccast.voting.system.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProgramCourseNormalizer {

    private ProgramCourseNormalizer() {}

    /** "Marketing Management - BSBA - MKT" -> "BSBA", "BSED - Math" -> "EDUC", "AB English" -> "BAEL" */
    public static String toDepartmentCode(String programCourse) {
        if (programCourse == null) return "";
        String raw = programCourse.trim().toUpperCase(Locale.ROOT);
        if (raw.isEmpty()) return "";

        String compact = raw.replaceAll("[^A-Z0-9]", "");
        if (compact.equals("BAEL") || compact.startsWith("ABENGLISH")) return "BAEL";

        Set<String> tokens = Arrays.stream(raw.split("[^A-Z0-9]+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());

        if (tokens.contains("BSBA")) return "BSBA";
        if (tokens.contains("BSED") || tokens.contains("BEED")
                || tokens.contains("BPE") || tokens.contains("EDUC")) return "EDUC";

        return raw; // BSIS, BSA, BSAIS, BSCRIM... stay exact (BSA never matches BSAIS)
    }

    public static boolean matches(String programCourse, String departmentName) {
        String a = toDepartmentCode(programCourse);
        return !a.isEmpty() && a.equals(toDepartmentCode(departmentName));
    }
}