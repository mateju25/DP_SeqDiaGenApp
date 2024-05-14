package main.core;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ReferenceDto {
    private String methodName;
    private final Map<String, Integer> referencedBy = new HashMap<>();

    public ReferenceDto(String className) {
        this.methodName = className;
    }

    public Integer getAllReferences() {
        return referencedBy.values().stream().reduce(0, Integer::sum);
    }

    public String getAllReferencesFormatted() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.methodName);
        for (Map.Entry<String, Integer> entry : referencedBy.entrySet()) {
            sb.append("\n\t").append(entry.getKey());
        }
        return sb.toString();
    }
}
