package us.codecraft.webmagic;

public enum Status {

    INIT(0), RUNNING(1), STOPPED(2);

    private int value;

    private Status(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static Status fromValue(int value) {
        for (Status status : Status.values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        //default value
        return INIT;
    }
}

