import aggregators.AggregatorsPackage;
import aggregators.HseAggregator;
import org.json.simple.JSONArray;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class Tester {
    public static void main(String[] args) throws Exception {
        List<HseAggregator> data = AggregatorsPackage.getAllHseAggregators();
        AggregatorsPackage.printData(data);
        JSONArray jsonList = new JSONArray();
        for (HseAggregator aData : data) {
            jsonList.add(aData.serializeToJSON());
        }
        FileWriter jsonData = new FileWriter(new File("Stats.json"));
        jsonData.write(jsonList.toJSONString());
        jsonData.close();
    }
}
