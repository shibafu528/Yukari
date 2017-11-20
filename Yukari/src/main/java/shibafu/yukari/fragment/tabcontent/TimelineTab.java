package shibafu.yukari.fragment.tabcontent;

public interface TimelineTab {
    /**
     * ユーザ操作によって閉じることのできるタブか？
     * @return 閉じる操作が可能かどうか
     */
    boolean isCloseable();

    void scrollToTop();
    void scrollToBottom();
    void scrollToOldestUnread();
    void scrollToPrevPage();
    void scrollToNextPage();
}
