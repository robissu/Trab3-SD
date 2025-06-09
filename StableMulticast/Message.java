package StableMulticast;

import java.io.Serializable;

public class Message implements Serializable {
    public int sender;
    public int[] vectorClock;
    public String content;

    public Message(int sender, int[] vectorClock, String content) {
        this.sender = sender;
        this.vectorClock = vectorClock;
        this.content = content;
    }
}
