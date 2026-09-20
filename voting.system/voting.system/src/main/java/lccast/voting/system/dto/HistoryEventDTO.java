package lccast.voting.system.dto;

public class HistoryEventDTO {

    private String action;
    private String id;
    private Integer count;

    public HistoryEventDTO() {}

    public static HistoryEventDTO of(String action, String id) {
        HistoryEventDTO dto = new HistoryEventDTO();
        dto.action = action;
        dto.id = id;
        return dto;
    }

    public static HistoryEventDTO ofCount(String action, int count) {
        HistoryEventDTO dto = new HistoryEventDTO();
        dto.action = action;
        dto.count = count;
        return dto;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}