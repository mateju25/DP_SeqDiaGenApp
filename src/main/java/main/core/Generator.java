package main.core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithBody;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import javafx.scene.image.Image;
import javafx.util.Pair;
import main.xml.Diagram;
import main.xml.DiagramList;
import net.sourceforge.plantuml.SourceStringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Generator {
    public static String LIBS = "D:/Skola/DP/SeqDiaGen/libs";
    public static String CONCRETE_TEST = "D:/Skola/DP/SeqDiaGen/src/test3/java";
    public static String OUTPUT = "D:/Skola/DP/SeqDiaGen/out3/";

    public static Boolean WHOLE_POLYMORPH = true;
    public static Boolean ONLY_PROJECT = false;
    public static Boolean USE_REFS = false;
    public static Boolean ONLY_MODEL_INFO = false;
    public static Boolean GENERATE_XML = true;
    //     0 - no constructors
    //     1 - only constructors that are present in code
    //     2 - every constructor
    public static Integer CONSTRUCTOR_MODE = 1;


    private static Integer diagramIds = 0;
    private static final Set<Node> nodes = new HashSet<>();
    private static final DiagramList diagrams = new DiagramList();
    private static List<GraphNode> graphNodes = new ArrayList<>();
    private static Set<String> projectClasses = new HashSet<>();
    private static final List<ReferenceDto> referencedMethods = new ArrayList<>();
    private static Set<String> modelByHeuristicWithAllTypes = new HashSet<>();
    private static Set<String> existingConstructors = new HashSet<>();
    private static Map<String, Set<String>> subtypeMapGroupedBySupertype = new HashMap<>();
    private static Map<String, Set<String>> supertypeMapGroupedBySubtype = new HashMap<>();

    public static Pair<Map<String, GraphNode>, Map<String, Image>> main(String[] args) throws IOException {
        graphNodes = new ArrayList<>();
        projectClasses = new HashSet<>();
        existingConstructors = new HashSet<>();
        subtypeMapGroupedBySupertype = new HashMap<>();
        supertypeMapGroupedBySubtype = new HashMap<>();
        List<File> javaFiles = FileUtils.findJavaFiles(new File(CONCRETE_TEST));
        List<File> jarFiles = new ArrayList<>();
        for (String s: LIBS.split(";")) {
            jarFiles.addAll(FileUtils.findJarFiles(new File(s)));
        }

        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        if (CONCRETE_TEST.contains("8")) //TODO remove before release
            combinedSolver.add(new ReflectionTypeSolver(false)); //use this only if javafx lib is not in libs test 8
        else
            combinedSolver.add(new ReflectionTypeSolver(true));
        combinedSolver.add(new JavaParserTypeSolver(new File(CONCRETE_TEST)));
        jarFiles.forEach(jarFile -> {
            try {
                combinedSolver.add(new JarTypeSolver(jarFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
        StaticJavaParser
                .getParserConfiguration()
                .setSymbolResolver(symbolSolver);

        //prepare graphnodes from all java files, one graphnode == one method/constructor
        for (File javaFile : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);

            List<FieldDeclaration> fields = new ArrayList<>();
            List<ConstructorDeclaration> constructors = new ArrayList<>();
            List<MethodDeclaration> methods = new ArrayList<>();

            var sourceCodeName = javaFile.getName().replace(".java", "");

            cu.getClassByName(sourceCodeName).map(e -> projectClasses.add(Utils.getFullNameOfClass(e)));
            cu.getInterfaceByName(sourceCodeName).map(e -> projectClasses.add(Utils.getFullNameOfClass(e)));
            cu.getEnumByName(sourceCodeName).map(e -> projectClasses.add(Utils.getFullNameOfClass(e)));
            cu.getRecordByName(sourceCodeName).map(e -> projectClasses.add(Utils.getFullNameOfClass(e)));
            cu.getAnnotationDeclarationByName(sourceCodeName).map(e -> projectClasses.add(Utils.getFullNameOfClass(e)));

            boolean isOriginal = true;
            var classType = cu.getClassByName(sourceCodeName);
            if (classType.isPresent()) {
                fields = classType.get().findAll(FieldDeclaration.class);
                constructors = classType.get().findAll(ConstructorDeclaration.class);
                methods = classType.get().findAll(MethodDeclaration.class);

                classType.get().getExtendedTypes().forEach(e -> {
                    fillTypeMap(classType, e);
                });
                classType.get().getImplementedTypes().forEach(e -> {
                    fillTypeMap(classType, e);
                });
            }
            var interfaceType = cu.getInterfaceByName(sourceCodeName);
            if (interfaceType.isPresent()) {
                isOriginal = false;
                methods = interfaceType.get().findAll(MethodDeclaration.class);
                interfaceType.get().getExtendedTypes().forEach(e -> {
                    fillTypeMap(interfaceType, e);
                });
                interfaceType.get().getImplementedTypes().forEach(e -> {
                    fillTypeMap(interfaceType, e);
                });
            }

            List<FieldDeclaration> finalFields = fields;
            boolean finalIsOriginal = isOriginal;
            constructors.forEach(method -> {
                graphNodes.add(new GraphNode(
                        new Diagram(method.resolve().getQualifiedSignature().replace("-", "")),
                        Utils.getNameOfClassFromSignature(method.resolve().getQualifiedSignature()),
                        method.resolve().getQualifiedSignature().replace("-", ""),
                        method.getBody(),
                        finalFields,
                        new ArrayList<>(method.getParameters()),
                        method.findAll(VariableDeclarationExpr.class),
                        !method.resolve().getQualifiedSignature().contains("Anonymous") && finalIsOriginal));
            });
            methods.forEach(method -> {
                try {
                graphNodes.add(new GraphNode(
                        new Diagram(method.resolve().getQualifiedSignature().replace("-", "")),
                        Utils.getNameOfClassFromSignature(method.resolve().getQualifiedSignature()),
                        method.resolve().getQualifiedSignature().replace("-", ""),
                        method.getBody().orElse(null),
                        finalFields,
                        new ArrayList<>(method.getParameters()),
                        method.findAll(VariableDeclarationExpr.class),
                        !method.resolve().getQualifiedSignature().contains("Anonymous") && finalIsOriginal));
                } catch (UnsolvedSymbolException e) {

                }
            });

        }

        List<DtoClassInformation> dtoClassInformation = createDtoClassInformation(javaFiles);
        var modelByHeuristic = dtoClassInformation.stream()
                .filter(e -> e.getFields() != 0 &&
                        !e.getClassName().contains("util") &&
                        !e.getClassName().contains("Util")  && !
                        e.getClassName().contains("controller") &&
                        !e.getClassName().contains("Controller") &&
                        e.getNormalClass().equals("1") &&
                        ((e.getGetters()+e.getSetters()) * 100.0 / (e.getMethods() == 0 ? 0.0001 : e.getMethods())) >= 30)
                .map(DtoClassInformation::getClassName).collect(Collectors.toSet());
        modelByHeuristicWithAllTypes = new HashSet<>(modelByHeuristic);
        for (String s : modelByHeuristic) {
            modelByHeuristicWithAllTypes.addAll(getAllSupertypes(s));
        }
        modelByHeuristicWithAllTypes.removeIf(s -> !projectClasses.contains(s));

        System.out.println("-----------------------------------------\n" + CONCRETE_TEST);
        System.out.println("Number of files: " + javaFiles.size());

        Formatter fmt = new Formatter();
        fmt.format("%100s %15s %15s %15s %15s %15s %15s\n", "Class", "Fields", "Methods", "Getters", "Setters", "Percent", "NormalClass");
        dtoClassInformation.forEach(e -> {
            if (e.getNormalClass().equals("0"))
                fmt.format("%100s %15s %15s %15s  %15s %15f %15s\n", "-", "", "", "", "", 0.0, "");
            else
                fmt.format("%100s %15s %15s %15s  %15s %15f %15s\n", e.getClassName(), e.getFields(), e.getMethods(), e.getGetters(), e.getSetters(), ((e.getGetters()+e.getSetters()) * 100.0 / (e.getMethods() == 0 ? 0.0001 : e.getMethods())), e.getNormalClass());
        });
        System.out.print(fmt);
        System.out.println("Probably model by heuristic: \n" + modelByHeuristic.stream().sorted().collect(Collectors.joining("\n")));
        System.out.println("Count by heuristic: " + modelByHeuristic.size());
        modelByHeuristicWithAllTypes.removeAll(modelByHeuristic);
        System.out.println("\nProbably also model due to supertypes: \n" + modelByHeuristicWithAllTypes.stream().sorted().collect(Collectors.joining("\n")));
        modelByHeuristicWithAllTypes.addAll(modelByHeuristic);
        System.out.println("Count with all types: " + modelByHeuristicWithAllTypes.size());

        if (ONLY_MODEL_INFO)
            return null;

        System.out.println("\nSTARTED PARSING");
        for (int i = 0; i < graphNodes.size(); i++) {
            generateNode(graphNodes.get(i));
        }
        System.out.println("END PARSING");

        Map<String, GraphNode> result = new HashMap<>();
        Map<String, Image> resultImages = new HashMap<>();
        AtomicInteger countOfAllDiagrams = new AtomicInteger();
        AtomicInteger countOfUseCaseGoodDiagrams = new AtomicInteger();
        AtomicInteger countOfAllDiagramsWithoutSimple = new AtomicInteger();
        Set<String> useCaseDiagramNames = new HashSet<>();
        System.out.println("STARTED GENERATING PICTURES");
        graphNodes.forEach(g -> {
            countOfAllDiagrams.getAndIncrement();
            if (true || isNodeUseful(g)) {
                countOfAllDiagramsWithoutSimple.getAndIncrement();
                var diagram = makeSequenceDiagram(g);
                var usefull = GENERATE_XML || isNodeUsefulWithUseCase(g, modelByHeuristicWithAllTypes, false);
                if (!usefull)
                    return;

                useCaseDiagramNames.add(g.getMethodName());
                countOfUseCaseGoodDiagrams.getAndIncrement();
                var name = (g.getMethodName()).replaceAll("[/\\\\?%*:|\"<>]", "_") + ".png";

                diagrams.getDiagrams().add(g.getDiagram());
                result.put(name, g);
                if (!GENERATE_XML) {
                    SourceStringReader reader = new SourceStringReader(diagram);
                    File png = new File(OUTPUT + name);
                    try {
                        OutputStream pngOut = new FileOutputStream(png);
                        reader.outputImage(pngOut);
                        resultImages.put(name, new Image(png.toURI().toString()));
                    } catch (IOException ignored) {
                        ignored.printStackTrace();
                    }
                }
            }
        });
        System.out.println("END GENERATING PICTURES\n");

        System.out.println("Number of sequence diagrams: " + countOfAllDiagrams.get());
        System.out.println("Number of sequence diagrams without simple: " + countOfAllDiagramsWithoutSimple.get());
        System.out.println("Number of sequence diagrams without simple and UC good: " + countOfUseCaseGoodDiagrams.get());
        Set<String> filteredDiagramNamesWithoutUnusedModelMethods = new HashSet<>(useCaseDiagramNames);
        for (String model : modelByHeuristicWithAllTypes) {
            for (String method: useCaseDiagramNames)
                if (method.startsWith(model+".") && referencedMethods.stream().noneMatch(e -> e.getMethodName().contains(method))) {
                    System.out.println("Unused method from model: " + method);
                    filteredDiagramNamesWithoutUnusedModelMethods.remove(method);
                }
        }
        System.out.println("Number of sequence diagrams without simple and UC good and unused method from model: " + filteredDiagramNamesWithoutUnusedModelMethods.size());

        System.out.println("\nEXPERIMENTAL: ");
        var copyOfUseCaseDiagramNames = new HashSet<>(useCaseDiagramNames);
        useCaseDiagramNames.removeAll(referencedMethods.stream().map(ReferenceDto::getMethodName).collect(Collectors.toSet()));
        filteredDiagramNamesWithoutUnusedModelMethods = new HashSet<>(useCaseDiagramNames);
        for (String model : modelByHeuristicWithAllTypes) {
            for (String method: useCaseDiagramNames)
                if (method.startsWith(model+"."))
                    filteredDiagramNamesWithoutUnusedModelMethods.remove(method);
        }
        System.out.println("Diagrams that are on top of references (no other diagram reference it): " + filteredDiagramNamesWithoutUnusedModelMethods.size());
        System.out.println(filteredDiagramNamesWithoutUnusedModelMethods.stream().map(e -> "0 - " + e).sorted().collect(Collectors.joining("\n")));
        for (String model : filteredDiagramNamesWithoutUnusedModelMethods) {
            var opt = diagrams.getDiagrams().stream().filter(e -> e.getTitle().equals(model)).findFirst();
            opt.ifPresent(diagram -> diagram.setOnTopOfReferenceTree(true));
        }

        System.out.println("\n Other diagrams: ");
        System.out.println(referencedMethods.stream().filter(e -> copyOfUseCaseDiagramNames.contains(e.getMethodName())).sorted(Comparator.comparing(ReferenceDto::getAllReferences)).map(ReferenceDto::getAllReferencesFormatted).collect(Collectors.toSet()).stream().sorted().collect(Collectors.joining("\n")));

        if (GENERATE_XML) {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(DiagramList.class);
                Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

                StringWriter sw = new StringWriter();
                jaxbMarshaller.marshal(diagrams, sw);
                String xmlString = sw.toString();

                File file = new File(OUTPUT + "diagrams.xml");
                FileWriter fw = new FileWriter(file);
                fw.write(xmlString);
                fw.close();
            } catch (JAXBException e) {
                e.printStackTrace();
            }
        }

        return new Pair<>(result, resultImages);
    }

    private static List<DtoClassInformation> createDtoClassInformation(List<File> javaFiles) throws FileNotFoundException {
        List<DtoClassInformation> dtoClassInformation = new ArrayList<>();
        //prepare dtoModelInformation from all java files, one dtoModelInformation == one class
        try {
            for (File javaFile : javaFiles) {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                var classType = cu.getClassByName(javaFile.getName().replace(".java", ""));
                classType.map(classOrInterfaceDeclaration ->
                        dtoClassInformation.add(
                                new DtoClassInformation(
                                        Utils.getFullNameOfClass(classOrInterfaceDeclaration),
                                        classOrInterfaceDeclaration.resolve().getAllNonStaticFields().stream()
                                                .filter(e -> (e.declaringType() instanceof JavaParserFieldDeclaration) || (e.declaringType() instanceof JavaParserClassDeclaration)).count(),
                                        classOrInterfaceDeclaration.resolve().getAllMethods().stream().filter(e -> e.getDeclaration() instanceof JavaParserMethodDeclaration).count(),
                                        classOrInterfaceDeclaration.resolve().getAllMethods().stream().filter(e -> e.getDeclaration() instanceof JavaParserMethodDeclaration)
                                                .filter(e -> e.getName().contains("get")).count(),
                                        classOrInterfaceDeclaration.resolve().getAllMethods().stream().filter(e -> e.getDeclaration() instanceof JavaParserMethodDeclaration)
                                                .filter(e -> e.getName().contains("set")).count(),
                                        classOrInterfaceDeclaration.getModifiers().contains(Modifier.abstractModifier()) || classOrInterfaceDeclaration.resolve().isInterface() ? "0" : "1")));
            }
        } catch (UnsolvedSymbolException e) {

        }
        return dtoClassInformation;
    }

    private static void fillTypeMap(Optional<ClassOrInterfaceDeclaration> classType, ClassOrInterfaceType e) {
        var superType2 = e.resolve().describe();
        var subType2 = Utils.getFullNameOfClass(classType.get());
        if (subtypeMapGroupedBySupertype.containsKey(superType2)) {
            subtypeMapGroupedBySupertype.get(superType2).add(subType2);
        } else {
            subtypeMapGroupedBySupertype.put(superType2, new HashSet<>(Collections.singletonList(subType2)));
        }

        if (supertypeMapGroupedBySubtype.containsKey(subType2)) {
            supertypeMapGroupedBySubtype.get(subType2).add(superType2);
        } else {
            supertypeMapGroupedBySubtype.put(subType2, new HashSet<>(Collections.singletonList(superType2)));
        }
    }

    private static boolean isNodeUseful(GraphNode g) {
        return g.getOriginal() && g.getSequence().size() >= 4;
    }

    private static boolean isNodeUsefulWithUseCase(GraphNode g, Set<String> dataModel, boolean silent) {
        var isUsedInReturnOrParams = false;
        var isUsedInNameOfMethod = false;
        for (String model: dataModel) {
            for (String message : g.getMessages()) {
                if (message.contains(model)) {
                    isUsedInReturnOrParams = true;
                    break;
                }
                var split = model.split("\\.");
                if (message.contains(split[split.length - 1])) {
                    isUsedInNameOfMethod = true;
                    break;
                }
            }
            if (g.getMethodName().contains(model)) {
                isUsedInReturnOrParams = true;
                break;
            }

        }
        var participants = g.getLifelines();

        if (!silent) {
            if (!(g.getOriginal() && g.getSequence().size() > 4 && (participants.stream().anyMatch(dataModel::contains) || isUsedInReturnOrParams || isUsedInNameOfMethod)))
                System.out.println("USE CASE NOT: " + g.getMethodName());
        }
        // is in participants any string from dataModel
        // nejde inform() napriec ref nemam lifelines
        return g.getOriginal() && g.getSequence().size() > 4 && (participants.stream().anyMatch(dataModel::contains) || isUsedInReturnOrParams || isUsedInNameOfMethod);
    }

    private static String makeSequenceDiagram(GraphNode g) {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> seqParticipants = new HashMap<>();

        sb.append("title ").append(g.getMethodName()).append("\n");
        sb.append(" -> ").append(Utils.getNameOfClassFromSignature(g.getMethodName())).append("\n");
        g.getSequence().forEach(s -> {
            sb.append(s).append("\n");
        });

        for (String participant : seqParticipants.keySet()) {
            g.getParticipants().add(new Pair<>(participant, projectClasses.contains(participant)));
        }

        g.getExistingConstructors().addAll(existingConstructors);
        sb.append("return ").append("\n");
        sb.append("@enduml\n");
        sb.insert(0, "skinparam sequenceReferenceBackgroundColor #GreenYellow\n");
        sb.insert(0, "autoactivate on\n");
        sb.insert(0, "@startuml\n");
        return sb.toString();
    }

    private static GraphNode findGraphNode(String className, String signature) {
        if (signature == null || className == null) {
            return null;
        }
        return graphNodes.stream().filter(g -> g.getClassName().equals(className) && g.getMethodName().equals(signature)).findFirst().orElse(null);
    }

    private static void createMessageWithActivation(GraphNode node, String objectName, String to, String signature, List<String> result, String returnType) {
        String className = node.getClassName().replaceAll("<.*>", "");
        if (to != null)
            to = to.replaceAll("<.*>", "");
        signature = signature.replaceAll("<.*>", "");

        var xmlDiagram = node.getDiagram();
        if (returnType == null) {
            xmlDiagram.addParticipant(className);
            xmlDiagram.addParticipant(to);
            xmlDiagram.addCreateMessage(className, to, signature, diagramIds);
            diagramIds++;

            result.add(className +  " -> " + to + ": " + (objectName != null && objectName.charAt(0) > 96 ? "//" + objectName + "//." : "") +  Utils.getFullSignatureWithoutPackageName(signature));
            node.getLifelines().add(to);
            node.getMessages().add(Utils.getOnlyNameOfMethod(signature));
        } else {
            xmlDiagram.addParticipant(className);
            xmlDiagram.addParticipant(to);
            xmlDiagram.addReturnMessage(className, to, returnType, diagramIds);
            diagramIds++;

            result.add("return" + (returnType.equals("void") ? "" : (returnType)));
            if (!returnType.equals("void")) {
                node.getMessages().add(returnType);
            }
        }
    }

    private static void fillDiagramFromNodeOnlyForConstructor(GraphNode from, String to, String signature, GraphNode inside, List<String> result) {
        generateNode(inside);

        if (ONLY_PROJECT && findGraphNode(to, signature) == null)
            return;

        createMessageWithActivation(from, null, to, signature, result, null);
        createReference(from, inside, result);
        createMessageWithActivation(from, null, to, signature, result, to);
    }

    private static void createReference(GraphNode from, GraphNode inside, List<String> result) {
        if (isNodeUseful(inside) && USE_REFS) {
            if (isNodeUsefulWithUseCase(inside, modelByHeuristicWithAllTypes, true)) {
                result.add("ref#YellowGreen over " + inside.getClassName() + ": **" + inside.getMethodName() + "**");
            } else {
                var previous = result.get(result.size() - 1);
                previous += " #YellowGreen";
                result.set(result.size() - 1, previous);
            }
        } else {
            result.addAll(inside.getSequence());
            from.getDiagram().addMessages(inside.getDiagram().getBlocks());
        }
        from.getLifelines().addAll(inside.getLifelines());
        from.getMessages().addAll(inside.getMessages());
    }

    private static void parseNode(GraphNode graphNode, Node node, List<String> result) {
//        if (nodes.contains(node)) {
//            return;
//        }
//        nodes.add(node);
        if (node instanceof BlockStmt) {
            node.getChildNodes().forEach(child -> parseNode(graphNode, child, result));
            return;
        }

        if (node instanceof MethodDeclaration && node.getParentNode().isPresent() && node.getParentNode().get() instanceof ObjectCreationExpr) {
            String className = ((ObjectCreationExpr) node.getParentNode().get()).getTypeAsString();
            String signature = ((MethodDeclaration) node).resolve().getQualifiedSignature();
            createMessageWithActivation(graphNode, null, className, signature, result, null);
            var newNode = new GraphNode(new Diagram(signature), className, signature, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false);
            node.getChildNodes().forEach(child -> parseNode(newNode, child, result));
            createMessageWithActivation(graphNode, null, className, signature, result, "void");

            graphNode.getLifelines().addAll(newNode.getLifelines());
            graphNode.getMessages().addAll(newNode.getMessages());
            return;
        }

        if (node instanceof TryStmt) {
            processTryStmt(graphNode, (TryStmt) node, result);
            return;
        }

        if (node instanceof ReturnStmt) {
            processReturnStmt(graphNode, node, result);
            return;
        }

        //variable
        if (node instanceof VariableDeclarator) {
            var type = ((VariableDeclarator) node).getType().resolve().describe();
            var name = ((VariableDeclarator) node).getNameAsString();
            processVariableDeclaratorOrAssignExpression(graphNode, node, result, type, name);
            return;
        }
        if (node instanceof AssignExpr) {
            var name = node.findAll(NameExpr.class).size() > 0 ? node.findAll(NameExpr.class).get(0).getNameAsString() : node.findAll(FieldAccessExpr.class).get(0).getNameAsString();
            processVariableDeclaratorOrAssignExpression(graphNode, node, result, null, name);
            return;
        }
        //constructors
        if (node instanceof ObjectCreationExpr) {
            String className = ((ObjectCreationExpr) node).getType().resolve().describe();
            String signature = ((ObjectCreationExpr) node).resolve().getQualifiedSignature();
            processObjectCreation(graphNode, node, result, className, signature);
            return;
        }
        if (node instanceof ExplicitConstructorInvocationStmt) {
            String signature = ((ExplicitConstructorInvocationStmt) node).resolve().getQualifiedSignature();
            String className = Utils.getNameOfClassFromSignature(signature);
            processObjectCreation(graphNode, node, result, className, signature);
            return;
        }

        if (node instanceof MethodCallExpr) {
            processMethodCallExpression(graphNode, node, result);
            return;
        }
        //condition
        if (node instanceof IfStmt) {
            processIfStmt(graphNode, (IfStmt) node, result);
            return;
        }
        if (node instanceof SwitchStmt) {
            processSwitchStmt(graphNode, (SwitchStmt) node, result);
            return;
        }
        //loops
        if (node instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) node;
            if (forStmt.getInitialization() != null) {
                forStmt.getInitialization().forEach(init -> parseNode(graphNode, init, result));
            }
            if (forStmt.getCompare().isPresent()) {
                parseNode(graphNode, forStmt.getCompare().get(), result);
            }
            if (forStmt.getUpdate() != null) {
                forStmt.getUpdate().forEach(update -> parseNode(graphNode, update, result));
            }
            var loop = forStmt.getInitialization().stream().map(Node::toString).collect(Collectors.joining(",")) + ";" +
                    (forStmt.getCompare().isPresent() ? forStmt.getCompare().get().toString() : "") + ";" +
                    forStmt.getUpdate().stream().map(Node::toString).collect(Collectors.joining(","));
            processLoop(graphNode, (ForStmt) node, result, loop);
            return;
        }
        if (node instanceof ForEachStmt) {
            if (((ForEachStmt) node).getIterable()!= null) {
                parseNode(graphNode, ((ForEachStmt) node).getIterable(), result);
            }
            var loop = ((ForEachStmt) node).getIterable().toString();
            processLoop(graphNode, (ForEachStmt) node, result, loop);
            return;
        }
        if (node instanceof WhileStmt) {
            if (((WhileStmt) node).getCondition() != null) {
                parseNode(graphNode, ((WhileStmt) node).getCondition(), result);
            }
            var loop = ((WhileStmt) node).getCondition().toString();
            processLoop(graphNode, (WhileStmt) node, result, loop);
            return;
        }
        if (node instanceof DoStmt) {
            if (((DoStmt) node).getCondition() != null) {
                parseNode(graphNode, ((DoStmt) node).getCondition(), result);
            }
            var loop = ((DoStmt) node).getCondition().toString();
            processLoop(graphNode, (DoStmt) node, result, loop);
            return;
        }

        node.getChildNodes().forEach(child -> parseNode(graphNode, child, result));
    }

    private static void processMethodCallExpression(GraphNode graphNode, Node node, List<String> result) {
        // first we need to draw all methods that are called in the call of the method
        if (node.findAll(MethodCallExpr.class).size() + node.findAll(ObjectCreationExpr.class).size() > 1)
            node.getChildNodes().stream().filter(e -> !(e instanceof LambdaExpr)).forEach(child -> parseNode(graphNode, child, result));

        //TODO sometimes nested lambdas make problem
        try { ((MethodCallExpr) node).resolve(); }
        catch (Exception e) {
            String name = node.findAll(NameExpr.class).stream().findFirst().map(Node::toString).orElse(null);
            String className = graphNode.getMembersMap().get(name);
            String signature = node.toString();
            createMessageWithActivation(graphNode, name, className, signature, result, null);
            createMessageWithActivation(graphNode, name, className, signature, result, "");
            return;
        }

        String polymorph = null;
        String name = null;
        String signature = ((MethodCallExpr) node).resolve().getQualifiedSignature();
        String className = ((MethodCallExpr) node).resolve().declaringType().getQualifiedName();
        String returnType = ((MethodCallExpr) node).resolve().getReturnType().describe();


        // if look if used variable is not polymorph and if so we flag it
        if (((MethodCallExpr) node).getScope().isPresent()) {
            name = ((MethodCallExpr) node).getScope().get().toString();

            var newType = graphNode.getMembersMap().get(name);
            if (newType == null || graphNode.getUnknownTypes().contains(name)) {
                if (!name.equals("this") && !name.equals("super")) {
                    if (findGraphNode(className, signature) != null) {
                        polymorph = name;
                    }
                }
            }
            if (newType != null) {
                signature = signature.replace(className, newType);
                className = newType;
            }
        }

        String changedSignature = null;
        if (((MethodCallExpr) node).getArguments().size() > 0) {
            var classNameAndMethod = signature.split("\\(")[0];
            var args = new ArrayList<String>();
            ((MethodCallExpr) node).getArguments().forEach(expression -> {
                if (expression instanceof ClassExpr)
                    args.add(((ClassExpr) expression).getType().resolve().describe());
                if (expression instanceof ObjectCreationExpr)
                    args.add(((ObjectCreationExpr) expression).getType().resolve().describe());
                if (expression instanceof NameExpr)
                    if (graphNode.getMembersMap().containsKey(((NameExpr) expression).getNameAsString()))
                        args.add(graphNode.getMembersMap().get(((NameExpr) expression).getNameAsString()));
            });
            if (args.size() > 0) {
                changedSignature = signature;
                signature = classNameAndMethod + "(" + String.join(", ", args) + ")";
                if (signature.equals(changedSignature) || (findGraphNode(className, signature) != null &&  findGraphNode(className, changedSignature) == null))
                    changedSignature = null;
            }
        }

        var route = findGraphNode(className, signature);
        var possible = findGraphNode(className, changedSignature);
        if (possible != null) {
            route = new GraphNode(new Diagram(signature), className, signature, possible.getBlockStmt(), possible.getClassMembers(), possible.getParametersMembers(), possible.getVariableMembers(), false);
            var args = signature.split("\\)")[0].split("\\(")[1].split(", ");
            for (int i = 0; i < args.length; i++) {
                var param = route.getParametersMembers().get(i);
                if (!param.getType().resolve().describe().equals(args[i]))
                    route.putMember(param.getNameAsString(), args[i]);
            }
        }

        //recursive call
        if (className.equals(graphNode.getClassName()) && signature.equals(graphNode.getMethodName())) {
            createMessageWithActivation(graphNode, name, graphNode.getClassName(), signature, result, null);
            result.add("return");
            addReferenceInfo(graphNode, signature);
            return;
        }

        //own call
        if (className.equals(graphNode.getClassName())) {
            addReferenceInfo(graphNode, signature);
            result.add(graphNode.getClassName() + " -> " + graphNode.getClassName() + ": " + signature);
            graphNode.getLifelines().add(graphNode.getClassName());
            graphNode.getMessages().add(Utils.getOnlyNameOfMethod(signature));
            if (route != null && route.getSequence().size() == 0) {
                generateNode(route);
            }
            if (route != null) {
                createReference(graphNode, route, result);
            }
            result.add("return" + (returnType.equals("void") ? "" : (returnType)));
            return;
        }

        if (ONLY_PROJECT && findGraphNode(className, signature) == null)
            return;

        var realPoly = new ArrayList<String>();
        if (polymorph != null) {
            var types = getAllSubtypes(className);
            types.remove(className);
            if (graphNode.getMembersPossibleTypes().containsKey(polymorph))
                types.retainAll(graphNode.getMembersPossibleTypes().get(polymorph));
            for (var type : types) {
                //if we can find the same method in the subtype, we can draw the polymorphism
                if (findGraphNode(type, signature.replace(className, type)) != null)
                    realPoly.add(type);
                if (findGraphNode(type, signature.replace(className, type)) == null && changedSignature != null && findGraphNode(type, changedSignature.replace(className, type)) != null)
                    realPoly.add(type);
            }
            // if there exists more than zero, we draw the polymorphism
            if (realPoly.size() > 1) {
                result.add("alt#White #fccccc unknown polymorphism - ");
                graphNode.getDiagram().addAltBeginMessage("unknown polymorphism", diagramIds);
                diagramIds++;
            }
        }

        if (realPoly.size() == 1) {
            route = findGraphNode(realPoly.get(0), signature.replace(className, realPoly.get(0)));
            if (route == null && changedSignature != null)
                route = findGraphNode(realPoly.get(0), changedSignature.replace(className, realPoly.get(0)));
            signature = signature.replace(className, realPoly.get(0));
            className = realPoly.get(0);
        }

        createMessageWithActivation(graphNode, name, className, signature, result, null);
        if (route != null) {
            addReferenceInfo(graphNode, route.getMethodName());

            //recursion fix`
            if (!(!route.getOriginal() && getAllSupertypes(className).contains(route.getClassName()) && realPoly.contains(graphNode.getClassName()))) {
                generateNode(route);
            }
            createReference(graphNode, route, result);
        } else {
            if (node.findAll(LambdaExpr.class).size() > 0) {
                var newNode = new GraphNode(new Diagram(signature), className, signature, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false);
                node.getChildNodes().forEach(child -> parseNode(newNode, child, result));
                graphNode.getLifelines().addAll(newNode.getLifelines());
                graphNode.getMessages().addAll(newNode.getMessages());
            }
        }
        createMessageWithActivation(graphNode, name, className, signature, result, returnType);


        if (realPoly.size() > 1) {
            int index = result.stream().filter(v -> v.contains("alt#White #fccccc unknown polymorphism - ")).map(result::indexOf).max(Integer::compareTo).orElse(-1);
            for (var type : realPoly) {
                var newRoute = findGraphNode(type, signature.replace(className, type));
                if (newRoute == null && changedSignature != null)
                    newRoute = findGraphNode(type, changedSignature.replace(className, type));
                if (newRoute != null && index != -1) {
                    result.set(index, result.get(index) + type + (realPoly.indexOf(type) == realPoly.size() -1 ? "" : ", "));
                }
                if (newRoute != null) {
                    addReferenceInfo(graphNode, newRoute.getMethodName());

                    if (WHOLE_POLYMORPH) {
                        result.add("else");
                        graphNode.getDiagram().addAltElseMessage("", diagramIds);
                        diagramIds++;
                        if (findGraphNode(type, signature.replace(className, type)) != null) {
                            createMessageWithActivation(graphNode, name, type, signature.replace(className, type), result, null);
                            createReference(graphNode, newRoute, result);
                            createMessageWithActivation(graphNode, name, type, signature.replace(className, type), result, returnType);
                        }
                        if (findGraphNode(type, signature.replace(className, type)) == null && changedSignature != null && findGraphNode(type, changedSignature.replace(className, type)) != null) {
                            createMessageWithActivation(graphNode, name, type, changedSignature.replace(className, type), result, null);
                            createReference(graphNode, newRoute, result);
                            createMessageWithActivation(graphNode, name, type, changedSignature.replace(className, type), result, returnType);
                        }
                    }

                    if (newRoute.getSequence().size() == 0 && !type.equals(graphNode.getClassName()))
                        generateNode(newRoute);
                    graphNode.getLifelines().addAll(newRoute.getLifelines());
                    graphNode.getMessages().addAll(newRoute.getMessages());
                }
            }
            result.add("end alt");
            graphNode.getDiagram().addAltEndMessage("", diagramIds);
            diagramIds++;
        }
    }

    private static void addReferenceInfo(GraphNode graphNode, String methodName) {
        ReferenceDto referenceDto = findReferenceByName(methodName);
        if (referenceDto == null) {
            referenceDto = new ReferenceDto(methodName);
            referencedMethods.add(referenceDto);
        }
        //increase count of reference
        referenceDto.getReferencedBy().put(graphNode.getMethodName(), referenceDto.getReferencedBy().getOrDefault(graphNode.getMethodName(), 0) + 1);
    }

    private static void processLoop(GraphNode graphNode, NodeWithBody<?> node, List<String> result, String loop) {
        result.add("loop " + loop);
        parseNode(graphNode, node.getBody(), result);
        result.add("end");
        if (result.get(result.size() - 2).contains("loop")) {
            result.remove(result.get(result.size() - 1));
            result.remove(result.get(result.size() - 1));
        }
    }

    private static void processSwitchStmt(GraphNode graphNode, SwitchStmt node, List<String> result) {
        if (node.getSelector() != null) {
            var xmlDiagram = graphNode.getDiagram();
            parseNode(graphNode, node.getSelector(), result);
            result.add("alt " + node.getSelector().toString().replaceAll("[\r\n]", ""));
            xmlDiagram.addAltBeginMessage(node.getSelector().toString().replaceAll("[\r\n]", ""), diagramIds);
            diagramIds++;

            node.getEntries().forEach(entry -> {
                result.add("else " + (entry.getLabels().size() == 0 ? "default" : entry.getLabels().stream().map(Node::toString).collect(Collectors.joining(","))));
                xmlDiagram.addAltElseMessage("", diagramIds);
                diagramIds++;
                entry.getStatements().forEach(statement -> parseNode(graphNode, statement, result));
                if (result.get(result.size() - 1).contains("else")) {
                    result.remove(result.get(result.size() - 1));
                    xmlDiagram.removeLastBlock();
                }
            });
            result.add("end");
            xmlDiagram.addAltEndMessage("", diagramIds);
            diagramIds++;
        }
    }

    private static void processIfStmt(GraphNode graphNode, IfStmt node, List<String> result) {
        var xmlDiagram = graphNode.getDiagram();
        if (node.getThenStmt() != null) {
            parseNode(graphNode, node.getCondition(), result);
        }
        if (node.getThenStmt() != null) {
            result.add("alt " + node.getCondition().toString().replaceAll("[\r\n]", ""));
            xmlDiagram.addAltBeginMessage(node.getCondition().toString().replaceAll("[\r\n]", ""), diagramIds);
            diagramIds++;
            parseNode(graphNode, node.getThenStmt(), result);
        }
        if (node.getElseStmt().isPresent()) {
            result.add("else");
            xmlDiagram.addAltElseMessage("", diagramIds);
            diagramIds++;
            parseNode(graphNode, node.getElseStmt().orElse(null), result);
        }
        if (result.get(result.size() - 1).contains("else")) {
            result.remove(result.get(result.size() - 1));
            xmlDiagram.removeLastBlock();
        }
        if (result.get(result.size() - 1).contains("alt")) {
            result.remove(result.get(result.size() - 1));
            xmlDiagram.removeLastBlock();
            return;
        }
        result.add("end");
        xmlDiagram.addAltEndMessage("", diagramIds);
        diagramIds++;
    }

    private static void processObjectCreation(GraphNode graphNode, Node node, List<String> result, String className, String signature) {
        var route = findGraphNode(className, signature);
        //if constructor is not in the project, we do not need to draw it
        if (CONSTRUCTOR_MODE != 0) {
            if (route != null) {
                //we draw constructor only if it is in the project
                existingConstructors.add(signature);
                fillDiagramFromNodeOnlyForConstructor(graphNode, className, signature, route, result);
                node.getChildNodes().forEach(child -> parseNode(graphNode, child, result));
            } else {
                if (CONSTRUCTOR_MODE == 2) {
                    //we draw constructor always
                    createMessageWithActivation(graphNode, null, className, signature, result, null);
                    createMessageWithActivation(graphNode, null, className, signature, result, className);
                } else {
                    if (node.getChildNodes().stream().anyMatch(e -> e instanceof MethodDeclaration)) {
                        //we need still to draw all methods present TODO maybe generate new diagram
                        node.getChildNodes().forEach(child -> parseNode(graphNode, child, result));
                    }
                }
            }
        }
    }

    private static void processVariableDeclaratorOrAssignExpression(GraphNode graphNode, Node node, List<String> result, String type, String name) {
        //if there is constructor, we have certain type
        if (node.getChildNodes().stream().anyMatch(e -> e instanceof ObjectCreationExpr)) {
            type = node.findAll(ObjectCreationExpr.class).get(0).getType().resolve().describe();
        } else {
            //if there is method call, we do not have certain type so we need to add it back to unknown types below
            if (node.findAll(MethodCallExpr.class).size() > 0) {
                try {
                    type = node.findAll(MethodCallExpr.class).get(0).resolve().getReturnType().describe();
                    var methodNode = findGraphNode(node.findAll(MethodCallExpr.class).get(0).resolve().getClassName(), node.findAll(MethodCallExpr.class).get(0).resolve().getQualifiedSignature());
                    if (methodNode != null && methodNode.getReturnTypes().size() > 1) {
                        methodNode.getReturnTypes().forEach(t -> graphNode.putMemberPossibleType(name, t));
                    }
                } catch (Exception ignored) {
                    graphNode.putMemberPossibleType(name, type);
                }

            }
        }
        if (type != null)
            graphNode.putMember(name, type);

        if (node.findAll(MethodCallExpr.class).size() > 0) //here we return it
            graphNode.getUnknownTypes().add(name);

        //investigate child nodes
        node.getChildNodes().forEach(child -> parseNode(graphNode, child, result));
    }

    private static void processReturnStmt(GraphNode graphNode, Node node, List<String> result) {
        var returnNode = (ReturnStmt) node;
        if (returnNode.getExpression().isPresent()) {
            if (returnNode.findAll(LambdaExpr.class).size() == 0) {
                var expression = returnNode.getExpression().get();
                var type = expression.calculateResolvedType().describe();
                if (expression instanceof ObjectCreationExpr) {
                    type = ((ObjectCreationExpr) expression).getType().resolve().describe();
                }
                if (expression instanceof MethodCallExpr) {
                    type = ((MethodCallExpr) expression).resolve().getReturnType().describe();
                }
                if (!type.equals("null") && !type.equals("void"))
                    graphNode.getReturnTypes().add(type);
            }
        }
        node.getChildNodes().forEach(child -> parseNode(graphNode, child, result));
    }

    private static void processTryStmt(GraphNode graphNode, TryStmt node, List<String> result) {
        if (node.getTryBlock() != null) {
            result.add("alt try block");
            node.getResources().forEach(r -> parseNode(graphNode, r, result));
            parseNode(graphNode, node.getTryBlock(), result);
        }
        node.getCatchClauses().forEach(c -> {
            result.add("else catch " + c.getParameter().getType().toString());
            parseNode(graphNode, c.getBody(), result);
        });
        result.add("end");

        if (node.getFinallyBlock().isPresent()) {
            parseNode(graphNode, node.getFinallyBlock().get(), result);
        }
    }

    private static void generateNode(GraphNode graphNode) {
        //if is already generated skip
        if (graphNode.getSequence().size() > 0)
            return;
        if (graphNode.getIsGeneratedDueToRecursion())
            return;

        List<String> umlStrings = new ArrayList<>();
        if (graphNode.getBlockStmt() == null)
            return;
        graphNode.setIsGeneratedDueToRecursion(true);
        graphNode.getBlockStmt().getChildNodes().forEach(node -> {
            parseNode(graphNode, node, umlStrings);
        });
        graphNode.setSequence(umlStrings);
    }

    private static Set<String> getAllSubtypes(String name) {
        Set<String> result = new HashSet<>();
        result.add(name);
        if (subtypeMapGroupedBySupertype.containsKey(name)) {
            for (String subtype : subtypeMapGroupedBySupertype.get(name)) {
                result.addAll(getAllSubtypes(subtype));
            }
        }
        return result;
    }

    private static Set<String> getAllSupertypes(String name) {
        Set<String> result = new HashSet<>();
        result.add(name);
        if (supertypeMapGroupedBySubtype.containsKey(name)) {
            for (String subtype : supertypeMapGroupedBySubtype.get(name)) {
                result.addAll(getAllSupertypes(subtype));
            }
        }
        return result;
    }

    private static ReferenceDto findReferenceByName(String name) {
        return referencedMethods.stream().filter(r -> r.getMethodName().equals(name)).findFirst().orElse(null);
    }
}
