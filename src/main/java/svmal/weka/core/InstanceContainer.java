package svmal.weka.core;import weka.core.Instance;import weka.core.Instances;public interface InstanceContainer {    public double classValue() throws Exception;    public void setClassValue(double value) throws Exception;    public double weight();    public void setWeight(double weight);    public int getIndex();    public void setIndex(int indx);//    public Instance toInstance(Instances dataSet) throws Exception;}