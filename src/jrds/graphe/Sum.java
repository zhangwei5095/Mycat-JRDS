package jrds.graphe;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jrds.GraphDesc;
import jrds.HostsList;
import jrds.Probe;
import jrds.RdsGraph;
import jrds.probe.SumProbe;

import org.apache.log4j.Logger;
import org.jrobin.core.FetchData;
import org.jrobin.core.RrdException;
import org.jrobin.graph.LinearInterpolator;
import org.jrobin.graph.Plottable;
import org.jrobin.graph.RrdGraph;
import org.jrobin.graph.RrdGraphDef;

public class Sum extends RdsGraph {
	static final private Logger logger = Logger.getLogger(Sum.class);

	static final HostsList hl = HostsList.getRootGroup();
	static final GraphDesc gd = new GraphDesc();
	static {
		gd.setGraphName("Sum");
		gd.setGraphTitle("Sum");
		gd.setHostTree(new Object[] {GraphDesc.HOST, GraphDesc.TITLE});
		gd.setViewTree(new Object[] {GraphDesc.SERVICES,  "Sum", GraphDesc.TITLE});
	}

	public Sum(Probe theStore) {
		super(theStore, gd);
		gd.setGraphName(theStore.getName());
		gd.setGraphTitle(theStore.getName());
	}
	
	public RrdGraph getRrdGraph(Date startDate, Date endDate) throws
	IOException, RrdException {
		SumProbe p = (SumProbe) probe;
		double[][] allvalues = null;
		GraphDesc tempgd = null;
		FetchData fd = null;
		for(Iterator i = p.getProbeList().iterator() ; i.hasNext() ;) {
			String name = (String)i.next();
			RdsGraph g = hl.getGraphById(name.hashCode());
			if(g != null) {
				tempgd = g.getGraphDesc();
				fd = g.getProbe().fetchData(startDate, endDate);
				if(allvalues != null) {
					double[][] tempallvalues = fd.getValues();
					for(int c = 0 ; c < tempallvalues.length ; c++) {
						for(int r = 0 ; r < tempallvalues[c].length; r++) {
							allvalues[c][r] += tempallvalues[c][r];
						}
					}
				}
				else {
					allvalues = fd.getValues();
				}
			}
			else {
				logger.error("Graph not found: " + name);
			}
		}
		Map ownValues = new HashMap(allvalues.length);
		long[] ts = fd.getTimestamps();
		String[] dsNames = fd.getDsNames();
		for(int i= 0; i < fd.getColumnCount(); i++) {
			Plottable pl = new LinearInterpolator(ts, allvalues[i]);
			ownValues.put(dsNames[i], pl);
		}
		RrdGraphDef tempGraphDef = tempgd.getGraphDef(p, ownValues);
		tempGraphDef.setTimePeriod(startDate, endDate);
		tempGraphDef = graphFormat(tempGraphDef, startDate, endDate);
		return new RrdGraph(tempGraphDef, true);
	}
	

}