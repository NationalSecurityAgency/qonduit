package qonduit.operations.scanner;

import qonduit.operation.OperationResponse;

public class KVPair extends OperationResponse {

    private Key key;
    private Value value;

    public KVPair() {
        super();
    }

    public KVPair(Key k, Value v) {
        super();
        this.key = k;
        this.value = v;
    }

    public Key getKey() {
        return key;
    }

    public Value getValue() {
        return value;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    public void setValue(Value value) {
        this.value = value;
    }

}
