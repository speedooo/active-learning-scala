package svmal.libsvm;import java.util.*;import svmal.weka.classifiers.DistributionClassifier;import svmal.weka.core.DataContainer;import svmal.weka.core.Instance2;import svmal.weka.core.InstanceContainer;import weka.core.*;public class SvmLib extends DistributionClassifier implements        OptionHandler, java.io.Serializable {    private svm_parameter m_param = null;    private svm_model m_model = null;    private SvmLibProblem m_prob = null;    public SvmLib() {        this.m_param = new svm_parameter();    }    public double[] distributionForInstance(Instance instance) throws Exception {        int cls = (int) (this.classifyInstance(instance));//        double[] dist = new double[instance.numAttributes() - 1];        double[] dist = new double[2];        dist[cls] = 1.0;        dist[(cls + 1) % 2] = 0.0;        return dist;    }    @Override    public Capabilities getCapabilities() {        return null;    }    public double classifyInstance(Instance2 instance) throws Exception {        return svm.svm_predict(this.m_model,                this.convertInstance(instance, this.m_prob));    }    public double classifyInstance(InstanceContainer ins) throws Exception {        return svm.svm_predict(this.m_model, ((SvmLibPair) ins).getNodes());    }    public double confindenceOnInstance(Instance2 instance) throws Exception {        // works only for two classes!        return svm.svm_twoClass_confidence(this.m_model,                this.convertInstance(instance,                        this.m_prob), 0, 1        );    }    public double confindenceOnInstance(InstanceContainer ins) throws Exception {        // works only for two classes!        return svm.svm_twoClass_confidence(this.m_model,                ((SvmLibPair) ins).getNodes(), 0, 1);    }    public double marginOfInstance(Instance2 instance) throws Exception {        return svm.svm_margin(this.m_model,                this.convertInstance(instance, this.m_prob), 0, 1);    }    public double marginOfInstance(InstanceContainer ins) throws Exception {        return svm.svm_margin(this.m_model, ((SvmLibPair) ins).getNodes(), 0, 1);    }    public double marginOfSvm() throws Exception {        // only for linearly separable !!!        return 1.0 / (svm.svm_WSize(this.m_model, 0, 1));    }    public int getNumSV() {        return this.m_model.l;    }    public int getNumBSV() {        double c = this.m_param.C;        int res = 0;        for (int i = 0; i < this.m_model.l; i++) {            if (Utils.eq(Math.abs(this.m_model.sv_coef[0][i]), c)) {                res++;            }        }        return res;    }    public SvmLibProblem getProblem() {        return this.m_prob;    }    public void setProblem(SvmLibProblem prob) {        this.m_prob = prob;    }    public svm_parameter getParams() {        return this.m_param;    }    public void buildClassifier(Instances data) throws Exception {        // initialize all members        this.m_prob = (SvmLibProblem) this.convertData(data);        this.buildClassifier();    }    public void buildClassifier(DataContainer data) throws Exception {        this.m_prob = (SvmLibProblem) data;        this.buildClassifier();    }    private void buildClassifier() throws Exception {        double[] successByClass = new double[2];        double totalSuccess;        if (this.m_param.gamma == 0) {            this.m_param.gamma = 1.0 / (this.m_prob.prob.l - 1); // 1/max_index        }        this.m_model = svm.svm_train(this.m_prob.prob, this.m_param);    }    private int[] findSupportVectors() {        int i, j;        svm_node[] sv;        double coef;        int[] isSupportVector = new int[this.m_prob.prob.l];        for (j = 0; j < this.m_prob.prob.l; j++) {            isSupportVector[j] = 0;        }        for (i = 0; i < this.m_model.SV.length; i++) {            sv = this.m_model.SV[i];            for (j = 0; j < this.m_prob.prob.l; j++) {                if (SvmLibPair.isEqual(this.m_prob.prob.x[j], sv)) {                    if (Utils.eq(this.m_model.sv_coef[0][i], this.m_param.C)) {                        isSupportVector[j] = 2;                    } else {                        isSupportVector[j] = 1;                    }                    break;                }            }        }        return isSupportVector;    }    public DataContainer convertData(Instances data) throws Exception {        SvmLibProblem svmProb = new SvmLibProblem();        svmProb.classIndex = data.classIndex();        this.findMinMax(data, svmProb);        svmProb.prob = new svm_problem();        svmProb.prob.l = data.numInstances();        svmProb.indexes = new FastVector(svmProb.prob.l);        svmProb.prob.x = new svm_node[svmProb.prob.l][];        svmProb.prob.y = new double[svmProb.prob.l];        // transfer data to 'libsvm' data structures        int i;        Instance2 ins;//        System.out.print("Converting data ");        for (i = 0; i < svmProb.prob.l; i++) {            ins = (Instance2) data.instance(i);            svmProb.prob.x[i] = this.convertInstance(ins, svmProb);            svmProb.prob.y[i] = ins.classValue();            svmProb.indexes.addElement(new IntegerContainer(ins.getIndex()));            //System.out.print(".");        }//        System.out.println("done.");        return svmProb;    }    //RON - quick-n-dirty solution    public DataContainer convertData(double[][] attr, double[] lables) throws            Exception {        SvmLibProblem svmProb = new SvmLibProblem();        svmProb.classIndex = attr.length; // note: no affect        this.findMinMax(attr, svmProb);        svmProb.prob = new svm_problem();        svmProb.prob.l = lables.length;        svmProb.indexes = new FastVector(svmProb.prob.l);        svmProb.prob.x = new svm_node[svmProb.prob.l][];        svmProb.prob.y = new double[svmProb.prob.l];        // transfer data to 'libsvm' data structures        int i;//        System.out.print("Converting data ");        for (i = 0; i < svmProb.prob.l; i++) {            svmProb.prob.x[i] = convert(attr[i], svmProb);            svmProb.prob.y[i] = lables[i];            svmProb.indexes.addElement(new IntegerContainer(i));            //System.out.print(".");        }//        System.out.println("done.");        return svmProb;    }    //--    public DataContainer convertData(Instances data, DataContainer former) throws            Exception {        SvmLibProblem svmProb = new SvmLibProblem();        svmProb.classIndex = data.classIndex();        svmProb.minValue = ((SvmLibProblem) former).minValue;        svmProb.maxValue = ((SvmLibProblem) former).maxValue;        svmProb.prob = new svm_problem();        svmProb.prob.l = data.numInstances();        svmProb.indexes = new FastVector(svmProb.prob.l);        svmProb.prob.x = new svm_node[svmProb.prob.l][];        svmProb.prob.y = new double[svmProb.prob.l];        // transfer data to 'libsvm' data structures        int i;        Instance2 ins;//        System.out.print("Converting data ");        for (i = 0; i < svmProb.prob.l; i++) {            ins = (Instance2) data.instance(i);            svmProb.prob.x[i] = this.convertInstance(ins, svmProb);            svmProb.prob.y[i] = ins.classValue();            svmProb.indexes.addElement(new IntegerContainer(ins.getIndex()));        }//        System.out.println("done.");        return svmProb;    }    public double testOnTrainingSet(DataContainer data, double[] successByClass) throws            Exception {        int i, size = data.size();        int classVal, actualClassVal;        int correct = 0;        int[] correctByClass = new int[2];        int[] sizeByClass = new int[2];        InstanceContainer ins;        for (i = 0; i < 2; i++) {            correctByClass[i] = 0;            sizeByClass[i] = 0;        }        for (i = 0; i < size; i++) {            ins = data.getInstance(i);            actualClassVal = (int) (ins.classValue());            classVal = (int) (this.classifyInstance(ins));            System.out.println("ins " + ins.toString() + " cls=" + classVal);            if (actualClassVal == classVal) {                correct++;                correctByClass[actualClassVal]++;            }            sizeByClass[actualClassVal]++;        }        for (i = 0; i < 2; i++) {            successByClass[i] = (((double) correctByClass[i]) /                    ((double) sizeByClass[i]));        }        return (((double) correct) / ((double) size));    }    /*RON - q-n-d */    public double kernelDistance(InstanceContainer ins1, InstanceContainer ins2) throws            Exception {        double dist;        svm_node[] x = ((SvmLibPair) ins1).getNodes();//may throw classCastException        svm_node[] y = ((SvmLibPair) ins2).getNodes();        double kxx = svm.kernelEval(x, x, this.getParams(), false);        kxx += svm.g_maxFix;        double kyy = svm.kernelEval(y, y, this.getParams(), false);        kyy += svm.g_maxFix;        double kxy = svm.kernelEval(x, y, this.getParams(), false);        dist = kxx + kyy - 2 * kxy;        dist = Math.sqrt(dist);        return dist;    }    /*////*/    //RON - quick-n-dirty solution    private void findMinMax(double[][] attr, SvmLibProblem svmProb) {        svmProb.maxValue = new double[attr[0].length];        svmProb.minValue = new double[attr[0].length];        Arrays.fill(svmProb.maxValue, Double.MIN_VALUE);        Arrays.fill(svmProb.minValue, Double.MAX_VALUE);        for (int c = 0; c < attr[0].length; ++c) {            for (int r = 0; r < attr.length; ++r) {                if (attr[r][c] > svmProb.maxValue[c]) {                    svmProb.maxValue[c] = attr[r][c];                }                if (attr[r][c] < svmProb.minValue[c]) {                    svmProb.minValue[c] = attr[r][c];                }            }        }    }    //--    private void findMinMax(Instances data, SvmLibProblem svmProb) throws            Exception {        int i, j, id, numInstances, numAtt;        boolean isSparse = false; //((data.instance(0)) instanceof SparseInstance);        double val;        Instance2 ins;        FastVector ids, vals;        Enumeration idsEnum, valsEnum;        numAtt = data.numAttributes();        boolean[] found = new boolean[numAtt + 1];        svmProb.maxValue = new double[numAtt + 1];        svmProb.minValue = new double[numAtt + 1];        for (j = 0; j < numAtt; j++) {            found[j] = false;            svmProb.maxValue[j] = -Double.MAX_VALUE;            svmProb.minValue[j] = Double.MAX_VALUE;        }        svmProb.maxValue[numAtt] = 1.0;        svmProb.minValue[numAtt] = 0.0;        found[numAtt] = true;        numInstances = data.numInstances();        // find max and min values for each attribute        for (i = 0; i < numInstances; i++) {            ins = (Instance2) data.instance(i);            ids = ins.ids();            vals = ins.values();            if (ids.size() != vals.size()) {                throw new Exception("ids and vals size are different");            }            idsEnum = ids.elements();            valsEnum = vals.elements();            while (idsEnum.hasMoreElements()) {                id = ((Integer) idsEnum.nextElement()).intValue();                val = ((Double) valsEnum.nextElement()).doubleValue();                if (id != svmProb.classIndex) {                    if (val > svmProb.maxValue[id]) {                        svmProb.maxValue[id] = val;                    }                    if (val < svmProb.minValue[id]) {                        svmProb.minValue[id] = val;                    }                    found[id] = true;                }            }        }        for (j = 0; j < numAtt; j++) {            if (!found[j]) {                svmProb.maxValue[j] = svmProb.minValue[j] = Double.NaN;                continue;            }            if (isSparse) {                val = 0.0;                if (val > svmProb.maxValue[j]) {                    svmProb.maxValue[j] = val;                }                if (val < svmProb.minValue[j]) {                    svmProb.minValue[j] = val;                }            }            if (Utils.eq(svmProb.maxValue[j], svmProb.minValue[j])) {                svmProb.minValue[j] = 0;            }        }    }    private svm_node[] convertInstance(Instance2 instance, SvmLibProblem svmProb) throws            Exception {        return this.scaleAndTransferInstance(instance, svmProb);    }    // RON - quick-n-dirty solution    private svm_node[] convert(double[] attr, SvmLibProblem svmProb) throws            Exception {        svm_node[] nodes = new svm_node[attr.length + 1];        double val;        for (int i = 0; i < nodes.length - 1; ++i) {            nodes[i] = new svm_node();            nodes[i].index = i + 1; //starting to count from 1            // scale value to [-1,1]            val = attr[i];            if (Utils.eq(svmProb.maxValue[i], svmProb.minValue[i])) {                val = 0.0;            } else {                val = -1.0 +                        2.0 *                                ((val - svmProb.minValue[i]) /                                        (svmProb.maxValue[i] - svmProb.minValue[i]));            }            nodes[i].value = val;        }        // do not know why doing so..        nodes[nodes.length - 1] = new svm_node();        nodes[nodes.length - 1].index = nodes.length; //starting to count from 1        nodes[nodes.length - 1].value = 1;        return nodes;    }    //--    private svm_node[] scaleAndTransferInstance(Instance2 ins,                                                SvmLibProblem svmProb) throws            Exception {        int numActualFeatures = 0;        svm_node[] nodes;        double val;        int id;        FastVector ids, vals;        Enumeration idsEnum, valsEnum;        ids = ins.ids();        vals = ins.values();        if (ids.size() != vals.size()) {            throw new Exception("ids and vals size are different");        }        // count number of actual features        numActualFeatures = 0;        idsEnum = ids.elements();        while (idsEnum.hasMoreElements()) {            id = ((Integer) idsEnum.nextElement()).intValue();            if (Double.isNaN(svmProb.maxValue[id])) {                if (!Double.isNaN(svmProb.minValue[id])) {                    throw new Exception("Only max value is NaN");                }                continue;            }            if (id != svmProb.classIndex) {                numActualFeatures++;            }        }        nodes = new svm_node[numActualFeatures + 1];        // insert data to nodes        numActualFeatures = 0;        idsEnum = ids.elements();        valsEnum = vals.elements();        while (idsEnum.hasMoreElements()) {            id = ((Integer) idsEnum.nextElement()).intValue();            val = ((Double) valsEnum.nextElement()).doubleValue();            if (Double.isNaN(svmProb.maxValue[id])) {                continue;            }            if (id != svmProb.classIndex) {                nodes[numActualFeatures] = new svm_node();                nodes[numActualFeatures].index = id + 1;                // scale value to [-1,1]                if (Utils.eq(svmProb.maxValue[id], svmProb.minValue[id])) {                    val = 0.0;                } else {                    val = -1.0 +                            2.0 *                                    ((val - svmProb.minValue[id]) /                                            (svmProb.maxValue[id] - svmProb.minValue[id]));                }                nodes[numActualFeatures].value = val;                numActualFeatures++;            }        }        nodes[numActualFeatures] = new svm_node();        nodes[numActualFeatures].index = svmProb.maxValue.length;        nodes[numActualFeatures].value = 1.0;        return nodes;    }    public void setType(int type) {        this.m_param.svm_type = type;    }    public void setKernelType(int type) {        this.m_param.kernel_type = type;    }    public void setDegree(double d) {        this.m_param.degree = d;    }    public double getGamma() {        return this.m_param.gamma;    }    public void setGamma(double g) {        this.m_param.gamma = g;    }    public void setCoef0(double c) {        this.m_param.coef0 = c;    }    public void setCacheSize(double sz) {        this.m_param.cache_size = sz;    }    public void setEpsilon(double e) {        this.m_param.eps = e;    }    public void setCost(double c) {        this.m_param.C = c;    }    public void setNU(double nu) {        this.m_param.nu = nu;    }    public void setPEps(double p) {        this.m_param.p = p;    }    public void setShrinking(int sh) {        this.m_param.shrinking = sh;    }    public Enumeration listOptions() {        FastVector newVector = new FastVector(5);        newVector.addElement(new Option(                "\tType of kernel:\n\t\t0 - LINEAR\n\t\t1 - POLYNOMIAL\n\t\t2 - RBF\n\t\t3 - SIGMOID\n"                        + "\t(Default is 2(RBF))",                "K", 1, "-K <type of kernel>"        ));        newVector.addElement(new Option(                "\tCost.\n"                        + "\t(Default is 1000)",                "C", 1, "-C <cost>"        ));        newVector.addElement(new Option(                "\tGamma (for poly/rbf/sigmoid).\n"                        + "\t(Default is 0.5)",                "G", 1, "-G <gamma>"        ));        newVector.addElement(new Option(                "\tDegree (for poly).\n"                        + "\t(Default is 3)",                "D", 1, "-D <degree>"        ));        newVector.addElement(new Option(                "\tCoef0 (for poly/sigmoid).\n"                        + "\t(Default is 0)",                "F", 1, "-F <coef0>"        ));        return newVector.elements();    }    public String[] getOptions() {        String[] options = new String[10];        int current = 0;        options[current++] = "-K";        options[current++] = "" + this.m_param.kernel_type;        options[current++] = "-C";        options[current++] = "" + this.m_param.C;        options[current++] = "-G";        options[current++] = "" + this.m_param.gamma;        options[current++] = "-D";        options[current++] = "" + this.m_param.degree;        options[current++] = "-F";        options[current++] = "" + this.m_param.coef0;        return options;    }    public void setOptions(String options[]) throws Exception {        String KString = Utils.getOption('K', options);        if (KString.length() != 0) {            this.setKernelType((new Integer(KString)).intValue());            if ((this.m_param.kernel_type < 0) ||                    (this.m_param.kernel_type > 3)) {                throw new Exception(                        "SvmLib: Kernel types are: 0-linear, 1-polynomial, 2-rbf, 3-sigmoid, please.");            }        }        String CString = Utils.getOption('C', options);        if (CString.length() != 0) {            this.setCost((new Double(CString)).doubleValue());            if (this.m_param.C < 0) {                throw new Exception("SvmLib: cost is positive, please.");            }        }        String GString = Utils.getOption('G', options);        if (GString.length() != 0) {            this.setGamma((new Double(GString)).doubleValue());            if (this.m_param.gamma < 0) {                throw new Exception("SvmLib: gamma is positive, please.");            }        }        String DString = Utils.getOption('D', options);        if (DString.length() != 0) {            this.setDegree((new Double(DString)).doubleValue());            if (this.m_param.degree < 0) {                throw new Exception("SvmLib: degree is positive, please.");            }        }        String FString = Utils.getOption('F', options);        if (FString.length() != 0) {            this.setCoef0((new Double(FString)).doubleValue());            if (this.m_param.coef0 < 0) {                throw new Exception("SvmLib: coef0 is positive, please.");            }        }        Utils.checkForRemainingOptions(options);    }    public String toString() {        StringBuffer msg = new StringBuffer();        msg.append("SvmLib\n-----------------------\n" +                "Parameter values:\n\tKernel Type = " +                this.m_param.kernel_type +                "\n\tCost = " + this.m_param.C +                "\n\tGamma = " + this.m_param.gamma +                "\n\tDegree = " + this.m_param.degree +                "\n\tCoef0 = " + this.m_param.coef0 + "\n");        return msg.toString();    }//    @Override//    public void buildClassifier(Instances data) throws Exception {//        throw new Exception("stub");//    }}