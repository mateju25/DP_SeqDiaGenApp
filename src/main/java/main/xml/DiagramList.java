package main.xml;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class DiagramList {
    private List<Diagram> diagrams;

    public DiagramList() {
        this.diagrams = new ArrayList<>();
    }

    @XmlElementWrapper
    @XmlElement(name="diagram")
    public List<Diagram> getDiagrams() {
        return diagrams;
    }

    public void setDiagrams(List<Diagram> diagrams) {
        this.diagrams = diagrams;
    }
}
