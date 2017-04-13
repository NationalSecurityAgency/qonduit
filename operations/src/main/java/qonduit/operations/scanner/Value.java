package qonduit.operations.scanner;

import qonduit.utils.ByteArrayUtils;

public class Value {

    protected byte[] value;

    public Value() {
    }

    public Value(byte[] v) {
        this.setValue(v);
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] v) {
        this.value = ByteArrayUtils.copy(v);
    }

}
