package tsml.classifiers.shapelet_based.dev.type;

import tsml.data_containers.TimeSeriesInstance;

public class ShapeletIndependentMV extends ShapeletSingle {

    private int seriesIndex;
    private double[] data;

    public ShapeletIndependentMV(int start, int length, int instanceIndex, double classIndex,  int seriesIndex, TimeSeriesInstance instance){
        super(start, length, instanceIndex, classIndex);
        this.seriesIndex = seriesIndex;
        this.setData(instance);
    }

    public int getSeriesIndex(){
        return seriesIndex;
    }

    public void setData(TimeSeriesInstance instance) {
        this.data = new double[length];
        for (int i=0;i<length;i++){
            this.data[i] = instance.get(this.seriesIndex).get(start+i);
        }
        this.data = NORMALIZE.rescaleSeries(this.data);
    }

    public double[] getData(){
        return this.data;
    }

    @Override
    public String toString(){
        return "Instance: " + instanceIndex + " Series: " + seriesIndex + " Start: " + start + " Length: " + length +
                 " Quality: " + quality + " Class "  + classIndex +  "\n";//->" + Arrays.toString(data) ;
    }




}