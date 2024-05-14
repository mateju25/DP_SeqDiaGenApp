package main.xml;

import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Diagram {
    private String title;
    private Boolean onTopOfReferenceTree = false;
    private List<Participant> participants;
    private List<Block> blocks;

    public Diagram(String title) {
        participants = new ArrayList<>();
        blocks = new ArrayList<>();
        this.title = title;
    }

    @Setter
    public static class Participant {
        private String name;

        @XmlElement(name="name")
        public String getName() {
            return name;
        }
    }

    @Setter
    public static class Block {
        private String type;
        private int order;
        private BlockData data;

        @XmlElement(name="type")
        public String getType() {
            return type;
        }

        @XmlElement(name="order")
        public int getOrder() {
            return order;
        }

        @XmlElement(name="data")
        public BlockData getData() {
            return data;
        }
    }

    public enum BlockType {
        MESSAGE_CREATE, MESSAGE_RETURN, ALT_BEGIN, ALT_ELSE, ALT_END
    }

    @Setter
    public static class BlockData {
        private Integer id;
        private String from;
        private String to;
        private String message;

        @XmlElement(name="from")
        public String getFrom() {
            return from;
        }

        @XmlElement(name="id")
        public Integer getId() {
            return id;
        }

        @XmlElement(name="to")
        public String getTo() {
            return to;
        }

        @XmlElement(name="message")
        public String getMessage() {
            return message;
        }
    }

    @XmlElement(name="title")
    public String getTitle() {
        return title;
    }

    @XmlElementWrapper
    @XmlElement(name="participant")
    public List<Participant> getParticipants() {
        return participants;
    }

    @XmlElementWrapper
    @XmlElement(name="block")
    public List<Block> getBlocks() {
        return blocks;
    }

    @Transient
    public void addParticipant(String name) {
        if (participants.stream().anyMatch(participant -> participant.getName() != null && participant.getName().equals(name))) {
            return;
        }
        Participant participant = new Participant();
        participant.setName(name);
        participants.add(participant);
    }

    @Transient
    public void addMessages(List<Block> block) {
        this.blocks.addAll(block);
    }

    @Transient
    public void addCreateMessage(String from, String to, String message, Integer ids) {
        Block block = new Block();
        block.setType(BlockType.MESSAGE_CREATE.toString());
        block.setOrder(blocks.size());
        BlockData data = new BlockData();
        data.setId(ids);
        data.setFrom(from);
        data.setTo(to);
        data.setMessage(message);
        block.setData(data);
        this.blocks.add(block);
    }

    @Transient
    public void addReturnMessage(String from, String to, String message, Integer ids) {
        Block block = new Block();
        block.setType(BlockType.MESSAGE_RETURN.toString());
        block.setOrder(blocks.size());
        BlockData data = new BlockData();
        data.setId(ids);
        data.setFrom(from);
        data.setTo(to);
        data.setMessage(message);
        block.setData(data);
        this.blocks.add(block);
    }

    @Transient
    public void addAltBeginMessage(String message, Integer ids) {
        Block block = new Block();
        block.setType(BlockType.ALT_BEGIN.toString());
        block.setOrder(blocks.size());
        BlockData data = new BlockData();
        data.setId(ids);
        data.setMessage(message);
        block.setData(data);
        this.blocks.add(block);
    }

    @Transient
    public void addAltElseMessage(String message, Integer ids) {
        Block block = new Block();
        block.setType(BlockType.ALT_ELSE.toString());
        block.setOrder(blocks.size());
        BlockData data = new BlockData();
        data.setId(ids);
        data.setMessage(message);
        block.setData(data);
        this.blocks.add(block);
    }

    @Transient
    public void addAltEndMessage(String message, Integer ids) {
        Block block = new Block();
        block.setType(BlockType.ALT_END.toString());
        block.setOrder(blocks.size());
        BlockData data = new BlockData();
        data.setId(ids);
        data.setMessage(message);
        block.setData(data);
        this.blocks.add(block);
    }

    public void removeLastBlock() {
        this.blocks.remove(this.blocks.size() - 1);
    }
}
