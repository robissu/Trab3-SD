package StableMulticast;

import java.io.Serializable;

public class StableMulticastMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private String content;
    private int[] senderVC; // The sender's view of its own clock (VC[sender][*])
    private int senderId;
    private long timestamp; // For debugging/ordering within buffer if needed

    public StableMulticastMessage(String content, int[] senderVC, int senderId) {
        this.content = content;
        this.senderVC = senderVC;
        this.senderId = senderId;
        this.timestamp = System.nanoTime(); // A simple timestamp
    }

    public String getContent() {
        return content;
    }

    public int[] getSenderVC() {
        return senderVC;
    }

    public int getSenderId() {
        return senderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Msg from P").append(senderId).append(": \"").append(content).append("\" VC: [");
        if (senderVC != null) {
            for (int i = 0; i < senderVC.length; i++) {
                sb.append(senderVC[i]);
                if (i < senderVC.length - 1) {
                    sb.append(", ");
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }
}