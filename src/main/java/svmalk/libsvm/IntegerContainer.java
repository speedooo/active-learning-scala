package svmalk.libsvm;public class IntegerContainer implements java.io.Serializable {    private int val;    public IntegerContainer(int value) {        this.val = value;    }    public int getValue() {        return this.val;    }    public void setValue(int value) {        this.val = value;    }}