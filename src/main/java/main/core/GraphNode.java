package main.core;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import main.xml.Diagram;

import java.util.*;

@Getter
@Setter
public class GraphNode {
    private Boolean isGeneratedDueToRecursion = false;
    private Diagram diagram;
    private String className;
    private String methodName;
    private Boolean original;

    private Map<String, String> membersMap = new HashMap<>();
    private List<String> unknownTypes = new ArrayList<  >();
    private Set<String> returnTypes = new HashSet<>();
    private Map<String, Set<String>> membersPossibleTypes = new HashMap<>();

    private List<FieldDeclaration> classMembers;
    private List<Parameter> parametersMembers;
    private List<VariableDeclarationExpr> variableMembers;

    private BlockStmt blockStmt;
    private List<String> sequence = new ArrayList<>();
    private final List<Pair<String, Boolean>> participants = new ArrayList<>();
    private final Set<String> existingConstructors = new HashSet<>();
    private final Set<String> lifelines = new HashSet<>();
    private final Set<String> messages = new HashSet<>();

    public GraphNode(Diagram diagram, String className, String methodName, BlockStmt blockStmt, List<FieldDeclaration> classMembers, List<Parameter> parametersMembers, List<VariableDeclarationExpr> variableMembers, boolean original) {
        this.diagram = diagram;
        this.lifelines.add(className);
        this.messages.add(Utils.getOnlyNameOfMethod(methodName));
        this.original = original;
        this.className = className;
        this.methodName = methodName;
        this.blockStmt = blockStmt;
        this.classMembers = classMembers;
        this.parametersMembers = parametersMembers;
        this.variableMembers = variableMembers;

        parametersMembers.forEach(parameter -> {
            if (parameter.findAll(ClassOrInterfaceType.class).size() > 0 && parameter.findAll(SimpleName.class).size() > 0) {
                var type = parameter.getType().resolve().describe();
                var name = parameter.getNameAsString();
                this.putMember(name, type);
                this.unknownTypes.add(name);
            }
        });

        classMembers.forEach(parameter -> {
            if (parameter.findAll(VariableDeclarator.class).size() > 0) {
                var type = parameter.findAll(VariableDeclarator.class).get(0).getType().resolve().describe();
                var name = parameter.findAll(VariableDeclarator.class).get(0).getNameAsString();
                if (parameter.findAll(ObjectCreationExpr.class).size() > 0) {
                    type = parameter.findAll(ObjectCreationExpr.class).get(0).getType().resolve().describe();
                    this.putMember(name, type);
                    return;
                }
                this.unknownTypes.add(name);
            }
        });
    }

    public void putMember(String name, String type) {
        //remove generics
        type = type.replaceAll("<.*>", "");

        //put to known types
        membersMap.put(name, type);

        //remove from unknown types
        unknownTypes.remove(name);
    }

    public void putMemberPossibleType(String name, String type) {
        if (type == null) return;
        //remove generics
        type = type.replaceAll("<.*>", "");

        //put to known types
        if (!membersPossibleTypes.containsKey(name)) {
            membersPossibleTypes.put(name, new HashSet<>());
        }
        membersPossibleTypes.get(name).add(type);
    }

}
