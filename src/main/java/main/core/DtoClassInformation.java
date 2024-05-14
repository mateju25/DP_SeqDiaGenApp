package main.core;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DtoClassInformation {
    private String className;
    private long fields;
    private long methods;
    private long getters;
    private long setters;
    private String normalClass;
}
