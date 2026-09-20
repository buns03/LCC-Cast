package lccast.voting.system.dto;

public class AuditDetailDTO {

    private String target;
    private String field;
    private String from;
    private String to;

    public AuditDetailDTO() {}

    public AuditDetailDTO(String target, String field, String from, String to) {
        this.target = target;
        this.field = field;
        this.from = from;
        this.to = to;
    }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
}