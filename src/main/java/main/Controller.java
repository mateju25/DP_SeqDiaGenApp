package main;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import main.core.Generator;
import main.core.GraphNode;
import main.core.Utils;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Controller {
    public TextField source;
    public TextField output;
    public TextField libs;
    public Button generate;
    public ImageView imageView;
    public Button open;

    public Map<String, GraphNode> result = new HashMap<>();
    public GraphNode selectedNode;
    public HBox participants;
    public ListView<String> listView;
    public CheckBox onlyProject;
    public CheckBox noconstructors;
    public CheckBox allConstructors;
    public Button tooltip;
    public HBox refs;


    public void generateOutput(ActionEvent actionEvent) throws IOException {
        Generator.LIBS = libs.getText();
//        for (int i = 2; i <= 11; i++) {
////            Generator.ONLY_MODEL_INFO = true;
//            Generator.OUTPUT = "D:/Skola/DP/SeqDiaGen/out" + i + "/";
//            Generator.CONCRETE_TEST = "D:/Skola/DP/SeqDiaGen/src/test" + i + "/java";
//            Generator.main(new String[0]);
//        }
//        if (true)
//            return;
        Generator.ONLY_MODEL_INFO = false;
        Generator.LIBS = libs.getText();
        Generator.OUTPUT = output.getText();
        Generator.CONCRETE_TEST = source.getText();
        var data = Generator.main(new String[0]);
        result = data.getKey();

        ObservableList<String> items = FXCollections.observableArrayList (data.getValue().keySet().stream().sorted().collect(Collectors.toList()));
        this.listView.setItems(items);

        this.listView.setCellFactory(param -> new ListCell<String>() {
            {
                setStyle("-fx-padding: 20px");
            }

            private ImageView imageListView = new ImageView();
            @Override
            public void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // make ratio of width to height
                    imageListView.setFitWidth(300);
                    imageListView.setPreserveRatio(true);
                    imageListView.setImage(data.getValue().get(name));
//                    setText(name);
//                    setGraphic(imageListView);


                    VBox vBox = new VBox(5);
                    vBox.getChildren().addAll(
                            imageListView,
                            new Label(data.getKey().containsKey(name) ? data.getKey().get(name).getMethodName() : "")
                    );
                    setGraphic(vBox);

                    imageListView.setOnMouseClicked(event -> {
                        imageView.setFitWidth(data.getValue().get(name).getWidth());
                        imageView.setFitHeight(data.getValue().get(name).getHeight());
                        imageView.setImage(data.getValue().get(name));

                        selectedNode = result.get(name);
                        if (selectedNode != null) {
                            rerenderImage();
                        }
                    });
                }
            }
        });
        listView.setOnMouseClicked(new EventHandler<MouseEvent>() {

            @Override
            public void handle(MouseEvent event) {
                participants.getChildren().clear();

                var name = listView.getSelectionModel().getSelectedItem();
                imageView.setFitWidth(data.getValue().get(name).getWidth());
                imageView.setFitHeight(data.getValue().get(name).getHeight());
                imageView.setImage(data.getValue().get(name));

                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(name);
                clipboard.setContent(content);

                selectedNode = result.get(name);
                if (selectedNode != null) {
                    rerenderImage();
                }
            }
        });
    }

    public void openImage(ActionEvent actionEvent) {
        this.participants.getChildren().clear();
        FileChooser fileChooser = new FileChooser();

        //Set extension filter
        FileChooser.ExtensionFilter extFilterPNG = new FileChooser.ExtensionFilter("PNG files (*.png)", "*.PNG");
        fileChooser.getExtensionFilters().addAll(extFilterPNG);

        //Show open file dialog
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            Image image = new Image(file.toURI().toString());
            imageView.setFitWidth(image.getWidth());
            imageView.setFitHeight(image.getHeight());
            imageView.setImage(image);

            this.selectedNode = result.get(file.getName());
            if (selectedNode != null) {
                rerenderImage();
            }
        }
    }

    public void rerenderImage() {
        var diagram = makeSequenceDiagram(selectedNode);
        tooltip.setTooltip(new Tooltip(diagram));

        SourceStringReader reader = new SourceStringReader(diagram);
        try {
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            reader.outputImage(pngOut);
            Image image1 = new Image(new ByteArrayInputStream(pngOut.toByteArray()));

            imageView.setFitWidth(image1.getWidth());
            imageView.setFitHeight(image1.getHeight());
            imageView.setPreserveRatio(true);
            imageView.resize(0.5, 0.5);
            imageView.setImage(image1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String makeSequenceDiagram(GraphNode g) {
        StringBuilder sb = new StringBuilder();

        sb.append("title ").append(g.getMethodName()).append("\n");
        sb.append(" -> ").append(Utils.getNameOfClassFromSignature(g.getMethodName())).append("\n");
        g.getSequence().forEach(s -> {
            sb.append(s).append("\n");
        });
        sb.append("return ").append("\n");
        sb.append("@enduml\n");
        sb.insert(0, "skinparam sequenceReferenceBackgroundColor #GreenYellow\n");
        sb.insert(0, "autoactivate on\n");
        sb.insert(0, "@startuml\n");
        return sb.toString();
    }

    public void findSourceCode(ActionEvent actionEvent) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Source code");

        File selectedDirectory = chooser.showDialog(null);
        if (selectedDirectory != null) {
            source.setText(selectedDirectory.getPath());
        }
    }

    public void findOutput(ActionEvent actionEvent) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Output");

        File selectedDirectory = chooser.showDialog(null);
        if (selectedDirectory != null) {
            output.setText(selectedDirectory.getPath());
        }
    }

    public void findLibs(ActionEvent actionEvent) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Libs");

        File selectedDirectory = chooser.showDialog(null);
        if (selectedDirectory != null) {
            libs.setText(selectedDirectory.getPath());
        }
    }

    public void changeOnlyProject(ActionEvent actionEvent) {
        for (Node check: this.participants.getChildren()) {
            ((CheckBox) check).setSelected(true);
        }
        rerenderImage();
    }

    public void copyToClipboard(ActionEvent actionEvent) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(tooltip.getTooltip().getText());
        clipboard.setContent(content);
    }
}
