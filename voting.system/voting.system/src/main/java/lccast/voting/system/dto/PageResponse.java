package lccast.voting.system.dto;

import java.util.List;

public class PageResponse<T> {

    private List<T> items;
    private int page;
    private int totalPages;
    private long totalItems;

    public PageResponse() {}

    public PageResponse(List<T> items, int page, int totalPages, long totalItems) {
        this.items = items;
        this.page = page;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
    }

    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public long getTotalItems() { return totalItems; }
    public void setTotalItems(long totalItems) { this.totalItems = totalItems; }
}