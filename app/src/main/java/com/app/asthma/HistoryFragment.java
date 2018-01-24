package com.app.asthma;

import android.graphics.Color;
import android.support.v4.app.Fragment;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.app.asthma.R;
/*import org.achartengine.*;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;*/

/**
 * Created by siddartha on 11/6/16.
 */

public class HistoryFragment extends Fragment {

    private LinearLayout chartView;

    public HistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View inflatedView = inflater.inflate(R.layout.history_fragment, container, false);
        chartView = (LinearLayout)inflatedView.findViewById(R.id.chart);
        //showChart();

        return inflatedView;
    }

    /*public void showChart(){
        XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();

        XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();

        double[] xvals = new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        double[] yvals = new double[]{25, 45, 50, 65, 80, 84, 96, 106, 123, 118, 134, 121};
        XYSeries series = new XYSeries("Sales Data");

        for (int i = 0; i < 12; i++)
            series.add(xvals[i], yvals[i]);
        XYSeriesRenderer renderer = new XYSeriesRenderer();

        renderer.setColor(Color.BLUE);
        renderer.setLineWidth(2);
        renderer.setPointStyle(PointStyle.CIRCLE);
        renderer.setPointStrokeWidth(3);
        renderer.setDisplayBoundingPoints(true);
        renderer.setFillPoints(true);
        renderer.setDisplayChartValues(true);


        mDataset.addSeries(series);
        mRenderer.addSeriesRenderer(renderer);

        mRenderer.setMarginsColor(Color.argb(0x00, 0xff, 0x00, 0x00));
        mRenderer.setPanEnabled(false, false);
        mRenderer.setYAxisMax(35);
        mRenderer.setYAxisMin(0);
        mRenderer.setShowGrid(true);
        //mRenderer.`

        GraphicalView graphView = ChartFactory.getLineChartView(getActivity(), mDataset, mRenderer);
        chartView.addView(graphView);



    }*/
}
