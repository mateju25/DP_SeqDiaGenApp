package main.core;

import com.github.javaparser.ast.body.TypeDeclaration;

public class Utils {

    // return the full name of a class, including the package name
    public static String getFullNameOfClass(TypeDeclaration<?> type) {
        var tmp = type.resolve();
        return tmp.getPackageName() + (tmp.getPackageName().equals("") ? "" : ".") + tmp.getName();
    }

    // return the name of a class, including the package name
    public static String getNameOfClassFromSignature(String methodSignature) {
        var left = methodSignature.split("\\(")[0];
        return left.substring(0, left.lastIndexOf(".")).replace("-", "");
    }

    public static String getOnlyNameOfMethod(String methodSignature) {
        var left = methodSignature.split("\\(")[0];
        return left.substring(left.lastIndexOf(".") + 1);
    }

    public static String getFullSignatureWithoutPackageName(String methodSignature) {
        var left = methodSignature.split("\\(")[0];
        return methodSignature.substring(left.lastIndexOf(".") + 1);
    }
}
