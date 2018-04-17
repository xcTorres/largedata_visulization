package com.hbase.endpoint;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.coprocessor.CoprocessorException;
import org.apache.hadoop.hbase.coprocessor.CoprocessorService;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.QualifierFilter;
import org.apache.hadoop.hbase.protobuf.ResponseConverter;
import org.apache.hadoop.hbase.util.Bytes;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.Service;
import com.hbase.endpoint.DateCal;
import com.hbase.protoc.SpatialAgg.SpatialAggRequest;
import com.hbase.protoc.SpatialAgg.SpatialAggResponse;
import com.hbase.protoc.SpatialAgg.SpatialAggService;
import com.hbase.protoc.SpatialAgg.SumMatrix;

/**
 * @author zhou_20180416
 * @category spatial aggregation coprocessor by Get function 
 * @param rowkey, start_time, end_time
 * @return aggregation result(a list)
 */
public class SpatialGetEP extends SpatialAggService implements Coprocessor,CoprocessorService{

	
	private RegionCoprocessorEnvironment env;
	
	//private BufferedWriter out = null;    
	//private Writer w = null;
	
	@Override
	public Service getService() {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public void start(CoprocessorEnvironment arg0) throws IOException {
		// TODO Auto-generated method stub
		if (arg0 instanceof RegionCoprocessorEnvironment) {
            this.env = (RegionCoprocessorEnvironment) arg0;
        }else
            throw new CoprocessorException("Must be loaded on a table region!");
	}

	@Override
	public void stop(CoprocessorEnvironment arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void getSpatialAgg(RpcController controller, SpatialAggRequest request,
			RpcCallback<SpatialAggResponse> done) {
		// TODO Auto-generated method stub
		
		List<Integer> lstData = new ArrayList<Integer>();
        for (int i = 0;i < 65536; i++){
    		lstData.add(0);
    	}
		try {
			/*w = new FileWriter("/tmp/log.txt");
		    out = new BufferedWriter(w);*/
			    
	        //get single rowkey, filter by QualifierFilter
	        List<String> mapRes = DateCal.Calculate(request.getStartTime(),request.getEndTime());
	        	        
	        List<Filter> filterLst = new ArrayList<Filter>();
	        Filter qualifierFilter = null;
	        for(String str : mapRes){
	            qualifierFilter = new QualifierFilter(CompareFilter.CompareOp.EQUAL,new BinaryComparator(Bytes.toBytes(str)));
	            filterLst.add(qualifierFilter);
	        }

	        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ONE,filterLst);
	        Get get = new Get(Bytes.toBytes(request.getRowKey()));
	        get.setFilter(filterList);
			
	        Result getResult = env.getRegion().get(get);
			
            for (String s : mapRes){
            		            	
            	byte[] v = getResult.getValue(Bytes.toBytes("heatmap"), Bytes.toBytes(s));
                if (v != null) {
					valAggCal(v, lstData);
				}
            }
            /*out.write("Agg time: "+ (t2 - t1) + "\n");
            out.write("step2" + "\n");*/
            
           /* int count = 0;
            for(int i : lstData) {
            	if(i != 0) {
            		out.write(i + " ");
            		count++;
            	}
            	if (count == 50) {
					out.write("\n");
					count = 0;
				}
            }*/
            
            sendResponseBack(lstData, done);
	        
		} catch (IOException e) {
			// TODO Auto-generated catch block
			ResponseConverter.setControllerException(controller,e);
            sendResponseBack(lstData,done);
		}finally{
		
	        /*try {
	        	out.close();
	        	w.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
		}
	}
	
	private void sendResponseBack(List<Integer> lstData, RpcCallback<SpatialAggResponse> done) {
		SumMatrix[] sum = {SumMatrix.newBuilder().addAllArray(lstData).build()};
        List<SumMatrix> lst = Arrays.asList(sum);
        SpatialAggResponse response = SpatialAggResponse.newBuilder().addAllSum(lst).build();
        done.run(response);
	}
	
	
	
	private void valAggCal(byte[] val, List<Integer> l){
    	
        int start = 1, end;
        for (int i = 5; i < val.length; i++){
            if(val[i] == 44){
        		end = i - 1;
        		val2byteArray(val, start, end,l);
        		start = i + 2;
    		}
            if(i == val.length - 2)
        		val2byteArray(val, start, i,l);
    	} 
	}

    private void val2byteArray(byte[] val,int s,int e, List<Integer> l){

        for(int i = s + 1; i < e; i++)
    	{            
            if (val[i] == 58){
            	int index = byte2Int(val, s, i - 1);
                int value = byte2Int(val, i + 2, e);
                /*try {
					out.write(index + " " + value + "\n");
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}*/
                l.set(index, l.get(index) + value);
                break;
    		}
    	}
	}

    private int byte2Int(byte[] v, int s, int e){

        int n = 0;
        int offset = 1;
        for (int i = e; i >= s; i--){
    		n += ((int)v[i] - 48)  * offset;
    		offset *= 10;
    	}

        return n;
    }
	

}
